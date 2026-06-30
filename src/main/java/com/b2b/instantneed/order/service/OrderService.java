package com.b2b.instantneed.order.service;

import com.b2b.instantneed.cart.entity.Cart;
import com.b2b.instantneed.cart.entity.CartItem;
import com.b2b.instantneed.cart.entity.CartStatus;
import com.b2b.instantneed.cart.repository.CartRepository;
import com.b2b.instantneed.catalog.entity.Product;
import com.b2b.instantneed.catalog.repository.PincodeMinOrderRepository;
import com.b2b.instantneed.catalog.repository.ProductRepository;
import com.b2b.instantneed.common.dto.PagedResponse;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.common.security.SecurityUtils;
import com.b2b.instantneed.common.service.EmailService;
import com.b2b.instantneed.customer.entity.Address;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.customer.repository.AddressRepository;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.order.dto.OrderResponse;
import com.b2b.instantneed.order.dto.PlaceOrderRequest;
import com.b2b.instantneed.order.dto.PlaceOrderResponse;
import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.order.entity.OrderItem;
import com.b2b.instantneed.order.entity.OrderStatus;
import com.b2b.instantneed.order.repository.OrderRepository;
import com.b2b.instantneed.pricing.dto.PriceCalculateResponse;
import com.b2b.instantneed.pricing.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final CustomerRepository customerRepository;
    private final SecurityUtils securityUtils;
    private final ProductRepository productRepository;
    private final PricingService pricingService;
    private final EmailService emailService;
    private final PincodeMinOrderRepository pincodeMinOrderRepository;
    private final InvoiceService invoiceService;

    @Transactional
    public PlaceOrderResponse placeOrder(PlaceOrderRequest request) {
        Customer customer = securityUtils.currentCustomer();

        // --- Resolve items ---
        List<CartItem> cartItemsToUse = null;
        List<PlaceOrderRequest.OrderItemRequest> directItems = request.items();
        boolean itemsProvided = directItems != null && !directItems.isEmpty();

        if (!itemsProvided) {
            // Fall back to active cart
            Cart cart = cartRepository
                    .findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                    .orElseThrow(() -> ApiException.badRequest("CART_EMPTY", "No active cart found"));
            if (cart.getItems().isEmpty()) {
                throw ApiException.badRequest("CART_EMPTY", "Cannot place an order with an empty cart");
            }
            cartItemsToUse = new ArrayList<>(cart.getItems());
        }

        // --- Resolve shipping address snapshot ---
        Map<String, Object> addressSnapshot;
        if (request.shippingAddress() != null) {
            addressSnapshot = buildInlineAddressSnapshot(request.shippingAddress());
            saveInlineAddressToAccount(customer, request.shippingAddress());
        } else if (request.shippingAddressId() != null) {
            Address address = addressRepository.findById(request.shippingAddressId())
                    .orElseThrow(() -> ApiException.notFound("ADDRESS_NOT_FOUND",
                            "Address not found: " + request.shippingAddressId()));
            if (!address.getCustomer().getId().equals(customer.getId())) {
                throw ApiException.notFound("ADDRESS_NOT_FOUND", "Address not found: " + request.shippingAddressId());
            }
            addressSnapshot = buildAddressSnapshot(address);
        } else {
            // Use customer's default address
            UUID defaultId = customer.getDefaultShippingAddressId();
            if (defaultId == null) {
                throw ApiException.badRequest("NO_DEFAULT_ADDRESS", "No shipping address provided or set as default");
            }
            Address address = addressRepository.findById(defaultId)
                    .orElseThrow(() -> ApiException.badRequest("NO_DEFAULT_ADDRESS", "Default address not found"));
            addressSnapshot = buildAddressSnapshot(address);
        }

        // --- Build order items + compute totals ---
        String orderNumber = generateOrderNumber();
        Map<String, Object> customerSnapshot = buildCustomerSnapshot(customer);
        String paymentMethod = (request.paymentMethod() != null && !request.paymentMethod().isBlank())
                ? request.paymentMethod() : "cod";

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .customer(customer)
                .shippingAddressSnapshot(addressSnapshot)
                .customerSnapshot(customerSnapshot)
                .status(OrderStatus.PENDING)
                .paymentMethod(paymentMethod)
                .paymentNote("Payment will be collected separately after order confirmation.")
                .customerNote(request.notes())
                .currencyCode("INR")
                .subtotalAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        String currencyCode = "INR";

        if (itemsProvided) {
            for (PlaceOrderRequest.OrderItemRequest req : directItems) {
                Product product = productRepository.findById(req.productId())
                        .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND",
                                "Product not found: " + req.productId()));
                if (product.getStock() < req.quantity()) {
                    throw ApiException.badRequest("INSUFFICIENT_STOCK",
                            "Only " + product.getStock() + " units available for " + product.getName());
                }
                PriceCalculateResponse price = pricingService.calculate(product.getId(), req.quantity());
                OrderItem item = OrderItem.builder()
                        .order(order)
                        .product(product)
                        .productNameSnapshot(product.getName())
                        .skuSnapshot(product.getSku())
                        .unitOfMeasurementSnapshot(product.getUnitOfMeasurement() != null ? product.getUnitOfMeasurement() : "unit")
                        .quantity(req.quantity())
                        .unitPrice(price.appliedUnitPrice())
                        .lineTotal(price.lineTotal())
                        .currencyCode(price.currencyCode())
                        .build();
                order.getItems().add(item);
                subtotal = subtotal.add(price.lineTotal());
                currencyCode = price.currencyCode();
                product.setStock(product.getStock() - req.quantity());
                productRepository.save(product);
            }
        } else {
            for (CartItem ci : cartItemsToUse) {
                if (ci.getProduct() == null) {
                    // Product was deleted after being added to cart — skip it
                    continue;
                }
                Product product = ci.getProduct();
                if (product.getStock() < ci.getQuantity()) {
                    throw ApiException.badRequest("INSUFFICIENT_STOCK",
                            "Only " + product.getStock() + " units available for " + product.getName());
                }
                OrderItem item = OrderItem.builder()
                        .order(order)
                        .product(product)
                        .productNameSnapshot(product.getName())
                        .skuSnapshot(product.getSku())
                        .unitOfMeasurementSnapshot(product.getUnitOfMeasurement() != null ? product.getUnitOfMeasurement() : "unit")
                        .quantity(ci.getQuantity())
                        .unitPrice(ci.getAppliedUnitPrice())
                        .lineTotal(ci.getLineTotal())
                        .currencyCode(ci.getCurrencyCode())
                        .build();
                order.getItems().add(item);
                subtotal = subtotal.add(ci.getLineTotal());
                currencyCode = ci.getCurrencyCode();
                product.setStock(product.getStock() - ci.getQuantity());
                productRepository.save(product);
            }
            if (order.getItems().isEmpty()) {
                throw ApiException.badRequest("CART_EMPTY",
                        "All products in the cart have been removed and can no longer be ordered");
            }
            // Mark cart as checked out
            cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                    .ifPresent(c -> { c.setStatus(CartStatus.CHECKED_OUT); cartRepository.save(c); });
        }

        order.setSubtotalAmount(subtotal);
        order.setTotalAmount(subtotal);
        order.setCurrencyCode(currencyCode);

        // Validate pincode minimum order if a rule exists
        String postalCode = (String) addressSnapshot.get("postalCode");
        if (postalCode != null && !postalCode.isBlank()) {
            final BigDecimal finalSubtotal = subtotal;
            final String finalPostalCode = postalCode;
            pincodeMinOrderRepository.findByPincodeAndActiveTrue(postalCode).ifPresent(rule -> {
                if (finalSubtotal.compareTo(rule.getMinAmount()) < 0) {
                    throw ApiException.badRequest("BELOW_MIN_ORDER",
                            "Minimum order for pincode " + finalPostalCode + " is ₹" +
                            rule.getMinAmount().stripTrailingZeros().toPlainString() +
                            ". Your order total is ₹" + finalSubtotal.stripTrailingZeros().toPlainString() + ".");
                }
            });
        }

        // Generate invoice while order.getItems() is still a plain ArrayList (before Hibernate intercepts it)
        String invoiceUrl = invoiceService.generateAndStore(order);
        if (invoiceUrl != null) {
            order.setInvoicePath(invoiceUrl);
        }

        orderRepository.save(order);

        // Send confirmation email asynchronously — never blocks the HTTP response
        if (customer.getUser() != null && customer.getUser().getEmail() != null) {
            emailService.sendOrderConfirmation(customer.getUser().getEmail(), OrderResponse.from(order));
        }

        return new PlaceOrderResponse(order.getId(), orderNumber, order.getStatus().name(),
                "Order placed successfully.");
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getOrders(int page, int limit) {
        Customer customer = securityUtils.currentCustomer();
        int safePage = Math.max(1, page) - 1;
        int safeLimit = Math.min(Math.max(1, limit), 50);
        Page<Order> orderPage = orderRepository.findByCustomerIdOrderByPlacedAtDesc(
                customer.getId(), PageRequest.of(safePage, safeLimit));
        return PagedResponse.of(orderPage.map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        Customer customer = securityUtils.currentCustomer();
        Order order = orderRepository.findWithItemsByIdAndCustomerId(orderId, customer.getId())
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND",
                        "Order not found: " + orderId));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        Customer customer = securityUtils.currentCustomer();
        Order order = orderRepository.findWithItemsByIdAndCustomerId(orderId, customer.getId())
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found: " + orderId));
        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw ApiException.badRequest("CANNOT_CANCEL", "Cannot cancel an order that has been shipped or delivered");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw ApiException.badRequest("ALREADY_CANCELLED", "Order is already cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Restore stock for each item
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null) {
                item.getProduct().setStock(item.getProduct().getStock() + item.getQuantity());
                productRepository.save(item.getProduct());
            }
        }

        return OrderResponse.from(order);
    }

    @Transactional
    public PlaceOrderResponse reorder(UUID orderId) {
        Customer customer = securityUtils.currentCustomer();

        Order originalOrder = orderRepository.findWithItemsByIdAndCustomerId(orderId, customer.getId())
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND",
                        "Order not found: " + orderId));

        // Get or create a fresh active cart
        Cart cart = cartRepository
                .findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .orElseGet(() -> {
                    Cart c = Cart.builder()
                            .customer(customer)
                            .status(CartStatus.ACTIVE)
                            .build();
                    return cartRepository.save(c);
                });

        // Re-add items from the original order into the cart
        for (OrderItem oi : originalOrder.getItems()) {
            if (oi.getProduct() == null) continue; // product was deleted

            com.b2b.instantneed.cart.entity.CartItem existing = cart.getItems().stream()
                    .filter(ci -> ci.getProduct().getId().equals(oi.getProduct().getId()))
                    .findFirst().orElse(null);

            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + oi.getQuantity());
                existing.setLineTotal(existing.getAppliedUnitPrice()
                        .multiply(BigDecimal.valueOf(existing.getQuantity())));
            } else {
                com.b2b.instantneed.cart.entity.CartItem item =
                        com.b2b.instantneed.cart.entity.CartItem.builder()
                                .cart(cart)
                                .product(oi.getProduct())
                                .quantity(oi.getQuantity())
                                .appliedUnitPrice(oi.getUnitPrice())
                                .lineTotal(oi.getLineTotal())
                                .currencyCode(oi.getCurrencyCode())
                                .build();
                cart.getItems().add(item);
            }
        }

        cartRepository.save(cart);

        return new PlaceOrderResponse(
                null,
                null,
                "ACTIVE",
                "Items added to your cart. Review and adjust quantities before placing the order."
        );
    }

    private void saveInlineAddressToAccount(Customer customer, PlaceOrderRequest.InlineAddressRequest addr) {
        // Clear any existing default address
        addressRepository.findByCustomerId(customer.getId()).forEach(a -> {
            if (a.isDefault()) {
                a.setDefault(false);
                addressRepository.save(a);
            }
        });

        Address saved = Address.builder()
                .customer(customer)
                .label("Default")
                .fullName(addr.fullName())
                .phoneNumber(addr.phoneNumber())
                .line1(addr.addressLine1())
                .line2(addr.addressLine2())
                .city(addr.city())
                .state(addr.state())
                .country(addr.country() != null ? addr.country() : "India")
                .postalCode(addr.postalCode())
                .isDefault(true)
                .build();
        saved = addressRepository.save(saved);

        customer.setDefaultShippingAddressId(saved.getId());
        customerRepository.save(customer);
    }

    private String generateOrderNumber() {
        String dateStr = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "WB-" + dateStr + "-";
        int next = orderRepository.findMaxSequenceForPrefix(prefix) + 1;
        return prefix + String.format("%04d", next);
    }

    private Map<String, Object> buildCustomerSnapshot(Customer customer) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", customer.getId().toString());
        map.put("fullName", customer.getFullName());
        map.put("businessName", customer.getBusinessName());
        map.put("gstVatNumber", customer.getGstVatNumber());
        return map;
    }

    private Map<String, Object> buildAddressSnapshot(Address address) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", address.getId().toString());
        map.put("label", address.getLabel());
        map.put("fullName", address.getFullName());
        map.put("line1", address.getLine1());
        map.put("line2", address.getLine2());
        map.put("city", address.getCity());
        map.put("state", address.getState());
        map.put("country", address.getCountry());
        map.put("postalCode", address.getPostalCode());
        map.put("phoneNumber", address.getPhoneNumber());
        return map;
    }

    private Map<String, Object> buildInlineAddressSnapshot(PlaceOrderRequest.InlineAddressRequest addr) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fullName", addr.fullName());
        map.put("line1", addr.addressLine1());
        map.put("line2", addr.addressLine2());
        map.put("city", addr.city());
        map.put("state", addr.state());
        map.put("country", addr.country());
        map.put("postalCode", addr.postalCode());
        map.put("phoneNumber", addr.phoneNumber());
        return map;
    }
}

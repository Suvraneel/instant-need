package com.b2b.instantneed.order.service;

import com.b2b.instantneed.cart.entity.Cart;
import com.b2b.instantneed.cart.entity.CartItem;
import com.b2b.instantneed.cart.entity.CartStatus;
import com.b2b.instantneed.cart.repository.CartRepository;
import com.b2b.instantneed.catalog.entity.PincodeMinOrder;
import com.b2b.instantneed.catalog.entity.Product;
import com.b2b.instantneed.catalog.entity.AvailabilityStatus;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
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
    private final OrderNumberService orderNumberService;

    @Transactional
    public PlaceOrderResponse placeOrder(PlaceOrderRequest request) {
        return placeOrder(request, null);
    }

    @Transactional
    public PlaceOrderResponse placeOrder(PlaceOrderRequest request, String idempotencyKey) {
        Customer customer = securityUtils.currentCustomer();
        String key = idempotencyKey == null ? null : idempotencyKey.trim();
        if (key != null && (key.isEmpty() || key.length() > 100)) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1 to 100 characters");
        }
        String requestHash = key == null ? null : hashRequest(request);
        // Serializes retries and cart checkouts by this customer before reading either state.
        customer = customerRepository.findByIdForUpdate(customer.getId())
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND", "Customer not found"));
        if (key != null) {
            var previous = orderRepository.findByCustomerIdAndIdempotencyKey(customer.getId(), key);
            if (previous.isPresent()) {
                Order existing = previous.get();
                if (!requestHash.equals(existing.getIdempotencyRequestHash())) {
                    throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was used for another order request");
                }
                return new PlaceOrderResponse(existing.getId(), existing.getOrderNumber(),
                        existing.getStatus().name(), "Order placed successfully.");
            }
        }

        // --- Resolve items ---
        List<CartItem> cartItemsToUse = null;
        List<PlaceOrderRequest.OrderItemRequest> directItems = request.items();
        boolean itemsProvided = directItems != null && !directItems.isEmpty();
        if (itemsProvided) {
            HashSet<UUID> seen = new HashSet<>();
            for (PlaceOrderRequest.OrderItemRequest item : directItems) {
                if (item.productId() == null || item.quantity() < 1 || !seen.add(item.productId())) {
                    throw ApiException.badRequest("INVALID_ORDER_ITEMS", "Each product must appear once with a positive quantity");
                }
            }
        }

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

        // --- Validate pincode is serviceable (checked before touching stock/cart) ---
        String postalCode = (String) addressSnapshot.get("postalCode");
        if (postalCode == null || postalCode.isBlank()) {
            throw ApiException.badRequest("PINCODE_NOT_SERVICEABLE", "Shipping address is missing a pincode");
        }
        PincodeMinOrder pincodeRule = pincodeMinOrderRepository.findByPincodeAndActiveTrue(postalCode)
                .orElseThrow(() -> ApiException.badRequest("PINCODE_NOT_SERVICEABLE",
                        "We don't currently deliver to pincode " + postalCode + ". Please check back soon or contact support."));

        // --- Build order items + compute totals ---
        List<UUID> productIds = itemsProvided
                ? directItems.stream().map(PlaceOrderRequest.OrderItemRequest::productId).toList()
                : cartItemsToUse.stream().filter(ci -> ci.getProduct() != null)
                        .map(ci -> ci.getProduct().getId()).toList();
        Map<UUID, Product> lockedProducts = new HashMap<>();
        productIds.stream().distinct().sorted(Comparator.naturalOrder()).forEach(id -> {
            Product product = productRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found: " + id));
            lockedProducts.put(id, product);
        });
        String orderNumber = orderNumberService.next();
        Map<String, Object> customerSnapshot = buildCustomerSnapshot(customer, request.gstinUin());
        String paymentMethod = (request.paymentMethod() != null && !request.paymentMethod().isBlank())
                ? request.paymentMethod() : "cod";

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .customer(customer)
                .idempotencyKey(key)
                .idempotencyRequestHash(requestHash)
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
                Product product = lockedProducts.get(req.productId());
                ensureOrderable(product);
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
                        .mrpSnapshot(product.getMrp())
                        .hsnCodeSnapshot(product.getHsnCode())
                        .currencyCode(price.currencyCode())
                        .build();
                applyTaxSnapshot(item, product, price.lineTotal());
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
                Product product = lockedProducts.get(ci.getProduct().getId());
                ensureOrderable(product);
                if (ci.getQuantity() < 1) {
                    throw ApiException.badRequest("INVALID_ORDER_ITEMS", "Cart quantities must be positive");
                }
                if (product.getStock() < ci.getQuantity()) {
                    throw ApiException.badRequest("INSUFFICIENT_STOCK",
                            "Only " + product.getStock() + " units available for " + product.getName());
                }
                PriceCalculateResponse price = pricingService.calculate(product.getId(), ci.getQuantity());
                OrderItem item = OrderItem.builder()
                        .order(order)
                        .product(product)
                        .productNameSnapshot(product.getName())
                        .skuSnapshot(product.getSku())
                        .unitOfMeasurementSnapshot(product.getUnitOfMeasurement() != null ? product.getUnitOfMeasurement() : "unit")
                        .quantity(ci.getQuantity())
                        .unitPrice(price.appliedUnitPrice())
                        .lineTotal(price.lineTotal())
                        .mrpSnapshot(product.getMrp())
                        .hsnCodeSnapshot(product.getHsnCode())
                        .currencyCode(price.currencyCode())
                        .build();
                applyTaxSnapshot(item, product, price.lineTotal());
                order.getItems().add(item);
                subtotal = subtotal.add(price.lineTotal());
                currencyCode = price.currencyCode();
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

        // Enforce the pincode's minimum order amount (serviceability already checked above)
        if (subtotal.compareTo(pincodeRule.getMinAmount()) < 0) {
            throw ApiException.badRequest("BELOW_MIN_ORDER",
                    "Minimum order for pincode " + postalCode + " is ₹" +
                    pincodeRule.getMinAmount().stripTrailingZeros().toPlainString() +
                    ". Your order total is ₹" + subtotal.stripTrailingZeros().toPlainString() + ".");
        }

        // Set placedAt now so buildHtml can format the date (PrePersist hasn't run yet)
        if (order.getPlacedAt() == null) {
            order.setPlacedAt(Instant.now());
        }

        orderRepository.save(order);
        Customer orderCustomer = customer;
        Runnable postCommit = () -> {
            try {
                invoiceService.generateAndStoreById(order.getId());
            } catch (Exception e) {
                log.error("Invoice generation failed after placing order {}", order.getId(), e);
            }
            try {
                if (orderCustomer.getUser() != null && orderCustomer.getUser().getEmail() != null) {
                    emailService.sendOrderConfirmation(orderCustomer.getUser().getEmail(), OrderResponse.forCustomer(order));
                }
            } catch (Exception e) {
                log.error("Confirmation email failed after placing order {}", order.getId(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { postCommit.run(); }
            });
        } else {
            postCommit.run();
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
        return PagedResponse.of(orderPage.map(OrderResponse::forCustomer));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        Customer customer = securityUtils.currentCustomer();
        Order order = orderRepository.findWithItemsByIdAndCustomerId(orderId, customer.getId())
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND",
                        "Order not found: " + orderId));
        return OrderResponse.forCustomer(order);
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

        return OrderResponse.forCustomer(order);
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

    private void ensureOrderable(Product product) {
        if (!product.isActive() || product.getAvailabilityStatus() == AvailabilityStatus.DISCONTINUED) {
            throw ApiException.badRequest("PRODUCT_UNAVAILABLE", "Product is no longer available: " + product.getName());
        }
    }

    private String hashRequest(PlaceOrderRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(request.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private Map<String, Object> buildCustomerSnapshot(Customer customer, String gstinUin) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", customer.getId().toString());
        map.put("fullName", customer.getFullName());
        map.put("businessName", customer.getBusinessName());
        map.put("gstinUin", gstinUin == null || gstinUin.isBlank() ? null : gstinUin.trim().toUpperCase());
        return map;
    }

    private void applyTaxSnapshot(OrderItem item, Product product, BigDecimal grossAmount) {
        BigDecimal cgstRate = nonNegative(product.getCgstRate());
        BigDecimal sgstRate = nonNegative(product.getSgstRate());
        BigDecimal gross = grossAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal cgstAmount = gross.multiply(cgstRate)
                .movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sgstAmount = gross.multiply(sgstRate)
                .movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxableAmount = gross;

        item.setCgstRate(cgstRate);
        item.setSgstRate(sgstRate);
        item.setTaxableAmount(taxableAmount);
        item.setCgstAmount(cgstAmount);
        item.setSgstAmount(sgstAmount);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
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

package com.b2b.instantneed.order.service;

import com.b2b.instantneed.cart.entity.Cart;
import com.b2b.instantneed.cart.entity.CartItem;
import com.b2b.instantneed.cart.entity.CartStatus;
import com.b2b.instantneed.cart.repository.CartRepository;
import com.b2b.instantneed.common.dto.PagedResponse;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.common.security.SecurityUtils;
import com.b2b.instantneed.customer.entity.Address;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.customer.repository.AddressRepository;
import com.b2b.instantneed.order.dto.OrderResponse;
import com.b2b.instantneed.order.dto.PlaceOrderRequest;
import com.b2b.instantneed.order.dto.PlaceOrderResponse;
import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.order.entity.OrderItem;
import com.b2b.instantneed.order.entity.OrderStatus;
import com.b2b.instantneed.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
    private final SecurityUtils securityUtils;

    @Transactional
    public PlaceOrderResponse placeOrder(PlaceOrderRequest request) {
        Customer customer = securityUtils.currentCustomer();

        // Load the active cart with items
        Cart cart = cartRepository
                .findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .orElseThrow(() -> ApiException.badRequest("CART_EMPTY", "No active cart found"));

        if (cart.getItems().isEmpty()) {
            throw ApiException.badRequest("CART_EMPTY", "Cannot place an order with an empty cart");
        }

        // Validate shipping address belongs to this customer
        Address address = addressRepository.findById(request.shippingAddressId())
                .orElseThrow(() -> ApiException.notFound("ADDRESS_NOT_FOUND",
                        "Address not found: " + request.shippingAddressId()));

        if (!address.getCustomer().getId().equals(customer.getId())) {
            throw ApiException.notFound("ADDRESS_NOT_FOUND",
                    "Address not found: " + request.shippingAddressId());
        }

        // Compute totals from the already-priced cart items
        BigDecimal subtotal = cart.getItems().stream()
                .map(CartItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String currencyCode = cart.getItems().get(0).getCurrencyCode();

        // Build order number: WB-YYYYMMDD-NNNN
        String orderNumber = generateOrderNumber();

        // Snapshot customer and address at this moment in time
        Map<String, Object> customerSnapshot = buildCustomerSnapshot(customer);
        Map<String, Object> addressSnapshot = buildAddressSnapshot(address);

        // Create order
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .customer(customer)
                .shippingAddressSnapshot(addressSnapshot)
                .customerSnapshot(customerSnapshot)
                .status(OrderStatus.PENDING)
                .paymentMethod("cod")
                .paymentNote("Payment will be collected separately after order confirmation.")
                .customerNote(request.customerNote())
                .subtotalAmount(subtotal)
                .totalAmount(subtotal)
                .currencyCode(currencyCode)
                .build();

        // Freeze cart items into order items (snapshot)
        List<OrderItem> orderItems = cart.getItems().stream().map(ci -> OrderItem.builder()
                .order(order)
                .product(ci.getProduct())
                .productNameSnapshot(ci.getProduct().getName())
                .skuSnapshot(ci.getProduct().getSku())
                .unitOfMeasurementSnapshot(ci.getProduct().getUnitOfMeasurement())
                .quantity(ci.getQuantity())
                .unitPrice(ci.getAppliedUnitPrice())
                .lineTotal(ci.getLineTotal())
                .currencyCode(ci.getCurrencyCode())
                .build()
        ).toList();

        order.getItems().addAll(orderItems);
        orderRepository.save(order);

        // Mark cart as checked out
        cart.setStatus(CartStatus.CHECKED_OUT);
        cartRepository.save(cart);

        return new PlaceOrderResponse(
                order.getId(),
                orderNumber,
                order.getStatus().name(),
                "Order placed successfully. Payment will be collected separately after confirmation."
        );
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
        // Import here to avoid a circular dep — use CartService would create circular dependency
        // so we manipulate the cart directly; pricing will be re-validated on next cart mutation
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
        map.put("line1", address.getLine1());
        map.put("line2", address.getLine2());
        map.put("city", address.getCity());
        map.put("state", address.getState());
        map.put("country", address.getCountry());
        map.put("postalCode", address.getPostalCode());
        return map;
    }
}

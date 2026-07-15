package com.b2b.instantneed.order.service;

import com.b2b.instantneed.cart.entity.Cart;
import com.b2b.instantneed.cart.entity.CartItem;
import com.b2b.instantneed.cart.entity.CartStatus;
import com.b2b.instantneed.cart.repository.CartRepository;
import com.b2b.instantneed.catalog.entity.AvailabilityStatus;
import com.b2b.instantneed.catalog.entity.PincodeMinOrder;
import com.b2b.instantneed.catalog.entity.Product;
import com.b2b.instantneed.catalog.repository.ProductRepository;
import com.b2b.instantneed.catalog.repository.PincodeMinOrderRepository;
import com.b2b.instantneed.common.dto.PagedResponse;
import com.b2b.instantneed.common.service.EmailService;
import com.b2b.instantneed.order.service.InvoiceService;
import com.b2b.instantneed.pricing.service.PricingService;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.common.security.SecurityUtils;
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
import com.b2b.instantneed.user.entity.AuthProvider;
import com.b2b.instantneed.user.entity.Role;
import com.b2b.instantneed.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository              orderRepository;
    @Mock CartRepository               cartRepository;
    @Mock AddressRepository            addressRepository;
    @Mock CustomerRepository           customerRepository;
    @Mock SecurityUtils                securityUtils;
    @Mock ProductRepository            productRepository;
    @Mock PricingService               pricingService;
    @Mock EmailService                 emailService;
    @Mock PincodeMinOrderRepository    pincodeMinOrderRepository;
    @Mock InvoiceService               invoiceService;

    @InjectMocks OrderService orderService;

    private Customer customer;
    private Address  address;
    private Product  product;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(UUID.randomUUID()).email("buyer@test.com")
                .passwordHash("hash").authProvider(AuthProvider.LOCAL)
                .role(Role.CUSTOMER).active(true).build();

        customer = Customer.builder()
                .id(UUID.randomUUID()).user(user)
                .fullName("Raj Sharma").businessName("Sharma Traders")
                .gstVatNumber("27AAPFU0939F1ZV").build();

        address = Address.builder()
                .id(UUID.randomUUID()).customer(customer)
                .label("Head Office").line1("12 MG Road")
                .city("Mumbai").state("Maharashtra")
                .country("India").postalCode("400001").isDefault(true).build();

        product = Product.builder()
                .id(UUID.randomUUID()).name("A4 Paper").sku("PAPER-A4")
                .slug("a4-paper").basePrice(new BigDecimal("250.00"))
                .availabilityStatus(AvailabilityStatus.IN_STOCK).active(true)
                .stock(100).build();

        given(securityUtils.currentCustomer()).willReturn(customer);
    }

    // ── placeOrder ────────────────────────────────────────────────────────────

    @Test
    void placeOrder_success_checksOutCartAndReturnsOrderNumber() {
        Cart cart = activeCartWithItem();

        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));
        given(addressRepository.findById(address.getId())).willReturn(Optional.of(address));
        given(pincodeMinOrderRepository.findByPincodeAndActiveTrue(address.getPostalCode()))
                .willReturn(Optional.of(activeRule(BigDecimal.ZERO)));
        given(orderRepository.findMaxSequenceForPrefix(anyString())).willReturn(0);
        given(orderRepository.save(any())).willAnswer(inv -> {
            Order o = inv.getArgument(0);
            o = Order.builder()
                    .id(UUID.randomUUID())
                    .orderNumber(o.getOrderNumber())
                    .customer(customer)
                    .status(OrderStatus.PENDING)
                    .placedAt(Instant.now())
                    .subtotalAmount(o.getSubtotalAmount())
                    .totalAmount(o.getTotalAmount())
                    .currencyCode("INR")
                    .shippingAddressSnapshot(o.getShippingAddressSnapshot())
                    .customerSnapshot(o.getCustomerSnapshot())
                    .build();
            return o;
        });

        PlaceOrderResponse res = orderService.placeOrder(
                new PlaceOrderRequest(null, address.getId(), null, null, "Please deliver by noon"));

        assertThat(res.orderNumber()).startsWith("WB-");
        assertThat(res.status()).isEqualTo("PENDING");
        assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
    }

    @Test
    void placeOrder_noActiveCart_throwsBadRequest() {
        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(new PlaceOrderRequest(null, address.getId(), null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void placeOrder_emptyCart_throwsBadRequest() {
        Cart emptyCart = Cart.builder()
                .id(UUID.randomUUID()).customer(customer).status(CartStatus.ACTIVE).build();
        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(emptyCart));

        assertThatThrownBy(() -> orderService.placeOrder(new PlaceOrderRequest(null, address.getId(), null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void placeOrder_addressNotFound_throws404() {
        Cart cart = activeCartWithItem();
        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));
        given(addressRepository.findById(address.getId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(new PlaceOrderRequest(null, address.getId(), null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void placeOrder_allCartProductsDeleted_throwsBadRequest() {
        // Cart has one item but the product was hard-deleted (product == null)
        Cart cart = Cart.builder()
                .id(UUID.randomUUID()).customer(customer).status(CartStatus.ACTIVE).build();
        CartItem deletedItem = CartItem.builder()
                .id(UUID.randomUUID()).cart(cart)
                .product(null) // deleted product
                .quantity(2).appliedUnitPrice(new BigDecimal("100.00"))
                .lineTotal(new BigDecimal("200.00")).currencyCode("INR").build();
        cart.getItems().add(deletedItem);

        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));
        given(addressRepository.findById(address.getId())).willReturn(Optional.of(address));
        given(pincodeMinOrderRepository.findByPincodeAndActiveTrue(address.getPostalCode()))
                .willReturn(Optional.of(activeRule(BigDecimal.ZERO)));
        given(orderRepository.findMaxSequenceForPrefix(anyString())).willReturn(0);

        assertThatThrownBy(() -> orderService.placeOrder(
                new PlaceOrderRequest(null, address.getId(), null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void placeOrder_addressBelongsToOtherCustomer_throws404() {
        Customer other = Customer.builder()
                .id(UUID.randomUUID()).user(null).fullName("Other").build();
        Address foreignAddress = Address.builder()
                .id(UUID.randomUUID()).customer(other)
                .line1("X").city("X").state("X").country("IN").postalCode("0").build();

        Cart cart = activeCartWithItem();
        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));
        given(addressRepository.findById(foreignAddress.getId()))
                .willReturn(Optional.of(foreignAddress));

        assertThatThrownBy(() -> orderService.placeOrder(new PlaceOrderRequest(null, foreignAddress.getId(), null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND); // never reveals existence
    }

    @Test
    void placeOrder_pincodeHasNoRule_throwsNotServiceable() {
        Cart cart = activeCartWithItem();
        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));
        given(addressRepository.findById(address.getId())).willReturn(Optional.of(address));
        given(pincodeMinOrderRepository.findByPincodeAndActiveTrue(address.getPostalCode()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(
                new PlaceOrderRequest(null, address.getId(), null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void placeOrder_belowPincodeMinimum_throwsBadRequest() {
        Cart cart = activeCartWithItem(); // subtotal = 1250.00
        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));
        given(addressRepository.findById(address.getId())).willReturn(Optional.of(address));
        given(pincodeMinOrderRepository.findByPincodeAndActiveTrue(address.getPostalCode()))
                .willReturn(Optional.of(activeRule(new BigDecimal("2000.00"))));

        assertThatThrownBy(() -> orderService.placeOrder(
                new PlaceOrderRequest(null, address.getId(), null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Minimum order");
    }

    // ── getOrders ─────────────────────────────────────────────────────────────

    @Test
    void getOrders_returnsPagedOrders() {
        Order order = minimalOrder();
        Page<Order> page = new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1);
        given(orderRepository.findByCustomerIdOrderByPlacedAtDesc(
                eq(customer.getId()), any())).willReturn(page);

        PagedResponse<OrderResponse> res = orderService.getOrders(1, 20);

        assertThat(res.items()).hasSize(1);
        assertThat(res.total()).isEqualTo(1L);
    }

    // ── getOrder ──────────────────────────────────────────────────────────────

    @Test
    void getOrder_belongsToCustomer_returnsDetail() {
        Order order = minimalOrder();
        given(orderRepository.findWithItemsByIdAndCustomerId(order.getId(), customer.getId()))
                .willReturn(Optional.of(order));

        OrderResponse res = orderService.getOrder(order.getId());

        assertThat(res.orderNumber()).isEqualTo("WB-20260523-0001");
    }

    @Test
    void getOrder_notFound_throws404() {
        UUID orderId = UUID.randomUUID();
        given(orderRepository.findWithItemsByIdAndCustomerId(orderId, customer.getId()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(orderId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── reorder ───────────────────────────────────────────────────────────────

    @Test
    void reorder_copiesItemsIntoNewCart() {
        Order original = minimalOrder();
        OrderItem oi = OrderItem.builder()
                .id(UUID.randomUUID()).order(original).product(product)
                .productNameSnapshot("A4 Paper").skuSnapshot("PAPER-A4")
                .quantity(5).unitPrice(new BigDecimal("250.00"))
                .lineTotal(new BigDecimal("1250.00")).currencyCode("INR").build();
        original.getItems().add(oi);

        given(orderRepository.findWithItemsByIdAndCustomerId(original.getId(), customer.getId()))
                .willReturn(Optional.of(original));
        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.empty());
        Cart newCart = Cart.builder()
                .id(UUID.randomUUID()).customer(customer).status(CartStatus.ACTIVE).build();
        given(cartRepository.save(any())).willReturn(newCart);

        PlaceOrderResponse res = orderService.reorder(original.getId());

        assertThat(res.status()).isEqualTo("ACTIVE");
        assertThat(res.message()).contains("cart");
        assertThat(newCart.getItems()).hasSize(1);
    }

    @Test
    void reorder_deletedProduct_skippedGracefully() {
        Order original = minimalOrder();
        OrderItem oi = OrderItem.builder()
                .id(UUID.randomUUID()).order(original)
                .product(null) // product was deleted
                .productNameSnapshot("Old Product").skuSnapshot("OLD-SKU")
                .quantity(2).unitPrice(new BigDecimal("100.00"))
                .lineTotal(new BigDecimal("200.00")).currencyCode("INR").build();
        original.getItems().add(oi);

        given(orderRepository.findWithItemsByIdAndCustomerId(original.getId(), customer.getId()))
                .willReturn(Optional.of(original));
        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.empty());
        Cart newCart = Cart.builder()
                .id(UUID.randomUUID()).customer(customer).status(CartStatus.ACTIVE).build();
        given(cartRepository.save(any())).willReturn(newCart);

        orderService.reorder(original.getId());

        assertThat(newCart.getItems()).isEmpty(); // deleted product skipped
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private PincodeMinOrder activeRule(BigDecimal minAmount) {
        return PincodeMinOrder.builder()
                .id(UUID.randomUUID()).pincode(address.getPostalCode())
                .minAmount(minAmount).active(true).build();
    }

    private Cart activeCartWithItem() {
        Cart cart = Cart.builder()
                .id(UUID.randomUUID()).customer(customer).status(CartStatus.ACTIVE).build();
        CartItem item = CartItem.builder()
                .id(UUID.randomUUID()).cart(cart).product(product)
                .quantity(5).appliedUnitPrice(new BigDecimal("250.00"))
                .lineTotal(new BigDecimal("1250.00")).currencyCode("INR").build();
        cart.getItems().add(item);
        return cart;
    }

    private Order minimalOrder() {
        return Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("WB-20260523-0001")
                .customer(customer)
                .status(OrderStatus.PENDING)
                .paymentMethod("cod")
                .placedAt(Instant.now())
                .subtotalAmount(new BigDecimal("1250.00"))
                .totalAmount(new BigDecimal("1250.00"))
                .currencyCode("INR")
                .shippingAddressSnapshot(Map.of("city", "Mumbai"))
                .customerSnapshot(Map.of("fullName", "Raj Sharma"))
                .build();
    }
}

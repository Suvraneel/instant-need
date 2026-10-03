package com.b2b.instantneed.order.service;

import com.b2b.instantneed.cart.entity.Cart;
import com.b2b.instantneed.cart.entity.CartItem;
import com.b2b.instantneed.cart.entity.CartStatus;
import com.b2b.instantneed.cart.repository.CartRepository;
import com.b2b.instantneed.catalog.entity.PincodeMinOrder;
import com.b2b.instantneed.catalog.entity.Product;
import com.b2b.instantneed.catalog.repository.PincodeMinOrderRepository;
import com.b2b.instantneed.catalog.repository.ProductRepository;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.common.security.SecurityUtils;
import com.b2b.instantneed.common.service.EmailService;
import com.b2b.instantneed.customer.entity.Address;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.customer.repository.AddressRepository;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.order.dto.PlaceOrderRequest;
import com.b2b.instantneed.order.dto.PlaceOrderResponse;
import com.b2b.instantneed.order.repository.OrderRepository;
import com.b2b.instantneed.user.entity.AuthProvider;
import com.b2b.instantneed.user.entity.Role;
import com.b2b.instantneed.user.entity.User;
import com.b2b.instantneed.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class OrderPersistenceTest {
    @Autowired OrderService orders;
    @Autowired UserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired AddressRepository addresses;
    @Autowired ProductRepository products;
    @Autowired CartRepository carts;
    @Autowired PincodeMinOrderRepository pincodes;
    @Autowired OrderRepository orderRepository;
    @MockitoBean SecurityUtils securityUtils;
    @MockitoBean InvoiceService invoiceService;
    @MockitoBean EmailService emailService;

    Customer customer;
    Address address;
    Product product;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        User user = users.save(User.builder().email(suffix + "@example.test")
                .passwordHash("test").authProvider(AuthProvider.LOCAL)
                .role(Role.CUSTOMER).active(true).build());
        customer = customers.save(Customer.builder().user(user).fullName("Test Buyer").build());
        address = addresses.save(Address.builder().customer(customer).label("Test")
                .line1("1 Test St").city("Pune").state("MH").country("India")
                .postalCode(suffix.substring(0, 6)).isDefault(true).build());
        pincodes.save(PincodeMinOrder.builder().pincode(address.getPostalCode())
                .minAmount(BigDecimal.ZERO).active(true).build());
        product = products.save(Product.builder().name("Test Item").slug("item-" + suffix)
                .sku("SKU-" + suffix).basePrice(new BigDecimal("100.00"))
                .stock(2).active(true).build());
        when(securityUtils.currentCustomer()).thenReturn(customer);
    }

    private PlaceOrderRequest direct(int quantity) {
        return new PlaceOrderRequest(List.of(new PlaceOrderRequest.OrderItemRequest(product.getId(), quantity)),
                address.getId(), null, "cod", null);
    }

    @Test
    void retryReturnsSamePersistedOrderAndDoesNotConsumeMoreStock() {
        PlaceOrderRequest request = direct(1);
        String key = "retry-" + UUID.randomUUID();
        PlaceOrderResponse first = orders.placeOrder(request, key);
        PlaceOrderResponse second = orders.placeOrder(request, key);
        assertThat(first.id()).isNotNull();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(orderRepository.countByCustomerId(customer.getId())).isEqualTo(1);
        assertThat(products.findById(product.getId()).orElseThrow().getStock()).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentRequestReturnsConflict() {
        String key = "conflict-" + UUID.randomUUID();
        orders.placeOrder(direct(1), key);
        assertThatThrownBy(() -> orders.placeOrder(direct(2), key))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getErrorCode())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(orderRepository.countByCustomerId(customer.getId())).isEqualTo(1);
    }

    @Test
    void orderNumbersIncreaseAcrossSeparateOrders() {
        PlaceOrderResponse first = orders.placeOrder(direct(1), "first-" + UUID.randomUUID());
        PlaceOrderResponse second = orders.placeOrder(direct(1), "second-" + UUID.randomUUID());
        assertThat(Integer.parseInt(second.orderNumber().substring(12)))
                .isEqualTo(Integer.parseInt(first.orderNumber().substring(12)) + 1);
    }

    @Test
    void cartPriceIsRefreshedAtCheckout() {
        Cart cart = Cart.builder().customer(customer).status(CartStatus.ACTIVE).build();
        cart.getItems().add(CartItem.builder().cart(cart).product(product).quantity(1)
                .appliedUnitPrice(new BigDecimal("100.00"))
                .lineTotal(new BigDecimal("100.00")).currencyCode("INR").build());
        carts.save(cart);
        product.setBasePrice(new BigDecimal("125.00"));
        products.save(product);
        PlaceOrderResponse placed = orders.placeOrder(new PlaceOrderRequest(null, address.getId(), null, "cod", null),
                "cart-" + UUID.randomUUID());
        assertThat(orderRepository.findWithItemsById(placed.id()).orElseThrow().getItems().getFirst()
                .getUnitPrice()).isEqualByComparingTo("125.00");
    }

    @Test
    void invalidQuantityDoesNotPersistOrderOrChangeStock() {
        assertThatThrownBy(() -> orders.placeOrder(direct(0), "bad-" + UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
        assertThat(products.findById(product.getId()).orElseThrow().getStock()).isEqualTo(2);
        assertThat(orderRepository.countByCustomerId(customer.getId())).isZero();
    }

    @Test
    void simultaneousCheckoutCannotOversell() throws Exception {
        product.setStock(1);
        products.save(product);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { start.await(); return attempt("one"); });
            var two = executor.submit(() -> { start.await(); return attempt("two"); });
            start.countDown();
            assertThat(one.get(15, TimeUnit.SECONDS) + two.get(15, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(products.findById(product.getId()).orElseThrow().getStock()).isZero();
        assertThat(orderRepository.countByCustomerId(customer.getId())).isEqualTo(1);
    }

    private int attempt(String key) {
        try {
            orders.placeOrder(direct(1), key + UUID.randomUUID());
            return 1;
        } catch (ApiException e) {
            assertThat(e.getErrorCode()).isEqualTo("INSUFFICIENT_STOCK");
            return 0;
        }
    }

    @Test
    void invoiceFailureAfterCommitLeavesOrderAvailableForRetry() {
        doThrow(new IllegalStateException("storage unavailable"))
                .when(invoiceService).generateAndStoreById(any());
        PlaceOrderResponse placed = orders.placeOrder(direct(1), "invoice-" + UUID.randomUUID());
        assertThat(orderRepository.findById(placed.id())).isPresent();
        assertThat(products.findById(product.getId()).orElseThrow().getStock()).isEqualTo(1);
    }
}

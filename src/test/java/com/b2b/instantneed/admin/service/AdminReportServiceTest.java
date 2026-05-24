package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.admin.dto.*;
import com.b2b.instantneed.catalog.repository.ProductRepository;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.order.entity.OrderStatus;
import com.b2b.instantneed.order.repository.OrderItemRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminReportServiceTest {

    @Mock OrderRepository     orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock CustomerRepository  customerRepository;
    @Mock ProductRepository   productRepository;

    @InjectMocks AdminReportService service;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
    }

    // ── summaryReport ─────────────────────────────────────────────────────────

    @Test
    void summaryReport_returnsAllCountsAndRevenue() {
        given(orderRepository.count()).willReturn(42L);
        given(orderRepository.countByStatus(OrderStatus.PENDING)).willReturn(5L);
        given(orderRepository.countByStatus(OrderStatus.PROCESSING)).willReturn(3L);
        given(orderRepository.countByStatus(OrderStatus.SHIPPED)).willReturn(10L);
        given(orderRepository.sumRevenueSince(eq(OrderStatus.CANCELLED), any(Instant.class)))
                .willReturn(new BigDecimal("123456.00"));
        given(customerRepository.count()).willReturn(200L);
        given(customerRepository.countByCreatedAtGreaterThanEqual(any(Instant.class))).willReturn(7L);
        given(productRepository.countByActiveTrue()).willReturn(55L);

        DashboardSummary summary = service.summaryReport();

        assertThat(summary.totalOrders()).isEqualTo(42L);
        assertThat(summary.pendingOrders()).isEqualTo(5L);
        assertThat(summary.processingOrders()).isEqualTo(3L);
        assertThat(summary.shippedOrders()).isEqualTo(10L);
        assertThat(summary.revenueThisMonth()).isEqualByComparingTo("123456.00");
        assertThat(summary.totalCustomers()).isEqualTo(200L);
        assertThat(summary.newCustomersThisMonth()).isEqualTo(7L);
        assertThat(summary.activeProducts()).isEqualTo(55L);
    }

    // ── salesReport ───────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void salesReport_noDateFilter_returnsAggregatedData() {
        Order o1 = order("2026-05-20", new BigDecimal("500.00"));
        Order o2 = order("2026-05-20", new BigDecimal("300.00"));
        Order o3 = order("2026-05-21", new BigDecimal("200.00"));
        given(orderRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(o1, o2, o3));

        SalesReportResponse res = service.salesReport(null, null);

        assertThat(res.totalOrders()).isEqualTo(3);
        assertThat(res.totalRevenue()).isEqualByComparingTo("1000.00");
        assertThat(res.averageOrderValue()).isEqualByComparingTo("333.33");
        assertThat(res.breakdown()).hasSize(2); // 2 distinct dates
    }

    @Test
    @SuppressWarnings("unchecked")
    void salesReport_noOrders_returnsZeroRevenue() {
        given(orderRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of());

        SalesReportResponse res = service.salesReport("2026-01-01", "2026-01-31");

        assertThat(res.totalOrders()).isZero();
        assertThat(res.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(res.averageOrderValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void salesReport_invalidDate_throwsBadRequest() {
        assertThatThrownBy(() -> service.salesReport("not-a-date", null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── topProducts ───────────────────────────────────────────────────────────

    @Test
    void topProducts_delegatesToRepositoryAndMapsRows() {
        UUID pid = UUID.randomUUID();
        Object[] row = {"A4 Paper", "PAPER-A4", "INR", pid.toString(), 100L, new BigDecimal("25000.00")};
        given(orderItemRepository.aggregateByProduct(
                eq(OrderStatus.CANCELLED.name()), isNull(), isNull(), any()))
                .willReturn(List.<Object[]>of(row));

        List<TopProductEntry> result = service.topProducts(10, null, null);

        assertThat(result).hasSize(1);
        TopProductEntry entry = result.get(0);
        assertThat(entry.productName()).isEqualTo("A4 Paper");
        assertThat(entry.sku()).isEqualTo("PAPER-A4");
        assertThat(entry.totalQuantity()).isEqualTo(100L);
        assertThat(entry.totalRevenue()).isEqualByComparingTo("25000.00");
    }

    @Test
    void topProducts_limitClamped_to50() {
        given(orderItemRepository.aggregateByProduct(any(), any(), any(), any()))
                .willReturn(List.of());

        service.topProducts(999, null, null);  // limit > 50

        // Verify pageable size was capped at 50
        verify(orderItemRepository).aggregateByProduct(
                any(), any(), any(),
                argThat(p -> p.getPageSize() == 50));
    }

    // ── customerActivity ──────────────────────────────────────────────────────

    @Test
    void customerActivity_delegatesToRepositoryAndMapsRows() {
        Instant lastOrder = Instant.now();
        Object[] row = {customerId, "Raj Sharma", "Sharma Traders",
                        "buyer@test.com", 5L, new BigDecimal("12500.00"), lastOrder};
        given(orderRepository.aggregateByCustomer(eq(OrderStatus.CANCELLED), any()))
                .willReturn(List.<Object[]>of(row));

        List<CustomerActivityEntry> result = service.customerActivity(10);

        assertThat(result).hasSize(1);
        CustomerActivityEntry entry = result.get(0);
        assertThat(entry.customerId()).isEqualTo(customerId);
        assertThat(entry.fullName()).isEqualTo("Raj Sharma");
        assertThat(entry.orderCount()).isEqualTo(5L);
        assertThat(entry.totalRevenue()).isEqualByComparingTo("12500.00");
        assertThat(entry.lastOrderAt()).isEqualTo(lastOrder);
    }

    // ── exportOrdersCsv ───────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void exportOrdersCsv_containsHeaderAndDataRows() {
        Order o = fullOrder("WB-20260523-0001", OrderStatus.DELIVERED,
                new BigDecimal("1250.00"), new BigDecimal("1250.00"));
        given(orderRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(o));

        byte[] csv = service.exportOrdersCsv(null, null, null);
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);

        assertThat(text).startsWith("Order Number,Status,");
        assertThat(text).contains("WB-20260523-0001");
        assertThat(text).contains("DELIVERED");
        assertThat(text).contains("Raj Sharma");
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportOrdersXlsx_producesNonEmptyByteArray() {
        Order o = fullOrder("WB-20260523-0001", OrderStatus.DELIVERED,
                new BigDecimal("1250.00"), new BigDecimal("1250.00"));
        given(orderRepository.findAll(any(Specification.class), any(Sort.class)))
                .willReturn(List.of(o));

        byte[] xlsx = service.exportOrdersXlsx(null, null, null);

        assertThat(xlsx).isNotEmpty();
        // XLSX files start with the ZIP magic bytes (PK header)
        assertThat(xlsx[0]).isEqualTo((byte) 0x50); // 'P'
        assertThat(xlsx[1]).isEqualTo((byte) 0x4B); // 'K'
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Order order(String isoDate, BigDecimal amount) {
        Instant ts = java.time.LocalDate.parse(isoDate)
                .atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        return Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("WB-" + isoDate.replace("-", "") + "-0001")
                .status(OrderStatus.DELIVERED)
                .paymentMethod("cod")
                .placedAt(ts)
                .subtotalAmount(amount)
                .totalAmount(amount)
                .currencyCode("INR")
                .shippingAddressSnapshot(Map.of("city", "Mumbai"))
                .customerSnapshot(Map.of("fullName", "Raj Sharma"))
                .build();
    }

    private Order fullOrder(String number, OrderStatus status,
                            BigDecimal subtotal, BigDecimal total) {
        User user = User.builder()
                .id(UUID.randomUUID()).email("buyer@test.com")
                .passwordHash("h").authProvider(AuthProvider.LOCAL)
                .role(Role.CUSTOMER).active(true).build();
        Customer customer = Customer.builder()
                .id(UUID.randomUUID()).user(user).fullName("Raj Sharma").build();

        return Order.builder()
                .id(UUID.randomUUID())
                .orderNumber(number)
                .customer(customer)
                .status(status)
                .paymentMethod("cod")
                .placedAt(Instant.now())
                .subtotalAmount(subtotal)
                .totalAmount(total)
                .currencyCode("INR")
                .shippingAddressSnapshot(Map.of("city", "Mumbai"))
                .customerSnapshot(Map.of("fullName", "Raj Sharma", "businessName", "Sharma Traders"))
                .build();
    }
}

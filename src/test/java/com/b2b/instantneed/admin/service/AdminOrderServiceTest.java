package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.admin.dto.AdminOrderSummary;
import com.b2b.instantneed.admin.dto.UpdateOrderStatusRequest;
import com.b2b.instantneed.common.dto.PagedResponse;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.common.service.EmailService;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.order.dto.OrderResponse;
import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.order.entity.OrderStatus;
import com.b2b.instantneed.order.repository.OrderRepository;
import com.b2b.instantneed.user.entity.AuthProvider;
import com.b2b.instantneed.user.entity.Role;
import com.b2b.instantneed.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock AuditLogService auditLog;
    @Mock EmailService    emailService;

    @InjectMocks AdminOrderService service;

    // ── listOrders ────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void listOrders_noFilters_returnsAllOrders() {
        Order o = order(OrderStatus.PENDING);
        Page<Order> page = new PageImpl<>(List.of(o));
        given(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(page);

        PagedResponse<AdminOrderSummary> res = service.listOrders(
                null, null, null, null, null, 1, 20);

        assertThat(res.items()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listOrders_withStatusFilter_passesSpecification() {
        Page<Order> page = new PageImpl<>(List.of());
        given(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(page);

        service.listOrders("PENDING", null, null, null, null, 1, 20);

        verify(orderRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listOrders_invalidStatus_throwsBadRequest() {
        assertThatThrownBy(() -> service.listOrders("INVALID", null, null, null, null, 1, 20))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listOrders_invalidDateFormat_throwsBadRequest() {
        assertThatThrownBy(() -> service.listOrders(null, null, "not-a-date", null, null, 1, 20))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── getOrder ──────────────────────────────────────────────────────────────

    @Test
    void getOrder_found_returnsFullDetail() {
        Order o = order(OrderStatus.CONFIRMED);
        given(orderRepository.findWithItemsById(o.getId())).willReturn(Optional.of(o));

        OrderResponse res = service.getOrder(o.getId());

        assertThat(res.orderNumber()).isEqualTo("WB-20260523-0001");
        assertThat(res.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void getOrder_notFound_throws404() {
        UUID id = UUID.randomUUID();
        given(orderRepository.findWithItemsById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    void updateStatus_pendingToConfirmed_updatesAndAudits() {
        Order o = order(OrderStatus.PENDING);
        given(orderRepository.findWithItemsById(o.getId())).willReturn(Optional.of(o));
        given(orderRepository.save(any())).willReturn(o);

        OrderResponse res = service.updateStatus(o.getId(),
                new UpdateOrderStatusRequest("CONFIRMED"));

        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(auditLog).log(eq(AuditLogService.UPDATE), eq(AuditLogService.ORDER),
                any(), contains("CONFIRMED"), any(), any());
    }

    @Test
    void updateStatus_invalidStatusString_throwsBadRequest() {
        Order o = order(OrderStatus.PENDING);
        given(orderRepository.findWithItemsById(o.getId())).willReturn(Optional.of(o));

        assertThatThrownBy(() -> service.updateStatus(o.getId(),
                new UpdateOrderStatusRequest("GARBAGE")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateStatus_nullStatus_throwsBadRequest() {
        Order o = order(OrderStatus.PENDING);
        given(orderRepository.findWithItemsById(o.getId())).willReturn(Optional.of(o));

        assertThatThrownBy(() -> service.updateStatus(o.getId(),
                new UpdateOrderStatusRequest(null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateStatus_blankStatus_throwsBadRequest() {
        Order o = order(OrderStatus.PENDING);
        given(orderRepository.findWithItemsById(o.getId())).willReturn(Optional.of(o));

        assertThatThrownBy(() -> service.updateStatus(o.getId(),
                new UpdateOrderStatusRequest("  ")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Order order(OrderStatus status) {
        User u = User.builder()
                .id(UUID.randomUUID()).email("buyer@test.com")
                .passwordHash("hash").authProvider(AuthProvider.LOCAL)
                .role(Role.CUSTOMER).active(true).build();
        Customer c = Customer.builder()
                .id(UUID.randomUUID()).user(u).fullName("Raj Sharma").build();

        return Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("WB-20260523-0001")
                .customer(c)
                .status(status)
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

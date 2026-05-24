package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.admin.dto.AdminCustomerSummary;
import com.b2b.instantneed.admin.dto.UpdateCustomerRoleRequest;
import com.b2b.instantneed.admin.dto.UpdateCustomerStatusRequest;
import com.b2b.instantneed.common.dto.PagedResponse;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.user.entity.AuthProvider;
import com.b2b.instantneed.user.entity.Role;
import com.b2b.instantneed.user.entity.User;
import com.b2b.instantneed.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCustomerServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock UserRepository     userRepository;
    @Mock AuditLogService    auditLog;

    @InjectMocks AdminCustomerService service;

    // ── listCustomers ─────────────────────────────────────────────────────────

    @Test
    void listCustomers_returnsPagedResponse() {
        User u = user(Role.CUSTOMER);
        Customer c = customer(u);
        Page<Customer> page = new PageImpl<>(List.of(c));
        given(customerRepository.findAll(any(Pageable.class))).willReturn(page);

        PagedResponse<AdminCustomerSummary> res = service.listCustomers(null, 1, 20);

        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).email()).isEqualTo("test@example.com");
        assertThat(res.items().get(0).role()).isEqualTo("CUSTOMER");
    }

    // ── getCustomer ───────────────────────────────────────────────────────────

    @Test
    void getCustomer_found_returnsSummary() {
        User u = user(Role.CUSTOMER);
        Customer c = customer(u);
        given(customerRepository.findById(c.getId())).willReturn(Optional.of(c));

        AdminCustomerSummary summary = service.getCustomer(c.getId());

        assertThat(summary.customerId()).isEqualTo(c.getId());
        assertThat(summary.fullName()).isEqualTo("Raj Sharma");
    }

    @Test
    void getCustomer_notFound_throws404() {
        UUID id = UUID.randomUUID();
        given(customerRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCustomer(id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    void updateStatus_deactivate_setsActiveToFalseAndAudits() {
        User u = user(Role.CUSTOMER);
        Customer c = customer(u);
        given(customerRepository.findById(c.getId())).willReturn(Optional.of(c));

        AdminCustomerSummary res = service.updateStatus(c.getId(),
                new UpdateCustomerStatusRequest(false));

        assertThat(u.isEnabled()).isFalse();
        verify(userRepository).save(u);
        verify(auditLog).log(eq(AuditLogService.UPDATE), eq(AuditLogService.CUSTOMER),
                any(), any(), any(), any());
    }

    @Test
    void updateStatus_activate_setsActiveToTrue() {
        User u = user(Role.CUSTOMER);
        u.setActive(false);
        Customer c = customer(u);
        given(customerRepository.findById(c.getId())).willReturn(Optional.of(c));

        service.updateStatus(c.getId(), new UpdateCustomerStatusRequest(true));

        assertThat(u.isEnabled()).isTrue();
    }

    // ── updateRole ────────────────────────────────────────────────────────────

    @Test
    void updateRole_customerToAdmin_succeeds() {
        User u = user(Role.CUSTOMER);
        Customer c = customer(u);
        given(customerRepository.findById(c.getId())).willReturn(Optional.of(c));

        AdminCustomerSummary res = service.updateRole(c.getId(),
                new UpdateCustomerRoleRequest("ADMIN"));

        assertThat(u.getRole()).isEqualTo(Role.ADMIN);
        assertThat(res.role()).isEqualTo("ADMIN");
        verify(userRepository).save(u);
        verify(auditLog).log(eq(AuditLogService.UPDATE), eq(AuditLogService.CUSTOMER),
                any(), contains("ADMIN"), any(), any());
    }

    @Test
    void updateRole_adminToCustomer_succeeds() {
        User u = user(Role.ADMIN);
        Customer c = customer(u);
        given(customerRepository.findById(c.getId())).willReturn(Optional.of(c));

        service.updateRole(c.getId(), new UpdateCustomerRoleRequest("CUSTOMER"));

        assertThat(u.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void updateRole_superAdminTarget_throwsBadRequest() {
        // Cannot change SUPER_ADMIN's role via API
        User u = user(Role.SUPER_ADMIN);
        Customer c = customer(u);
        given(customerRepository.findById(c.getId())).willReturn(Optional.of(c));

        assertThatThrownBy(() -> service.updateRole(c.getId(),
                new UpdateCustomerRoleRequest("CUSTOMER")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private User user(Role role) {
        User u = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("hash")
                .authProvider(AuthProvider.LOCAL)
                .role(role)
                .active(true)
                .build();
        return u;
    }

    private Customer customer(User user) {
        return Customer.builder()
                .id(UUID.randomUUID())
                .user(user)
                .fullName("Raj Sharma")
                .businessName("Sharma Traders")
                .build();
    }
}

package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.admin.dto.AdminCustomerSummary;
import com.b2b.instantneed.admin.dto.UpdateCustomerRoleRequest;
import com.b2b.instantneed.admin.dto.UpdateCustomerStatusRequest;
import com.b2b.instantneed.common.dto.PagedResponse;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.user.entity.Role;
import com.b2b.instantneed.user.entity.User;
import com.b2b.instantneed.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLog;

    @Transactional(readOnly = true)
    public PagedResponse<AdminCustomerSummary> listCustomers(int page, int limit) {
        int safePage = Math.max(1, page) - 1;
        int safeLimit = Math.min(Math.max(1, limit), 100);
        Page<Customer> customers = customerRepository.findAll(
                PageRequest.of(safePage, safeLimit, Sort.by("createdAt").descending()));
        return PagedResponse.of(customers.map(c -> AdminCustomerSummary.from(c.getUser(), c)));
    }

    @Transactional(readOnly = true)
    public AdminCustomerSummary getCustomer(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND",
                        "Customer not found: " + customerId));
        return AdminCustomerSummary.from(customer.getUser(), customer);
    }

    @Transactional
    public AdminCustomerSummary updateRole(UUID customerId, UpdateCustomerRoleRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND",
                        "Customer not found: " + customerId));
        User user = customer.getUser();

        // Guard: never allow API-level SUPER_ADMIN promotion
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw ApiException.badRequest("CANNOT_CHANGE_SUPER_ADMIN_ROLE",
                    "SUPER_ADMIN role cannot be modified through the API");
        }

        Role newRole = Role.valueOf(request.role());
        AdminCustomerSummary before = AdminCustomerSummary.from(user, customer);
        user.setRole(newRole);
        userRepository.save(user);
        AdminCustomerSummary after = AdminCustomerSummary.from(user, customer);

        auditLog.log(AuditLogService.UPDATE, AuditLogService.CUSTOMER, customerId,
                "Role changed to " + newRole + " for user: " + customer.getFullName(),
                before, after);
        return after;
    }

    @Transactional
    public AdminCustomerSummary updateStatus(UUID customerId, UpdateCustomerStatusRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND",
                        "Customer not found: " + customerId));
        User user = customer.getUser();
        AdminCustomerSummary before = AdminCustomerSummary.from(user, customer);
        user.setActive(request.active());
        userRepository.save(user);
        AdminCustomerSummary after = AdminCustomerSummary.from(user, customer);
        String action = request.active() ? "Activated customer account" : "Deactivated customer account";
        auditLog.log(AuditLogService.UPDATE, AuditLogService.CUSTOMER, customerId,
                action + ": " + customer.getFullName(), before, after);
        return after;
    }
}

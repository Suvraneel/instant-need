package com.b2b.instantneed.common.security;

import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final CustomerRepository customerRepository;

    public User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) return user;
        throw ApiException.unauthorized("NOT_AUTHENTICATED", "No authenticated user in context");
    }

    public UUID currentUserId() {
        return currentUser().getId();
    }

    public Customer currentCustomer() {
        UUID userId = currentUserId();
        return customerRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND",
                        "No customer profile for user: " + userId));
    }
}

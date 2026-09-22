package com.b2b.instantneed.customer.service;

import com.b2b.instantneed.cart.entity.Cart;
import com.b2b.instantneed.cart.repository.CartRepository;
import com.b2b.instantneed.common.security.SecurityUtils;
import com.b2b.instantneed.common.storage.StorageService;
import com.b2b.instantneed.customer.entity.Address;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.customer.repository.AddressRepository;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.order.repository.OrderRepository;
import com.b2b.instantneed.user.entity.User;
import com.b2b.instantneed.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Deletes customer account data while preserving anonymized order records that
 * may be needed for accounting, tax, fraud prevention, or legal retention.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private static final String DELETED_CUSTOMER = "Deleted Customer";
    private static final Map<String, Object> ANONYMOUS_CUSTOMER = Map.of(
            "fullName", DELETED_CUSTOMER,
            "businessName", "",
            "gstinUin", ""
    );
    private static final Map<String, Object> ANONYMOUS_ADDRESS = Map.of(
            "fullName", DELETED_CUSTOMER,
            "line1", "[redacted]",
            "line2", "",
            "city", "[redacted]",
            "state", "[redacted]",
            "country", "India",
            "postalCode", "[redacted]",
            "phoneNumber", "[redacted]"
    );

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final StorageService storageService;

    @Transactional
    public void deleteCurrentAccount() {
        User user = securityUtils.currentUser();
        Customer customer = securityUtils.currentCustomer();

        List<Order> orders = orderRepository.findAllByCustomerId(customer.getId());
        for (Order order : orders) {
            if (order.getInvoicePath() != null && !order.getInvoicePath().isBlank()) {
                storageService.delete(order.getInvoicePath());
            }
            order.setInvoicePath(null);
            order.setCustomerSnapshot(ANONYMOUS_CUSTOMER);
            order.setShippingAddressSnapshot(ANONYMOUS_ADDRESS);
            order.setCustomerNote(null);
        }
        orderRepository.saveAll(orders);

        List<Cart> carts = cartRepository.findAllByCustomerId(customer.getId());
        cartRepository.deleteAll(carts);

        List<Address> addresses = addressRepository.findByCustomerId(customer.getId());
        addressRepository.deleteAll(addresses);

        customer.setFullName(DELETED_CUSTOMER);
        customer.setBusinessName(null);
        customer.setGstVatNumber(null);
        customer.setNotes(null);
        customer.setPushToken(null);
        customer.setDefaultShippingAddressId(null);
        customerRepository.save(customer);

        // Keep the row for the retained order-to-customer foreign key, but make
        // the login unusable and remove identifiers that can identify the user.
        user.setEmail("deleted+" + user.getId() + "@deleted.invalid");
        user.setPhoneNumber(null);
        user.setActive(false);
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetTokenExpiresAt(null);
        userRepository.save(user);

        log.info("Deleted customer account data: userId={}, customerId={}, retainedOrders={}",
                user.getId(), customer.getId(), orders.size());
    }
}

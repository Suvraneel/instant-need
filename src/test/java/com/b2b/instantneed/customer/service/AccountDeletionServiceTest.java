package com.b2b.instantneed.customer.service;

import com.b2b.instantneed.cart.entity.Cart;
import com.b2b.instantneed.cart.entity.CartStatus;
import com.b2b.instantneed.cart.repository.CartRepository;
import com.b2b.instantneed.common.security.SecurityUtils;
import com.b2b.instantneed.common.storage.StorageService;
import com.b2b.instantneed.customer.entity.Address;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.customer.repository.AddressRepository;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.order.repository.OrderRepository;
import com.b2b.instantneed.user.entity.AuthProvider;
import com.b2b.instantneed.user.entity.Role;
import com.b2b.instantneed.user.entity.User;
import com.b2b.instantneed.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock AddressRepository addressRepository;
    @Mock CartRepository cartRepository;
    @Mock OrderRepository orderRepository;
    @Mock UserRepository userRepository;
    @Mock SecurityUtils securityUtils;
    @Mock StorageService storageService;

    @InjectMocks AccountDeletionService service;

    private User user;
    private Customer customer;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("buyer@test.com")
                .phoneNumber("9876543210")
                .passwordHash("hash")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.CUSTOMER)
                .active(true)
                .build();
        customer = Customer.builder()
                .id(UUID.randomUUID())
                .user(user)
                .fullName("Raj Sharma")
                .businessName("Sharma Traders")
                .pushToken("expo-token")
                .build();
        given(securityUtils.currentUser()).willReturn(user);
        given(securityUtils.currentCustomer()).willReturn(customer);
    }

    @Test
    void deleteCurrentAccount_anonymizesRetainedOrdersAndDisablesLogin() {
        String invoicePath = "https://cdn.example.com/invoices/old.pdf";
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .invoicePath(invoicePath)
                .customerSnapshot(Map.of("fullName", "Raj Sharma"))
                .shippingAddressSnapshot(Map.of("fullName", "Raj Sharma"))
                .build();
        Address address = Address.builder().id(UUID.randomUUID()).customer(customer).build();
        Cart cart = Cart.builder().id(UUID.randomUUID()).customer(customer).status(CartStatus.ACTIVE).build();
        given(orderRepository.findAllByCustomerId(customer.getId())).willReturn(List.of(order));
        given(addressRepository.findByCustomerId(customer.getId())).willReturn(List.of(address));
        given(cartRepository.findAllByCustomerId(customer.getId())).willReturn(List.of(cart));

        service.deleteCurrentAccount();

        assertThat(order.getInvoicePath()).isNull();
        assertThat(order.getCustomerSnapshot()).containsEntry("fullName", "Deleted Customer");
        assertThat(order.getShippingAddressSnapshot()).containsEntry("phoneNumber", "[redacted]");
        assertThat(order.getCustomerNote()).isNull();
        assertThat(customer.getFullName()).isEqualTo("Deleted Customer");
        assertThat(customer.getPushToken()).isNull();
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getEmail()).isEqualTo("deleted+" + user.getId() + "@deleted.invalid");
        assertThat(user.getPhoneNumber()).isNull();

        verify(storageService).delete(invoicePath);
        verify(orderRepository).saveAll(List.of(order));
        verify(cartRepository).deleteAll(List.of(cart));
        verify(addressRepository).deleteAll(List.of(address));
        verify(customerRepository).save(customer);
        verify(userRepository).save(user);
    }
}

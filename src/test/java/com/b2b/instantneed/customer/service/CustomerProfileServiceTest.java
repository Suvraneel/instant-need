package com.b2b.instantneed.customer.service;

import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.common.security.SecurityUtils;
import com.b2b.instantneed.customer.dto.*;
import com.b2b.instantneed.customer.entity.Address;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.customer.repository.AddressRepository;
import com.b2b.instantneed.customer.repository.CustomerRepository;
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
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerProfileServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock AddressRepository  addressRepository;
    @Mock SecurityUtils      securityUtils;

    @InjectMocks CustomerProfileService service;

    private User     user;
    private Customer customer;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("buyer@test.com")
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
                .gstVatNumber("27AAPFU0939F1ZV")
                .build();

        given(securityUtils.currentUser()).willReturn(user);
        given(securityUtils.currentCustomer()).willReturn(customer);
    }

    // ── getProfile ────────────────────────────────────────────────────────────

    @Test
    void getProfile_returnsUserAndCustomerData() {
        ProfileResponse profile = service.getProfile();

        assertThat(profile.email()).isEqualTo("buyer@test.com");
        assertThat(profile.fullName()).isEqualTo("Raj Sharma");
        assertThat(profile.businessName()).isEqualTo("Sharma Traders");
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    void updateProfile_updatesFieldsAndSanitizesHtml() {
        given(customerRepository.save(any())).willReturn(customer);

        UpdateProfileRequest req = new UpdateProfileRequest(
                "<b>New Name</b>", "<script>evil</script>Biz", null, null);
        ProfileResponse res = service.updateProfile(req);

        assertThat(customer.getFullName()).isEqualTo("New Name");   // HTML stripped
        assertThat(customer.getBusinessName()).isEqualTo("Biz");    // script stripped
        verify(customerRepository).save(customer);
    }

    @Test
    void updateProfile_nullFields_notApplied() {
        given(customerRepository.save(any())).willReturn(customer);

        service.updateProfile(new UpdateProfileRequest(null, null, null, null));

        assertThat(customer.getFullName()).isEqualTo("Raj Sharma"); // unchanged
    }

    // ── getAddresses ──────────────────────────────────────────────────────────

    @Test
    void getAddresses_returnsMappedList() {
        Address a = address(customer, true);
        given(addressRepository.findByCustomerId(customer.getId())).willReturn(List.of(a));

        List<AddressResponse> result = service.getAddresses();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).city()).isEqualTo("Mumbai");
    }

    // ── addAddress ────────────────────────────────────────────────────────────

    @Test
    void addAddress_nonDefault_savesWithoutClearingExisting() {
        Address saved = address(customer, false);
        given(addressRepository.save(any())).willReturn(saved);

        CreateAddressRequest req = new CreateAddressRequest(
                "Office", "Test User", "12 MG Road", null, "Mumbai", "Maharashtra", "India", "400001", null, false);

        AddressResponse res = service.addAddress(req);

        assertThat(res.city()).isEqualTo("Mumbai");
        verify(addressRepository, never()).findByCustomerId(any()); // no default clear
    }

    @Test
    void addAddress_isDefault_clearsExistingDefaultFirst() {
        Address existingDefault = address(customer, true);
        given(addressRepository.findByCustomerId(customer.getId()))
                .willReturn(new ArrayList<>(List.of(existingDefault)));

        Address newAddress = address(customer, true);
        newAddress.setId(UUID.randomUUID());
        given(addressRepository.save(any())).willReturn(newAddress);
        given(customerRepository.save(any())).willReturn(customer);

        CreateAddressRequest req = new CreateAddressRequest(
                "Home", "Test User", "1 Home St", null, "Delhi", "Delhi", "India", "110001", null, true);

        service.addAddress(req);

        // Existing default was cleared
        assertThat(existingDefault.isDefault()).isFalse();
    }

    // ── deleteAddress ─────────────────────────────────────────────────────────

    @Test
    void deleteAddress_onlyAddress_throwsBadRequest() {
        Address addr = address(customer, false);
        given(addressRepository.findById(addr.getId())).willReturn(Optional.of(addr));
        given(addressRepository.findByCustomerId(customer.getId())).willReturn(List.of(addr));

        assertThatThrownBy(() -> service.deleteAddress(addr.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("only address");
    }

    @Test
    void deleteAddress_defaultAddress_throwsBadRequest() {
        Address defaultAddr = address(customer, true);
        Address otherAddr   = address(customer, false);
        given(addressRepository.findById(defaultAddr.getId())).willReturn(Optional.of(defaultAddr));
        given(addressRepository.findByCustomerId(customer.getId()))
                .willReturn(List.of(defaultAddr, otherAddr));

        assertThatThrownBy(() -> service.deleteAddress(defaultAddr.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("default");
    }

    @Test
    void deleteAddress_valid_deletesSuccessfully() {
        Address defaultAddr = address(customer, true);
        Address extra       = address(customer, false);
        given(addressRepository.findById(extra.getId())).willReturn(Optional.of(extra));
        given(addressRepository.findByCustomerId(customer.getId()))
                .willReturn(List.of(defaultAddr, extra));

        assertThatCode(() -> service.deleteAddress(extra.getId())).doesNotThrowAnyException();
        verify(addressRepository).delete(extra);
    }

    // ── ownedAddress security ─────────────────────────────────────────────────

    @Test
    void deleteAddress_addressBelongsToAnotherCustomer_throws404() {
        Customer otherCustomer = Customer.builder().id(UUID.randomUUID()).user(user).fullName("Other").build();
        Address foreignAddress = address(otherCustomer, false);

        given(addressRepository.findById(foreignAddress.getId())).willReturn(Optional.of(foreignAddress));

        assertThatThrownBy(() -> service.deleteAddress(foreignAddress.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND); // never reveals existence
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static Address address(Customer owner, boolean isDefault) {
        return Address.builder()
                .id(UUID.randomUUID())
                .customer(owner)
                .label("Default")
                .line1("12 MG Road")
                .city("Mumbai")
                .state("Maharashtra")
                .country("India")
                .postalCode("400001")
                .isDefault(isDefault)
                .build();
    }
}

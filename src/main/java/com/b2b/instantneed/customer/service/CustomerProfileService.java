package com.b2b.instantneed.customer.service;

import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.common.security.SecurityUtils;
import com.b2b.instantneed.common.util.HtmlSanitizer;
import com.b2b.instantneed.customer.dto.*;
import com.b2b.instantneed.customer.entity.Address;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.customer.repository.AddressRepository;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile() {
        User user = securityUtils.currentUser();
        Customer customer = securityUtils.currentCustomer();
        return ProfileResponse.from(user, customer);
    }

    @Transactional
    public ProfileResponse updateProfile(UpdateProfileRequest request) {
        User user = securityUtils.currentUser();
        Customer customer = securityUtils.currentCustomer();

        if (request.fullName() != null && !request.fullName().isBlank()) {
            customer.setFullName(HtmlSanitizer.strip(request.fullName()));
        }
        if (request.businessName() != null) {
            customer.setBusinessName(HtmlSanitizer.strip(request.businessName()));
        }
        if (request.gstVatNumber() != null) {
            customer.setGstVatNumber(HtmlSanitizer.strip(request.gstVatNumber()));
        }
        if (request.notes() != null) {
            customer.setNotes(HtmlSanitizer.strip(request.notes()));
        }

        customerRepository.save(customer);
        return ProfileResponse.from(user, customer);
    }

    @Transactional
    public void savePushToken(String token) {
        Customer customer = securityUtils.currentCustomer();
        customer.setPushToken(token);
        customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses() {
        Customer customer = securityUtils.currentCustomer();
        return addressRepository.findByCustomerId(customer.getId())
                .stream().map(AddressResponse::from).toList();
    }

    @Transactional
    public AddressResponse addAddress(CreateAddressRequest request) {
        Customer customer = securityUtils.currentCustomer();

        if (request.isDefault()) {
            clearExistingDefault(customer.getId());
        }

        Address address = Address.builder()
                .customer(customer)
                .label(request.label() != null && !request.label().isBlank() ? request.label() : "Default")
                .fullName(request.fullName())
                .phoneNumber(request.phoneNumber())
                .line1(request.addressLine1())
                .line2(request.addressLine2())
                .city(request.city())
                .state(request.state())
                .country(request.country())
                .postalCode(request.postalCode())
                .isDefault(request.isDefault())
                .build();

        address = addressRepository.save(address);

        if (request.isDefault()) {
            customer.setDefaultShippingAddressId(address.getId());
            customerRepository.save(customer);
        }

        return AddressResponse.from(address);
    }

    @Transactional
    public AddressResponse updateAddress(UUID addressId, UpdateAddressRequest request) {
        Customer customer = securityUtils.currentCustomer();
        Address address = ownedAddress(addressId, customer.getId());

        if (request.label() != null) address.setLabel(request.label());
        if (request.fullName() != null) address.setFullName(request.fullName());
        if (request.phoneNumber() != null) address.setPhoneNumber(request.phoneNumber());
        if (request.addressLine1() != null && !request.addressLine1().isBlank()) address.setLine1(request.addressLine1());
        if (request.addressLine2() != null) address.setLine2(request.addressLine2());
        if (request.city() != null && !request.city().isBlank()) address.setCity(request.city());
        if (request.state() != null && !request.state().isBlank()) address.setState(request.state());
        if (request.country() != null && !request.country().isBlank()) address.setCountry(request.country());
        if (request.postalCode() != null && !request.postalCode().isBlank()) address.setPostalCode(request.postalCode());

        if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefault()) {
            clearExistingDefault(customer.getId());
            address.setDefault(true);
            customer.setDefaultShippingAddressId(address.getId());
            customerRepository.save(customer);
        }

        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(UUID addressId) {
        Customer customer = securityUtils.currentCustomer();
        Address address = ownedAddress(addressId, customer.getId());

        List<Address> all = addressRepository.findByCustomerId(customer.getId());
        if (all.size() == 1) {
            throw ApiException.badRequest("CANNOT_DELETE_ONLY_ADDRESS",
                    "Cannot delete the only address on file");
        }
        if (address.isDefault()) {
            throw ApiException.badRequest("CANNOT_DELETE_DEFAULT_ADDRESS",
                    "Set another address as default before deleting this one");
        }

        addressRepository.delete(address);
    }

    @Transactional
    public AddressResponse setDefaultAddress(UUID addressId) {
        Customer customer = securityUtils.currentCustomer();
        Address address = ownedAddress(addressId, customer.getId());
        clearExistingDefault(customer.getId());
        address.setDefault(true);
        addressRepository.save(address);
        customer.setDefaultShippingAddressId(address.getId());
        customerRepository.save(customer);
        return AddressResponse.from(address);
    }

    private void clearExistingDefault(UUID customerId) {
        addressRepository.findByCustomerId(customerId).stream()
                .filter(Address::isDefault)
                .forEach(a -> {
                    a.setDefault(false);
                    addressRepository.save(a);
                });
    }

    private Address ownedAddress(UUID addressId, UUID customerId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> ApiException.notFound("ADDRESS_NOT_FOUND",
                        "Address not found: " + addressId));
        if (!address.getCustomer().getId().equals(customerId)) {
            throw ApiException.notFound("ADDRESS_NOT_FOUND", "Address not found: " + addressId);
        }
        return address;
    }
}

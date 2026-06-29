package com.b2b.instantneed.customer.controller;

import com.b2b.instantneed.customer.dto.*;
import com.b2b.instantneed.customer.service.CustomerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "My Profile", description = "Customer profile and address management")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class CustomerProfileController {

    private final CustomerProfileService profileService;

    @Operation(summary = "Get the current customer's profile")
    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile() {
        return ResponseEntity.ok(profileService.getProfile());
    }

    @Operation(summary = "Update profile fields (partial update — only non-null fields are applied)")
    @PatchMapping
    public ResponseEntity<ProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(profileService.updateProfile(request));
    }

    @Operation(summary = "List all shipping addresses")
    @GetMapping("/addresses")
    public ResponseEntity<List<AddressResponse>> getAddresses() {
        return ResponseEntity.ok(profileService.getAddresses());
    }

    @Operation(summary = "Add a new shipping address")
    @PostMapping("/addresses")
    public ResponseEntity<AddressResponse> addAddress(@Valid @RequestBody CreateAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profileService.addAddress(request));
    }

    @Operation(summary = "Update an existing address (partial update)")
    @PatchMapping("/addresses/{addressId}")
    public ResponseEntity<AddressResponse> updateAddress(
            @PathVariable UUID addressId,
            @Valid @RequestBody UpdateAddressRequest request) {
        return ResponseEntity.ok(profileService.updateAddress(addressId, request));
    }

    @Operation(summary = "Delete an address (cannot delete the only or default address)")
    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable UUID addressId) {
        profileService.deleteAddress(addressId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set an address as the default shipping address")
    @PatchMapping("/addresses/{addressId}/default")
    public ResponseEntity<AddressResponse> setDefaultAddress(@PathVariable UUID addressId) {
        return ResponseEntity.ok(profileService.setDefaultAddress(addressId));
    }

    @Operation(summary = "Register an Expo push token for order status notifications")
    @PutMapping("/push-token")
    public ResponseEntity<Void> savePushToken(@Valid @RequestBody SavePushTokenRequest request) {
        profileService.savePushToken(request.token());
        return ResponseEntity.noContent().build();
    }
}

package com.b2b.instantneed.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(
        // Items-based order (frontend sends cart items directly)
        List<@Valid OrderItemRequest> items,

        // Saved address ID (use one of the customer's saved addresses)
        UUID shippingAddressId,

        // OR inline address (checkout with a new address)
        @Valid InlineAddressRequest shippingAddress,

        String paymentMethod,
        String notes
) {
    public record OrderItemRequest(UUID productId, int quantity) {}

    public record InlineAddressRequest(
            String fullName,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String country,
            String phoneNumber,
            Boolean saveAddress
    ) {}
}

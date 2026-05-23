package com.b2b.instantneed.cart.controller;

import com.b2b.instantneed.cart.dto.AddToCartRequest;
import com.b2b.instantneed.cart.dto.CartResponse;
import com.b2b.instantneed.cart.dto.UpdateCartItemRequest;
import com.b2b.instantneed.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Cart", description = "Authenticated cart management — one active cart per customer")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Get the current customer's active cart")
    @GetMapping
    public ResponseEntity<CartResponse> getCart() {
        return ResponseEntity.ok(cartService.getCart());
    }

    @Operation(summary = "Add a product to cart (upsert: adds to quantity if already present)")
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@Valid @RequestBody AddToCartRequest request) {
        return ResponseEntity.ok(cartService.addItem(request));
    }

    @Operation(summary = "Set the quantity of a specific cart item (server re-prices server-side)")
    @PatchMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> updateItem(
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(cartService.updateItem(itemId, request));
    }

    @Operation(summary = "Remove a specific item from the cart")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> removeItem(@PathVariable UUID itemId) {
        return ResponseEntity.ok(cartService.removeItem(itemId));
    }

    @Operation(summary = "Clear all items from the cart")
    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        cartService.clearCart();
        return ResponseEntity.noContent().build();
    }
}

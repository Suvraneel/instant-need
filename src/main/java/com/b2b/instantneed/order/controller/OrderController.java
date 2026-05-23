package com.b2b.instantneed.order.controller;

import com.b2b.instantneed.common.dto.PagedResponse;
import com.b2b.instantneed.order.dto.OrderResponse;
import com.b2b.instantneed.order.dto.PlaceOrderRequest;
import com.b2b.instantneed.order.dto.PlaceOrderResponse;
import com.b2b.instantneed.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Orders", description = "Order placement and history for authenticated customers")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Place an order from the active cart (COD / offline payment)")
    @PostMapping
    public ResponseEntity<PlaceOrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request));
    }

    @Operation(summary = "List the current customer's order history")
    @GetMapping
    public ResponseEntity<PagedResponse<OrderResponse>> getOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(orderService.getOrders(page, limit));
    }

    @Operation(summary = "Get full detail of a specific order")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @Operation(summary = "Reorder: copy a past order's items into the active cart")
    @PostMapping("/{orderId}/reorder")
    public ResponseEntity<PlaceOrderResponse> reorder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.reorder(orderId));
    }
}

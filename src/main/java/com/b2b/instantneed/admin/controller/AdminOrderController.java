package com.b2b.instantneed.admin.controller;

import com.b2b.instantneed.admin.dto.AdminOrderSummary;
import com.b2b.instantneed.admin.dto.UpdateOrderStatusRequest;
import com.b2b.instantneed.admin.service.AdminOrderService;
import com.b2b.instantneed.admin.service.AdminReportService;
import com.b2b.instantneed.common.dto.PagedResponse;
import com.b2b.instantneed.order.dto.OrderResponse;
import com.b2b.instantneed.order.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Admin — Orders", description = "Order management and CSV export (ROLE_ADMIN)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService orderService;
    private final AdminReportService reportService;
    private final InvoiceService invoiceService;

    @Operation(summary = "List orders with optional filters: status, customerId, dateFrom, dateTo, orderNumber")
    @GetMapping
    public ResponseEntity<PagedResponse<AdminOrderSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String orderNumber,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(
                orderService.listOrders(status, customerId, dateFrom, dateTo, orderNumber, page, limit));
    }

    @Operation(summary = "Get full order detail")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> get(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @Operation(summary = "Update order status")
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(orderService.updateStatus(orderId, request));
    }

    @Operation(summary = "Regenerate and store the PDF invoice for an order")
    @PostMapping("/{orderId}/invoice/regenerate")
    public ResponseEntity<Map<String, String>> regenerateInvoice(@PathVariable UUID orderId) {
        String url = invoiceService.generateAndStoreById(orderId);
        if (url == null) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Invoice generation failed. Check server logs."));
        }
        return ResponseEntity.ok(Map.of("invoiceUrl", url));
    }

    @Operation(summary = "Export orders to CSV (optional filters: dateFrom, dateTo, status)")
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String status) {
        byte[] csv = reportService.exportOrdersCsv(dateFrom, dateTo, status);
        String filename = "orders-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}

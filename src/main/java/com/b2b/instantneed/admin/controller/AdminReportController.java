package com.b2b.instantneed.admin.controller;

import com.b2b.instantneed.admin.dto.*;
import com.b2b.instantneed.admin.service.AdminReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin — Reports", description = "Sales and activity reports (ROLE_ADMIN)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService reportService;

    // ── GET /summary ──────────────────────────────────────────────────────────

    @Operation(summary = "Dashboard summary — orders by status, revenue this month, customer and product counts")
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummary> summary() {
        return ResponseEntity.ok(reportService.summaryReport());
    }

    // ── GET /sales ────────────────────────────────────────────────────────────

    @Operation(summary = "Sales report by date range with daily breakdown (excludes cancelled orders)")
    @GetMapping("/sales")
    public ResponseEntity<SalesReportResponse> sales(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(reportService.salesReport(dateFrom, dateTo));
    }

    // ── GET /top-products ─────────────────────────────────────────────────────

    @Operation(summary = "Top-selling products by revenue (max 50)")
    @GetMapping("/top-products")
    public ResponseEntity<List<TopProductEntry>> topProducts(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(reportService.topProducts(limit, dateFrom, dateTo));
    }

    // ── GET /customer-activity ────────────────────────────────────────────────

    @Operation(summary = "Most active customers by revenue from non-cancelled orders (max 50)")
    @GetMapping("/customer-activity")
    public ResponseEntity<List<CustomerActivityEntry>> customerActivity(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(reportService.customerActivity(limit));
    }

    // ── GET /orders.csv ───────────────────────────────────────────────────────

    @Operation(summary = "Export orders as CSV — filterable by date range and status")
    @GetMapping(value = "/orders.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String status) {
        byte[] data = reportService.exportOrdersCsv(dateFrom, dateTo, status);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("orders.csv").build().toString())
                .body(data);
    }

    // ── GET /orders.xlsx ──────────────────────────────────────────────────────

    @Operation(summary = "Export orders as XLSX — filterable by date range and status")
    @GetMapping(value = "/orders.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String status) {
        byte[] data = reportService.exportOrdersXlsx(dateFrom, dateTo, status);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("orders.xlsx").build().toString())
                .body(data);
    }
}

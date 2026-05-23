package com.b2b.instantneed.admin.controller;

import com.b2b.instantneed.admin.dto.CustomerActivityEntry;
import com.b2b.instantneed.admin.dto.SalesReportResponse;
import com.b2b.instantneed.admin.dto.TopProductEntry;
import com.b2b.instantneed.admin.service.AdminReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    @Operation(summary = "Sales report by date range (excludes cancelled orders)")
    @GetMapping("/sales")
    public ResponseEntity<SalesReportResponse> sales(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(reportService.salesReport(dateFrom, dateTo));
    }

    @Operation(summary = "Top-selling products by revenue")
    @GetMapping("/top-products")
    public ResponseEntity<List<TopProductEntry>> topProducts(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(reportService.topProducts(limit, dateFrom, dateTo));
    }

    @Operation(summary = "Most active customers by total revenue")
    @GetMapping("/customer-activity")
    public ResponseEntity<List<CustomerActivityEntry>> customerActivity(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(reportService.customerActivity(limit));
    }
}

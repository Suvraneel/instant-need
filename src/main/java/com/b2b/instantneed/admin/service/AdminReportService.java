package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.admin.dto.*;
import com.b2b.instantneed.catalog.repository.ProductRepository;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.order.entity.OrderStatus;
import com.b2b.instantneed.order.repository.OrderItemRepository;
import com.b2b.instantneed.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final OrderRepository     orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository  customerRepository;
    private final ProductRepository   productRepository;

    // ── Dashboard summary ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DashboardSummary summaryReport() {
        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC)
                .withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        long totalOrders      = orderRepository.count();
        long pendingOrders    = orderRepository.countByStatus(OrderStatus.PENDING);
        long processingOrders = orderRepository.countByStatus(OrderStatus.PROCESSING);
        long shippedOrders    = orderRepository.countByStatus(OrderStatus.SHIPPED);
        BigDecimal revenueThisMonth = orderRepository.sumRevenueSince(OrderStatus.CANCELLED, startOfMonth);
        long totalCustomers         = customerRepository.count();
        long newCustomersThisMonth  = customerRepository.countByCreatedAtGreaterThanEqual(startOfMonth);
        long activeProducts         = productRepository.countByActiveTrue();

        return new DashboardSummary(
                totalOrders, pendingOrders, processingOrders, shippedOrders,
                revenueThisMonth, totalCustomers, newCustomersThisMonth, activeProducts);
    }

    // ── Sales report ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SalesReportResponse salesReport(String dateFrom, String dateTo) {
        Instant from = parseDate(dateFrom, false);
        Instant to   = parseDate(dateTo, true);

        Specification<Order> spec = Specification
                .<Order>where((r, q, cb) -> cb.notEqual(r.get("status"), OrderStatus.CANCELLED))
                .and(from != null ? (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("placedAt"), from) :
                        (r, q, cb) -> cb.conjunction())
                .and(to != null ? (r, q, cb) -> cb.lessThanOrEqualTo(r.get("placedAt"), to) :
                        (r, q, cb) -> cb.conjunction());

        List<Order> orders = orderRepository.findAll(spec, Sort.by("placedAt").ascending());

        BigDecimal totalRevenue = orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Group by date for daily breakdown
        Map<String, List<Order>> byDay = orders.stream().collect(
                Collectors.groupingBy(o ->
                        o.getPlacedAt().atZone(ZoneOffset.UTC)
                                .toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)));

        List<SalesReportResponse.DailyEntry> breakdown = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new SalesReportResponse.DailyEntry(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().map(Order::getTotalAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();

        return new SalesReportResponse(
                dateFrom, dateTo,
                orders.size(),
                totalRevenue,
                SalesReportResponse.avg(totalRevenue, orders.size()),
                breakdown);
    }

    // ── Top products (DB-level aggregation — no N+1) ──────────────────────────

    @Transactional(readOnly = true)
    public List<TopProductEntry> topProducts(int limit, String dateFrom, String dateTo) {
        int safeLimit = Math.clamp(limit, 1, 50);
        Instant from  = parseDate(dateFrom, false);
        Instant to    = parseDate(dateTo, true);

        String fromIso = from != null ? from.toString() : null;
        String toIso   = to   != null ? to.toString()   : null;

        List<Object[]> rows = orderItemRepository.aggregateByProduct(
                OrderStatus.CANCELLED.name(), fromIso, toIso,
                PageRequest.of(0, safeLimit));

        return rows.stream()
                .map(r -> new TopProductEntry(
                        r[3] != null ? UUID.fromString((String) r[3]) : null,  // product.id (VARCHAR cast)
                        (String)     r[0],   // product_name_snapshot
                        (String)     r[1],   // sku_snapshot
                        ((Number)    r[4]).longValue(),   // SUM(quantity)
                        (BigDecimal) r[5],   // SUM(line_total)
                        (String)     r[2]))  // currency_code
                .toList();
    }

    // ── Customer activity (DB-level aggregation — no N+1) ────────────────────

    @Transactional(readOnly = true)
    public List<CustomerActivityEntry> customerActivity(int limit) {
        int safeLimit = Math.clamp(limit, 1, 50);

        List<Object[]> rows = orderRepository.aggregateByCustomer(
                OrderStatus.CANCELLED,
                PageRequest.of(0, safeLimit));

        return rows.stream()
                .map(r -> new CustomerActivityEntry(
                        (UUID)       r[0],  // customer.id
                        (String)     r[1],  // fullName
                        (String)     r[2],  // businessName
                        (String)     r[3],  // user.email
                        ((Long)      r[4]), // COUNT(orders) — non-cancelled
                        (BigDecimal) r[5],  // SUM(totalAmount)
                        (Instant)    r[6])) // MAX(placedAt)
                .toList();
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportOrdersCsv(String dateFrom, String dateTo, String status) {
        List<Order> orders = fetchFilteredOrders(dateFrom, dateTo, status);

        StringBuilder csv = new StringBuilder();
        csv.append("Order Number,Status,Customer Name,Business Name,Payment Method," +
                   "Subtotal,Total Amount,Currency,Placed At\n");
        orders.forEach(o -> csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                o.getOrderNumber(),
                o.getStatus().name(),
                csvEscape((String) o.getCustomerSnapshot().getOrDefault("fullName", "")),
                csvEscape((String) o.getCustomerSnapshot().getOrDefault("businessName", "")),
                o.getPaymentMethod(),
                o.getSubtotalAmount(),
                o.getTotalAmount(),
                o.getCurrencyCode(),
                o.getPlacedAt().toString())));

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── XLSX export ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportOrdersXlsx(String dateFrom, String dateTo, String status) {
        List<Order> orders = fetchFilteredOrders(dateFrom, dateTo, status);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Orders");

            // Bold header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            String[] headers = {
                "Order Number", "Status", "Customer Name", "Business Name",
                "Payment Method", "Subtotal", "Total Amount", "Currency", "Placed At"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowIdx = 1;
            for (Order o : orders) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(o.getOrderNumber());
                row.createCell(1).setCellValue(o.getStatus().name());
                row.createCell(2).setCellValue(
                        (String) o.getCustomerSnapshot().getOrDefault("fullName", ""));
                row.createCell(3).setCellValue(
                        (String) o.getCustomerSnapshot().getOrDefault("businessName", ""));
                row.createCell(4).setCellValue(o.getPaymentMethod());
                row.createCell(5).setCellValue(o.getSubtotalAmount().doubleValue());
                row.createCell(6).setCellValue(o.getTotalAmount().doubleValue());
                row.createCell(7).setCellValue(o.getCurrencyCode());
                row.createCell(8).setCellValue(o.getPlacedAt().toString());
            }

            // Auto-size all columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate XLSX export", e);
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private List<Order> fetchFilteredOrders(String dateFrom, String dateTo, String status) {
        Instant from           = parseDate(dateFrom, false);
        Instant to             = parseDate(dateTo, true);
        OrderStatus parsedStatus = status != null && !status.isBlank() ? parseStatus(status) : null;

        Specification<Order> spec = Specification
                .<Order>where(from != null
                        ? (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("placedAt"), from)
                        : (r, q, cb) -> cb.conjunction())
                .and(to != null
                        ? (r, q, cb) -> cb.lessThanOrEqualTo(r.get("placedAt"), to)
                        : (r, q, cb) -> cb.conjunction())
                .and(parsedStatus != null
                        ? (r, q, cb) -> cb.equal(r.get("status"), parsedStatus)
                        : (r, q, cb) -> cb.conjunction());

        return orderRepository.findAll(spec, Sort.by("placedAt").descending());
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private Instant parseDate(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(value);
            return endOfDay
                    ? date.atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
                    : date.atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_DATE", "Date must be in ISO format: YYYY-MM-DD");
        }
    }

    private OrderStatus parseStatus(String value) {
        try {
            return OrderStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_STATUS", "Invalid order status: " + value);
        }
    }
}

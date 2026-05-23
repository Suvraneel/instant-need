package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.admin.dto.CustomerActivityEntry;
import com.b2b.instantneed.admin.dto.SalesReportResponse;
import com.b2b.instantneed.admin.dto.TopProductEntry;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.order.entity.OrderStatus;
import com.b2b.instantneed.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public SalesReportResponse salesReport(String dateFrom, String dateTo) {
        Instant from = parseDate(dateFrom, false);
        Instant to = parseDate(dateTo, true);

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
                        e.getValue().stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();

        return new SalesReportResponse(
                dateFrom, dateTo,
                orders.size(),
                totalRevenue,
                SalesReportResponse.avg(totalRevenue, orders.size()),
                breakdown
        );
    }

    @Transactional(readOnly = true)
    public List<TopProductEntry> topProducts(int limit, String dateFrom, String dateTo) {
        int safeLimit = Math.min(Math.max(1, limit), 50);
        Instant from = parseDate(dateFrom, false);
        Instant to = parseDate(dateTo, true);

        Specification<Order> spec = Specification
                .<Order>where((r, q, cb) -> cb.notEqual(r.get("status"), OrderStatus.CANCELLED))
                .and(from != null ? (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("placedAt"), from) :
                        (r, q, cb) -> cb.conjunction())
                .and(to != null ? (r, q, cb) -> cb.lessThanOrEqualTo(r.get("placedAt"), to) :
                        (r, q, cb) -> cb.conjunction());

        List<Order> orders = orderRepository.findAll(spec);

        // Aggregate order items by product
        record Key(UUID id, String name, String sku, String currency) {}

        Map<Key, long[]> agg = new LinkedHashMap<>();
        orders.forEach(o -> o.getItems().forEach(item -> {
            UUID pid = item.getProduct() != null ? item.getProduct().getId() : null;
            Key key = new Key(pid, item.getProductNameSnapshot(), item.getSkuSnapshot(), item.getCurrencyCode());
            agg.computeIfAbsent(key, k -> new long[]{0, 0});
            agg.get(key)[0] += item.getQuantity();
        }));

        // Second pass for revenue (using BigDecimal) with a proper map
        Map<Key, BigDecimal> revenue = new LinkedHashMap<>();
        orders.forEach(o -> o.getItems().forEach(item -> {
            UUID pid = item.getProduct() != null ? item.getProduct().getId() : null;
            Key key = new Key(pid, item.getProductNameSnapshot(), item.getSkuSnapshot(), item.getCurrencyCode());
            revenue.merge(key, item.getLineTotal(), BigDecimal::add);
        }));

        return revenue.entrySet().stream()
                .sorted(Map.Entry.<Key, BigDecimal>comparingByValue().reversed())
                .limit(safeLimit)
                .map(e -> new TopProductEntry(
                        e.getKey().id(),
                        e.getKey().name(),
                        e.getKey().sku(),
                        agg.get(e.getKey())[0],
                        e.getValue(),
                        e.getKey().currency()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerActivityEntry> customerActivity(int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 50);

        List<Order> allOrders = orderRepository.findAll(Sort.by("placedAt").descending());

        Map<UUID, List<Order>> byCustomer = allOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getCustomer().getId()));

        return byCustomer.entrySet().stream()
                .map(e -> {
                    List<Order> orders = e.getValue();
                    BigDecimal total = orders.stream()
                            .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                            .map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    Instant last = orders.get(0).getPlacedAt();
                    Customer customer = orders.get(0).getCustomer();
                    String email = customer.getUser() != null ? customer.getUser().getEmail() : null;
                    return new CustomerActivityEntry(
                            customer.getId(), customer.getFullName(), customer.getBusinessName(),
                            email, orders.size(), total, last);
                })
                .sorted(Comparator.comparing(CustomerActivityEntry::totalRevenue).reversed())
                .limit(safeLimit)
                .toList();
    }

    public byte[] exportOrdersCsv(String dateFrom, String dateTo, String status) {
        Instant from = parseDate(dateFrom, false);
        Instant to = parseDate(dateTo, true);
        OrderStatus parsedStatus = status != null && !status.isBlank() ? parseStatus(status) : null;

        Specification<Order> spec = Specification
                .<Order>where(from != null ? (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("placedAt"), from) :
                        (r, q, cb) -> cb.conjunction())
                .and(to != null ? (r, q, cb) -> cb.lessThanOrEqualTo(r.get("placedAt"), to) :
                        (r, q, cb) -> cb.conjunction())
                .and(parsedStatus != null ? (r, q, cb) -> cb.equal(r.get("status"), parsedStatus) :
                        (r, q, cb) -> cb.conjunction());

        List<Order> orders = orderRepository.findAll(spec, Sort.by("placedAt").descending());

        StringBuilder csv = new StringBuilder();
        csv.append("Order Number,Status,Customer Name,Payment Method,Total Amount,Currency,Placed At\n");
        orders.forEach(o -> csv.append(String.format("%s,%s,%s,%s,%s,%s,%s\n",
                o.getOrderNumber(),
                o.getStatus().name(),
                csvEscape((String) o.getCustomerSnapshot().getOrDefault("fullName", "")),
                o.getPaymentMethod(),
                o.getTotalAmount(),
                o.getCurrencyCode(),
                o.getPlacedAt().toString())));

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
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

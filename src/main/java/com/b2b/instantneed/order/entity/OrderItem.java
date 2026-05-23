package com.b2b.instantneed.order.entity;

import com.b2b.instantneed.catalog.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Column(name = "sku_snapshot", nullable = false, length = 100)
    private String skuSnapshot;

    @Column(name = "unit_of_measurement_snapshot", nullable = false, length = 50)
    private String unitOfMeasurementSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pricing_tier_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> pricingTierSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

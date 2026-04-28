package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "order_type", length = 10, nullable = false)
    private String orderSide; // BUY or SELL

    @Column(name = "price_type", length = 10, nullable = false)
    private String orderType; // MARKET or LIMIT

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "order_price", precision = 18, scale = 2)
    private BigDecimal price; // LIMIT일 경우 지정단가, MARKET일 경우 NULL 또는 체결가

    @Column(name = "remaining_quantity", nullable = false)
    private Long remainingQuantity;

    @Column(name = "status", length = 20, nullable = false)
    private String orderStatus = "PENDING"; // PENDING, PARTIAL, COMPLETED, CANCELED

    @Column(name = "queued_at")
    private LocalDateTime queuedAt;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Builder
    public Order(Account account, Stock stock, String orderSide, String orderType, Long quantity, BigDecimal price, Long remainingQuantity, String orderStatus, LocalDateTime queuedAt, LocalDateTime orderedAt, LocalDateTime canceledAt) {
        this.account = account;
        this.stock = stock;
        this.orderSide = orderSide;
        this.orderType = orderType;
        this.quantity = quantity;
        this.price = price;
        this.remainingQuantity = (remainingQuantity != null) ? remainingQuantity : quantity;
        this.orderStatus = (orderStatus != null) ? orderStatus : "PENDING";
        this.queuedAt = queuedAt;
        this.orderedAt = orderedAt;
        this.canceledAt = canceledAt;
    }

    public void updateStatus(String newStatus) {
        this.orderStatus = newStatus;
    }

    public void cancel() {
        this.orderStatus = "CANCELED";
        this.canceledAt = LocalDateTime.now();
    }

    public void deductRemaining(Long amount) {
        if (this.remainingQuantity < amount) {
            throw new IllegalArgumentException("Cannot deduct more than remaining.");
        }
        this.remainingQuantity -= amount;
        if (this.remainingQuantity == 0) {
            this.orderStatus = "COMPLETED";
        } else {
            this.orderStatus = "PARTIAL";
        }
    }
}

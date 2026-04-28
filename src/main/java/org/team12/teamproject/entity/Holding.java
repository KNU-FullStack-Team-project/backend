package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "holdings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "holding_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "average_buy_price", precision = 18, scale = 2, nullable = false)
    private BigDecimal averageBuyPrice;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Holding(Account account, Stock stock, Long quantity, BigDecimal averageBuyPrice, LocalDateTime updatedAt) {
        this.account = account;
        this.stock = stock;
        this.quantity = quantity;
        this.averageBuyPrice = averageBuyPrice;
        this.updatedAt = updatedAt;
    }

    public void addQuantity(Long amount, BigDecimal newPrice) {
        // 평단가 계산: ((기존수량 * 기존평단가) + (추가수량 * 신규가격)) / (기존수량 + 추가수량)
        BigDecimal totalOldValue = this.averageBuyPrice.multiply(BigDecimal.valueOf(this.quantity));
        BigDecimal totalNewValue = newPrice.multiply(BigDecimal.valueOf(amount));
        
        this.quantity += amount;
        if (this.quantity > 0) {
            this.averageBuyPrice = totalOldValue.add(totalNewValue)
                .divide(BigDecimal.valueOf(this.quantity), 2, java.math.RoundingMode.HALF_UP);
        } else {
            this.averageBuyPrice = BigDecimal.ZERO;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void deductQuantity(Long amount) {
        if (this.quantity < amount) {
            throw new IllegalArgumentException("Not enough holding quantity");
        }
        this.quantity -= amount;
        this.updatedAt = LocalDateTime.now();
    }
}

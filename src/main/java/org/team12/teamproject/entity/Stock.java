package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "stock_id")
    private Long id;

    // ❌ symbol 삭제

    @Column(name = "stock_code", length = 20, nullable = false)
    private String stockCode;

    @Column(name = "stock_name", length = 100, nullable = false)
    private String stockName;

    @Column(name = "market_type", length = 20, nullable = false)
    private String marketType;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "current_price", precision = 19, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "change_rate", precision = 10, scale = 2)
    private BigDecimal changeRate;

    @Column(name = "change_amount", precision = 19, scale = 2)
    private BigDecimal changeAmount;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Stock(String stockCode, String stockName, String marketType, String industry, Long volume, Boolean isActive, LocalDateTime createdAt) {
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.marketType = marketType;
        this.industry = industry;
        this.volume = volume;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }

    public void updateName(String newName) {
        if (newName != null && !newName.trim().isEmpty()) {
            this.stockName = newName.trim();
        }
    }
    public void updateIndustry(String industry) {
        if (industry != null && !industry.trim().isEmpty()) {
            this.industry = industry.trim();
        }
    }

    public void updateMarketType(String newType) {
        if (newType != null && !newType.trim().isEmpty()) {
            this.marketType = newType.trim();
        }
    }

    public void updateVolume(Long volume) {
        this.volume = volume;
    }

    public void updatePriceInfo(BigDecimal currentPrice, BigDecimal changeRate, BigDecimal changeAmount, Long volume) {
        this.currentPrice = currentPrice;
        this.changeRate = changeRate;
        this.changeAmount = changeAmount;
        this.volume = volume;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Stock stock = (Stock) o;
        return Objects.equals(stockCode, stock.stockCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stockCode);
    }
}
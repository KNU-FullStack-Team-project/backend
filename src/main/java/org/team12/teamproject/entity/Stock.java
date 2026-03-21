package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "stock_id")
    private Long id;

    @Column(name = "stock_code", length = 20, nullable = false)
    private String stockCode;

    @Column(name = "stock_name", length = 100, nullable = false)
    private String stockName;

    @Column(name = "market_type", length = 20, nullable = false)
    private String marketType;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Stock(String stockCode, String stockName, String marketType, Boolean isActive, LocalDateTime createdAt) {
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.marketType = marketType;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Stock stock = (Stock) o;
        return Objects.equals(symbol, stock.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol);
    }
}
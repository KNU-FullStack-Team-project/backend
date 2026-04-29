package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "portfolio_snapshot_seq_gen")
    @SequenceGenerator(name = "portfolio_snapshot_seq_gen", sequenceName = "portfolio_snapshot_seq", allocationSize = 1)
    @Column(name = "snapshot_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "total_asset", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalAsset;

    @Column(name = "cash_balance", nullable = false, precision = 20, scale = 2)
    private BigDecimal cashBalance;

    @Column(name = "stock_value", nullable = false, precision = 20, scale = 2)
    private BigDecimal stockValue;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

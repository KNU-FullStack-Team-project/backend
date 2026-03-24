package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "account_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "competition_id")
    private Long competitionId;

    @Column(name = "account_type", length = 20, nullable = false)
    private String accountType;

    @Column(name = "account_name", length = 100, nullable = false)
    private String accountName;

    @Column(name = "cash_balance", precision = 18, scale = 2, nullable = false)
    private BigDecimal cashBalance;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Account(User user, Long competitionId, String accountType, String accountName, BigDecimal cashBalance, Boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.user = user;
        this.competitionId = competitionId;
        this.accountType = accountType;
        this.accountName = accountName;
        this.cashBalance = cashBalance;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void deductBalance(BigDecimal amount) {
        if (this.cashBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance.");
        }
        this.cashBalance = this.cashBalance.subtract(amount);
    }

    public void addBalance(BigDecimal amount) {
        this.cashBalance = this.cashBalance.add(amount);
    }
}
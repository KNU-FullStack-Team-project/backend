package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "favorite_stocks", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "stock_id"})
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FavoriteStock {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fav_seq")
    @SequenceGenerator(name = "fav_seq", sequenceName = "favorite_stock_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "alert_level", length = 20)
    private String buyAlertLevel; // STRONG_BUY, BUY, NONE

    @Column(name = "sell_alert_level", length = 20)
    private String sellAlertLevel; // STRONG_SELL, SELL, NONE

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

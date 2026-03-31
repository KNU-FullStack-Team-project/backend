package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "competitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Competition {

    @Id
    @Column(name = "competition_id")
    private Long competitionId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "initial_seed_money", nullable = false, precision = 18, scale = 2)
    private BigDecimal initialSeedMoney;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_by_admin_id", nullable = false)
    private Long createdByAdminId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
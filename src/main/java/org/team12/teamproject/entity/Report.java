package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "reports",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_reports_user_target",
                        columnNames = {"target_type", "target_id", "reporter_user_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Report {

    public static final String TARGET_TYPE_POST = "POST";
    public static final String TARGET_TYPE_COMMENT = "COMMENT";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reports_seq_generator")
    @SequenceGenerator(
            name = "reports_seq_generator",
            sequenceName = "REPORTS_SEQ",
            allocationSize = 1
    )
    @Column(name = "report_id")
    private Long id;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporterUser;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Lob
    @Column(name = "detail")
    private String detail;

    @Column(name = "report_status", nullable = false, length = 20)
    private String reportStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_admin_id")
    private User handledAdmin;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
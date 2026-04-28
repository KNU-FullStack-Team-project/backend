package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostReport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "post_reports_seq_generator")
    @SequenceGenerator(name = "post_reports_seq_generator", sequenceName = "POST_REPORTS_SEQ", allocationSize = 1)
    @Column(name = "report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporterUser;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "detail", length = 500)
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
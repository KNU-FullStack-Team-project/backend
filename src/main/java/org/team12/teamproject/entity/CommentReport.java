package org.team12.teamproject.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "comment_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "comment_reports_seq_generator")
    @SequenceGenerator(name = "comment_reports_seq_generator", sequenceName = "COMMENT_REPORTS_SEQ", allocationSize = 1)
    @Column(name = "report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY)
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
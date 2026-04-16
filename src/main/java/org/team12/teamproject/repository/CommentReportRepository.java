package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.CommentReport;

public interface CommentReportRepository extends JpaRepository<CommentReport, Long> {
    boolean existsByCommentIdAndReporterUserId(Long commentId, Long reporterUserId);
    java.util.List<CommentReport> findAllByOrderByCreatedAtDesc();
}

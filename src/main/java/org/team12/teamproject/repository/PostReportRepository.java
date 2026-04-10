package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.PostReport;

public interface PostReportRepository extends JpaRepository<PostReport, Long> {
    boolean existsByPostIdAndReporterUserId(Long postId, Long reporterUserId);
}
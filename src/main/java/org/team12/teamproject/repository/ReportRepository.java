package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.Report;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByTargetTypeAndTargetIdAndReporterUserId(
            String targetType,
            Long targetId,
            Long reporterUserId
    );

    List<Report> findAllByOrderByCreatedAtDesc();

    List<Report> findByTargetTypeOrderByCreatedAtDesc(String targetType);
}
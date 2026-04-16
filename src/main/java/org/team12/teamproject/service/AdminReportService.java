package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.team12.teamproject.dto.AdminReportItemDto;
import org.team12.teamproject.entity.CommentReport;
import org.team12.teamproject.entity.PostReport;
import org.team12.teamproject.repository.CommentReportRepository;
import org.team12.teamproject.repository.PostReportRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final PostReportRepository postReportRepository;
    private final CommentReportRepository commentReportRepository;

    public List<AdminReportItemDto> getReports() {
        List<AdminReportItemDto> items = new ArrayList<>();

        for (PostReport report : postReportRepository.findAllByOrderByCreatedAtDesc()) {
            items.add(
                    AdminReportItemDto.builder()
                            .reportType("POST")
                            .reportId(report.getId())
                            .postId(report.getPost() != null ? report.getPost().getId() : null)
                            .postTitle(report.getPost() != null ? report.getPost().getTitle() : null)
                            .targetContent(report.getPost() != null ? report.getPost().getContent() : null)
                            .reporterNickname(report.getReporterUser() != null ? report.getReporterUser().getNickname() : null)
                            .reporterEmail(report.getReporterUser() != null ? report.getReporterUser().getEmail() : null)
                            .reason(report.getReason())
                            .detail(report.getDetail())
                            .reportStatus(report.getReportStatus())
                            .createdAt(report.getCreatedAt())
                            .build()
            );
        }

        for (CommentReport report : commentReportRepository.findAllByOrderByCreatedAtDesc()) {
            items.add(
                    AdminReportItemDto.builder()
                            .reportType("COMMENT")
                            .reportId(report.getId())
                            .postId(report.getComment() != null && report.getComment().getPost() != null ? report.getComment().getPost().getId() : null)
                            .commentId(report.getComment() != null ? report.getComment().getId() : null)
                            .postTitle(report.getComment() != null && report.getComment().getPost() != null ? report.getComment().getPost().getTitle() : null)
                            .targetContent(report.getComment() != null ? report.getComment().getContent() : null)
                            .reporterNickname(report.getReporterUser() != null ? report.getReporterUser().getNickname() : null)
                            .reporterEmail(report.getReporterUser() != null ? report.getReporterUser().getEmail() : null)
                            .reason(report.getReason())
                            .detail(report.getDetail())
                            .reportStatus(report.getReportStatus())
                            .createdAt(report.getCreatedAt())
                            .build()
            );
        }

        items.sort(Comparator.comparing(AdminReportItemDto::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }
}

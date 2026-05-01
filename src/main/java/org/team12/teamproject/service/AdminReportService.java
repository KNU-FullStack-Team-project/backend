package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.team12.teamproject.dto.AdminReportItemDto;
import org.team12.teamproject.entity.Comment;
import org.team12.teamproject.entity.Post;
import org.team12.teamproject.entity.Report;
import org.team12.teamproject.repository.CommentRepository;
import org.team12.teamproject.repository.PostRepository;
import org.team12.teamproject.repository.ReportRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public List<AdminReportItemDto> getReports() {
        return reportRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toAdminReportItemDto)
                .toList();
    }

    private AdminReportItemDto toAdminReportItemDto(Report report) {
        if (Report.TARGET_TYPE_POST.equals(report.getTargetType())) {
            return toPostReportDto(report);
        }

        if (Report.TARGET_TYPE_COMMENT.equals(report.getTargetType())) {
            return toCommentReportDto(report);
        }

        return AdminReportItemDto.builder()
                .reportType(report.getTargetType())
                .reportId(report.getId())
                .reporterNickname(report.getReporterUser() != null ? report.getReporterUser().getNickname() : null)
                .reporterEmail(report.getReporterUser() != null ? report.getReporterUser().getEmail() : null)
                .reason(report.getReason())
                .detail(report.getDetail())
                .reportStatus(report.getReportStatus())
                .createdAt(report.getCreatedAt())
                .build();
    }

    private AdminReportItemDto toPostReportDto(Report report) {
        Post post = postRepository.findById(report.getTargetId()).orElse(null);

        return AdminReportItemDto.builder()
                .reportType("POST")
                .reportId(report.getId())
                .postId(post != null ? post.getId() : report.getTargetId())
                .postTitle(post != null ? post.getTitle() : null)
                .targetContent(post != null ? post.getContent() : null)
                .reporterNickname(report.getReporterUser() != null ? report.getReporterUser().getNickname() : null)
                .reporterEmail(report.getReporterUser() != null ? report.getReporterUser().getEmail() : null)
                .reason(report.getReason())
                .detail(report.getDetail())
                .reportStatus(report.getReportStatus())
                .createdAt(report.getCreatedAt())
                .build();
    }

    private AdminReportItemDto toCommentReportDto(Report report) {
        Comment comment = commentRepository.findById(report.getTargetId()).orElse(null);
        Post post = comment != null ? comment.getPost() : null;

        return AdminReportItemDto.builder()
                .reportType("COMMENT")
                .reportId(report.getId())
                .postId(post != null ? post.getId() : null)
                .commentId(comment != null ? comment.getId() : report.getTargetId())
                .postTitle(post != null ? post.getTitle() : null)
                .targetContent(comment != null ? comment.getContent() : null)
                .reporterNickname(report.getReporterUser() != null ? report.getReporterUser().getNickname() : null)
                .reporterEmail(report.getReporterUser() != null ? report.getReporterUser().getEmail() : null)
                .reason(report.getReason())
                .detail(report.getDetail())
                .reportStatus(report.getReportStatus())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
package org.team12.teamproject.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class AdminReportItemDto {
    private String reportType;
    private Long reportId;
    private Long postId;
    private Long commentId;
    private String postTitle;
    private String targetContent;
    private String reporterNickname;
    private String reporterEmail;
    private String reason;
    private String detail;
    private String reportStatus;
    private LocalDateTime createdAt;
}

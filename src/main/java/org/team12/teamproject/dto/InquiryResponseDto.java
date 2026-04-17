package org.team12.teamproject.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryResponseDto {
    private Long inquiryId;
    private String category;
    private String title;
    private String content;
    private String status;
    private String answer;
    private boolean isReadByUser;
    private LocalDateTime answeredAt;
    private LocalDateTime createdAt;
}

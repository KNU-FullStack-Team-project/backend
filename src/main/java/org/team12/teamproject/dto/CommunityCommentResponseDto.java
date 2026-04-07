package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CommunityCommentResponseDto {
    private Long commentId;
    private Long userId;
    private String nickname;
    private String content;
    private LocalDateTime createdAt;
}
package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CommunityCommentResponseDto {
    private Long commentId;
    private Long parentCommentId;
    private Long userId;
    private String nickname;
    private Integer level;
    private String levelIconUrl;
    private String content;
    private LocalDateTime createdAt;
}

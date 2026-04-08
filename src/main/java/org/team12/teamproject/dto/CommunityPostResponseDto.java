package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CommunityPostResponseDto {
    private Long postId;
    private Long stockId;
    private String stockCode;
    private String stockName;
    private Long userId;
    private String nickname;
    private boolean hasBoughtStock;
    private String title;
    private Integer commentCount;
    private Integer likeCount;
    private Integer viewCount;
    private LocalDateTime createdAt;
}
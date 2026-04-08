package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CommunityPostDetailResponseDto {

    private Long postId;
    private Long stockId;
    private String stockCode;
    private String stockName;
    private Long userId;
    private String nickname;
    private Boolean hasBoughtStock;
    private String title;
    private String content;
    private Integer commentCount;
    private Integer likeCount;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private Boolean likedByCurrentUser;
    private List<CommunityCommentResponseDto> comments;
}
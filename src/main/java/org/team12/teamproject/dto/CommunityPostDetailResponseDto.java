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
    private Integer level;
    private String levelIconUrl;
    private Boolean hasBoughtStock;
    private String title;
    private String content;
    private Integer commentCount;
    private Integer likeCount;
    private Integer dislikeCount;
    private Integer viewCount;
    private String status;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;

    private Boolean likedByCurrentUser;
    private Boolean votedByCurrentUser;
    private String myVoteType;

    private List<CommunityCommentResponseDto> comments;
    private Boolean isNotice;
    private List<CommunityAttachmentResponseDto> attachments;
}

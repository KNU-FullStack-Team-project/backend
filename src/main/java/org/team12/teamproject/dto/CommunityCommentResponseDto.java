package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CommunityCommentResponseDto {
    private Long commentId;
    private Long parentCommentId;
    private Long userId;
    private String nickname;
    private String content;
    private Integer likeCount;
    private Integer dislikeCount;
    private Boolean votedByCurrentUser;
    private String myVoteType;
    private LocalDateTime createdAt;
    private List<CommunityCommentResponseDto> replies;
}

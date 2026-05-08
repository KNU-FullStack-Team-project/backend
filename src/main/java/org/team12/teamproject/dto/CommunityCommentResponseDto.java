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
    private Integer level;
    private String levelIconUrl;
    private String content;
    private Integer likeCount;
    private Integer dislikeCount;
    private Boolean votedByCurrentUser;
    private String myVoteType;
    private LocalDateTime createdAt;
<<<<<<< HEAD
    private List<CommunityCommentResponseDto> replies;
=======
>>>>>>> df90c813e70d99b5af4821dcc99d783ac1690aed
}

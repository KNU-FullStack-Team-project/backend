package org.team12.teamproject.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommunityCommentCreateRequestDto {
    private String content;

    // null이면 일반 댓글, 값이 있으면 해당 댓글의 대댓글
    private Long parentCommentId;
}
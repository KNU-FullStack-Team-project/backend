package org.team12.teamproject.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommunityCommentCreateRequestDto {
    private String content;
    private Long parentCommentId;
}

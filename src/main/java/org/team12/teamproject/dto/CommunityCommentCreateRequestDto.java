package org.team12.teamproject.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommunityCommentCreateRequestDto {
    private String content;
<<<<<<< HEAD
    private Long parentCommentId;
}
=======

    // null이면 일반 댓글, 값이 있으면 해당 댓글의 대댓글
    private Long parentCommentId;
}
>>>>>>> df90c813e70d99b5af4821dcc99d783ac1690aed

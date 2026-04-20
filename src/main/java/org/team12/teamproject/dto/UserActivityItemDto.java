package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserActivityItemDto {
    private String actionType;
    private String actionLabel;
    private String targetType;
    private String targetId;
    private Long postId;
    private String targetLabel;
    private String detail;
    private String occurredAt;
}

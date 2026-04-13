package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserActivityItemDto {
    private String actionType;
    private String targetType;
    private String targetTitle;
    private String description;
    private String occurredAt;
}

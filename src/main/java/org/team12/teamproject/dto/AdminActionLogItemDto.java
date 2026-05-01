package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminActionLogItemDto {
    private String occurredAt;
    private String adminUserId;
    private String adminEmail;
    private String adminNickname;
    private String actionType;
    private String actionLabel;
    private String targetType;
    private String targetId;
    private String targetLabel;
    private String detail;
}

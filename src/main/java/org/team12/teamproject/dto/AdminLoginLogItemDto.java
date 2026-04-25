package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminLoginLogItemDto {
    private String occurredAt;
    private String nickname;
    private String loginId;
    private String actionLabel;
}

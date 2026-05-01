package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileResponseDto {
    private Long id;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private Boolean socialLogin;
    private String role;
    private String status;
    private String suspendedUntil;
    private String suspensionReason;
    private String createdAt;
    private Long accountId;
    private Integer accountCount;
}

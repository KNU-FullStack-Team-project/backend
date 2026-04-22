package org.team12.teamproject.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SocialLoginResultDto {
    private boolean signupRequired;
    private LoginResponseDto login;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private boolean rejoinCandidate;
}

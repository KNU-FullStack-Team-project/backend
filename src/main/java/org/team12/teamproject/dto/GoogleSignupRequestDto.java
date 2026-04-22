package org.team12.teamproject.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleSignupRequestDto {
    private String credential;
    private String nickname;
    private Boolean marketingConsent;
}

package org.team12.teamproject.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {
    private Long userId;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private String role;
    private String message;
    
    @JsonIgnore
    private String token;
    
    @JsonIgnore
    private String refreshToken;
    
    private Long accountId;
    private Boolean captchaRequired;
    private Boolean socialLogin;
}

package org.team12.teamproject.dto;

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
    private String role;
    private String message;
    private String token; // JWT 토큰 필드 추가
    private Long accountId; // 계좌 ID 필드 추가
}
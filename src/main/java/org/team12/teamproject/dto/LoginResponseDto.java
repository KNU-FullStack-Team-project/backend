package org.team12.teamproject.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponseDto {

    private Long userId;
    private String email;
    private String nickname;
    private String role;
    private String message;
}
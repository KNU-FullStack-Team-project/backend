package org.team12.teamproject.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeNicknameRequestDto {
    private String email;
    private String nickname;
}

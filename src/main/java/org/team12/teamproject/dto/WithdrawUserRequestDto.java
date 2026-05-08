package org.team12.teamproject.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WithdrawUserRequestDto {
    private String email;
    private String reason;
    private Boolean deletionAgreed;
}

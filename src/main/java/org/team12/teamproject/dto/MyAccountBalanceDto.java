package org.team12.teamproject.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MyAccountBalanceDto {
    private Long accountId;
    private String accountName;
    private String accountType;
    private String cashBalance;
}

package org.team12.teamproject.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountDepositRequestDto {
    private BigDecimal amount;
}

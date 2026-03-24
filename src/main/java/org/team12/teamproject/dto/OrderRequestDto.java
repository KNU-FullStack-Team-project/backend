package org.team12.teamproject.dto;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class OrderRequestDto {
    private Long accountId;
    private String stockCode;
    private Long quantity;
    private String orderType; // MARKET, LIMIT
    private BigDecimal price; // LIMIT 일때만
}

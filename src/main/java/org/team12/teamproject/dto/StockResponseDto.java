package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockResponseDto {
    private String symbol;        // 종목코드
    private String name;          // 종목명 
    private String currentPrice;  // 현재가
}
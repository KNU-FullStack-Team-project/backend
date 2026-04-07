package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockResponseDto {
    private String symbol;        // 종목코드
    private String name;          // 종목명 
    private String currentPrice;  // 현재가
    private String changeAmount;  // 전일대비
    private String changeRate;    // 전일대비율
    private String volume;        // 누적거래량
    private String basePrice;     // 기준가 (전일 종가)
}
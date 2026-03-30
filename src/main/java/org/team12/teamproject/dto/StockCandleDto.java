package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockCandleDto {
    private String date;      // 일자 (YYYYMMDD)
    private String open;      // 시가
    private String high;      // 고가
    private String low;       // 저가
    private String close;     // 종가
    private String volume;    // 거래량
}

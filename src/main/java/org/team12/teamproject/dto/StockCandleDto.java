package org.team12.teamproject.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCandleDto {
    private String date;      // 일자 (YYYYMMDD)
    private String time;      // 시간 (HHMMSS) - 분봉용
    private String open;      // 시가
    private String high;      // 고가
    private String low;       // 저가
    private String close;     // 종가
    private String volume;    // 거래량
}

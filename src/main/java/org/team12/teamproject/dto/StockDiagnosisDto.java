package org.team12.teamproject.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockDiagnosisDto {
    private String symbol;
    private double totalScore;
    private String signal; // STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL
    
    private double rsi;
    private String rsiSignal;
    
    private String macdSignal;
    
    private String maSignal; // PERFECT_ORDER, REVERSE_ORDER, MIXED
    
    private double currentPrice;
    private double ma60;
    
    private double bollingerMiddle;
    private String bollingerSignal;
    
    private double volumeRatio; // compared to 20-day MA
    private String candlePattern;
    
    private List<DiagnosisDetail> details;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DiagnosisDetail {
        private String indicator;
        private String analysis;
        private double score;
    }
}

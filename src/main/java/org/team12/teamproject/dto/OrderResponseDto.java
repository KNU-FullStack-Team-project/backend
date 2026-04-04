package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class OrderResponseDto {
    private Long id;
    private String orderSide;
    private String orderType;
    private Long quantity;
    private BigDecimal price;
    private String orderStatus;
    private LocalDateTime orderedAt;
    private StockInfo stock;

    @Getter
    @Builder
    public static class StockInfo {
        private String stockCode;
        private String stockName;
    }
}
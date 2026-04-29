package org.team12.teamproject.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
public class AccountDashboardDto {
    private Long accountId;
    private String totalAsset;
    private String cashBalance;
    private BigDecimal rawCashBalance;
    private String totalProfitAmount;
    private String totalReturnRate;

    private List<HoldingItemDto> holdings;
    private List<FavoriteStockDto> favoriteStocks;

    @Getter
    @Setter
    @Builder
    public static class HoldingItemDto {
        private String stockName;
        private String stockCode;
        private Long quantity;
        private String averageBuyPrice;
        private String currentPrice;
        private String holdingValue;
        private BigDecimal holdingValueRaw; // 정렬용 원천 데이터
    }

    @Getter
    @Setter
    @Builder
    public static class FavoriteStockDto {
        private String name;
        private String currentPrice;
        private String changeRate;
        private String volume;
    }
}

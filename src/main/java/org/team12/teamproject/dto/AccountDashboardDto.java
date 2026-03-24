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
    private String totalAsset;
    private String cashBalance;
    private String totalProfitAmount;
    private String totalReturnRate;

    private List<HoldingItemDto> holdings;

    @Getter
    @Setter
    @Builder
    public static class HoldingItemDto {
        private String stockName;
        private Long quantity;
        private String averageBuyPrice;
        private String currentPrice;
    }
}

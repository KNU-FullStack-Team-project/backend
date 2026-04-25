package org.team12.teamproject.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class CompetitionRankingResponseDto {

    private Integer rank;
    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private BigDecimal totalAsset;
    private BigDecimal returnRate;
    private BigDecimal profitAmount;
}

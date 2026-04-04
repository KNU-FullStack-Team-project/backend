package org.team12.teamproject.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class CompetitionRankingResponseDto {

    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private BigDecimal returnRate;
    private BigDecimal profitAmount;
}
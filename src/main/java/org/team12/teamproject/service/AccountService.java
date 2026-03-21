package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.team12.teamproject.dto.AccountDashboardDto;
import org.team12.teamproject.entity.Account;
import org.team12.teamproject.entity.Holding;
import org.team12.teamproject.repository.AccountRepository;
import org.team12.teamproject.repository.HoldingRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final StockService stockService;

    // TODO: 프론트 연동 중 에러 방지용 임시 기본 유저 생성 로직 (1번 유저, 1번 계좌 리턴 혹은 생성)
    public Long getFallbackAccountId() {
        return accountRepository.findById(1L).map(Account::getId).orElseGet(() -> {
            // DB 초기화 후 등 계좌가 아예 없을 때
            return 1L; // 실제 개발 시에는 테스트용 계좌 생성 로직 필요 
        });
    }

    public AccountDashboardDto getDashboard(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found for id: " + accountId));

        BigDecimal cashBalance = account.getCashBalance();
        BigDecimal totalStockValue = BigDecimal.ZERO;
        BigDecimal totalInvestedValue = BigDecimal.ZERO;

        List<AccountDashboardDto.HoldingItemDto> holdingDtos = new ArrayList<>();

        // 현재가 조회 등
        // (Holding엔티티와 연관된 Stock에서 stockCode추출하여 KIS API 등으로 실제 가격 불러오기 진행)
        // 실제 구현에서는 holdingRepository.findByAccountId 로 리스트 조회 후 맵핑
        // 현재는 HoldingRepository에 리스트 조회 메서드가 없다면 findAll 후 filter(MVP)
        
        List<Holding> holdingsList = holdingRepository.findAll().stream()
                .filter(h -> h.getAccount().getId().equals(accountId))
                .toList();
                
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.KOREA);

        for (Holding h : holdingsList) {
            String currentPriceStr = stockService.getStockDetail(h.getStock().getStockCode()).getCurrentPrice();
            BigDecimal currentPrice = BigDecimal.ZERO;
            try { 
                currentPrice = new BigDecimal(currentPriceStr); 
            } catch(Exception ignored){}

            BigDecimal holdingValue = currentPrice.multiply(BigDecimal.valueOf(h.getQuantity()));
            BigDecimal investedValue = h.getAverageBuyPrice().multiply(BigDecimal.valueOf(h.getQuantity()));

            totalStockValue = totalStockValue.add(holdingValue);
            totalInvestedValue = totalInvestedValue.add(investedValue);

            holdingDtos.add(AccountDashboardDto.HoldingItemDto.builder()
                    .stockName(h.getStock().getStockName())
                    .quantity(h.getQuantity())
                    .averageBuyPrice(currencyFormat.format(h.getAverageBuyPrice()))
                    .currentPrice(currencyFormat.format(currentPrice))
                    .build());
        }

        BigDecimal totalAsset = cashBalance.add(totalStockValue);
        BigDecimal totalProfit = totalStockValue.subtract(totalInvestedValue);

        BigDecimal returnRate = BigDecimal.ZERO;
        if (totalInvestedValue.compareTo(BigDecimal.ZERO) > 0) {
            returnRate = totalProfit.divide(totalInvestedValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }

        return AccountDashboardDto.builder()
                .totalAsset(currencyFormat.format(totalAsset))
                .cashBalance(currencyFormat.format(cashBalance))
                .totalProfitAmount((totalProfit.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + currencyFormat.format(totalProfit))
                .totalReturnRate((returnRate.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + String.format("%.2f", returnRate) + "%")
                .holdings(holdingDtos)
                .build();
    }
}

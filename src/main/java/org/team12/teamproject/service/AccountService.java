package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.team12.teamproject.dto.AccountDashboardDto;
import org.team12.teamproject.entity.Account;
import org.team12.teamproject.entity.Holding;
import org.team12.teamproject.repository.AccountRepository;
import org.team12.teamproject.repository.HoldingRepository;

import org.team12.teamproject.repository.UserRepository;
import org.team12.teamproject.entity.User;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final StockService stockService;

    private final UserRepository userRepository;

    public Long getAccountIdByEmail(String email) {
        if (email == null) throw new IllegalArgumentException("Email is required");
        String cleanEmail = email.trim();
        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + cleanEmail));
        
        List<Account> accounts = accountRepository.findByUserId(user.getId());
        if (accounts.isEmpty()) {
            // 이 시점까지 계좌가 없으면 생성 (안전장치)
            Account account = Account.builder()
                    .user(user)
                    .accountType("MAIN")
                    .accountName(user.getNickname() + "의 기본 계좌")
                    .cashBalance(new BigDecimal("5000000"))
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            return accountRepository.save(account).getId();
        }
        return accounts.get(0).getId();
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
            BigDecimal currentPrice = BigDecimal.ZERO;
            try {
                String currentPriceStr = stockService.getStockDetail(h.getStock().getStockCode()).getCurrentPrice();
                currentPrice = new BigDecimal(currentPriceStr);
            } catch (Exception e) {
                log.warn("주식 가격 조회 실패 ({}): {}", h.getStock().getStockCode(), e.getMessage());
                // 가격 조회 실패 시 해당 종목은 0원으로 계산하거나 이전 로직 유지(여기서는 0원 처리)
            }

            BigDecimal holdingValue = currentPrice.multiply(BigDecimal.valueOf(h.getQuantity()));
            BigDecimal investedValue = h.getAverageBuyPrice().multiply(BigDecimal.valueOf(h.getQuantity()));

            totalStockValue = totalStockValue.add(holdingValue);
            totalInvestedValue = totalInvestedValue.add(investedValue);

            holdingDtos.add(AccountDashboardDto.HoldingItemDto.builder()
                    .stockName(h.getStock().getStockName())
                    .quantity(h.getQuantity())
                    .averageBuyPrice(currencyFormat.format(h.getAverageBuyPrice()))
                    .currentPrice(currencyFormat.format(currentPrice))
                    .holdingValue(currencyFormat.format(holdingValue))
                    .build());
        }

        BigDecimal totalAsset = cashBalance.add(totalStockValue);
        BigDecimal totalProfit = totalStockValue.subtract(totalInvestedValue);

        BigDecimal returnRate = BigDecimal.ZERO;
        if (totalInvestedValue.compareTo(BigDecimal.ZERO) > 0) {
            returnRate = totalProfit.divide(totalInvestedValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }

        return AccountDashboardDto.builder()
                .accountId(accountId)
                .totalAsset(currencyFormat.format(totalAsset))
                .cashBalance(currencyFormat.format(cashBalance))
                .rawCashBalance(cashBalance)
                .totalProfitAmount((totalProfit.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + currencyFormat.format(totalProfit))
                .totalReturnRate((returnRate.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + String.format("%.2f", returnRate) + "%")
                .holdings(holdingDtos)
                .build();
    }
}

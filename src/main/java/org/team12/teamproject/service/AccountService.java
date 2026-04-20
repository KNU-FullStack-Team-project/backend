package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.dto.AccountDashboardDto;
import org.team12.teamproject.dto.MyAccountBalanceDto;
import org.team12.teamproject.entity.Account;
import org.team12.teamproject.entity.Holding;
import org.team12.teamproject.repository.AccountRepository;
import org.team12.teamproject.repository.HoldingRepository;

import org.team12.teamproject.repository.UserRepository;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.dto.StockResponseDto;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final StockService stockService;
    private final FavoriteStockService favoriteStockService;

    private final UserRepository userRepository;

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.KOREA);

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

    @Transactional(readOnly = true)
    public AccountDashboardDto getDashboard(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found for id: " + accountId));

        Long userId = account.getUser().getId();
        List<String> favoriteSymbols = favoriteStockService.getFavoriteSymbols(userId);

        // [최적화] 일괄 조회를 통해 개별 API 호출 오버헤드 제거
        List<StockResponseDto> favoriteDetails = stockService.getStockDetails(favoriteSymbols);
        List<AccountDashboardDto.FavoriteStockDto> favoriteStockDtos = favoriteDetails.stream()
                .map(detail -> AccountDashboardDto.FavoriteStockDto.builder()
                        .name(detail.getName())
                        .currentPrice(detail.getCurrentPrice())
                        .changeRate(detail.getChangeRate())
                        .volume(detail.getVolume())
                        .build())
                .toList();

        BigDecimal cashBalance = account.getCashBalance();
        List<Holding> holdingsList = holdingRepository.findByAccountId(accountId);
        List<String> holdingSymbols = holdingsList.stream()
                .map(h -> h.getStock().getStockCode())
                .toList();

        // [최적화] 보유 종목 시세도 벌크로 조회
        List<StockResponseDto> holdingPriceDetails = stockService.getStockDetails(holdingSymbols);
        
        // 시세 정보를 종목 코드별로 맵핑 (성능 최적화)
        Map<String, StockResponseDto> priceMap = holdingPriceDetails.stream()
                .collect(Collectors.toMap(StockResponseDto::getSymbol, d -> d));

        List<AccountDashboardDto.HoldingItemDto> holdingDtos = holdingsList.stream()
                .map(h -> {
                    StockResponseDto priceDto = priceMap.get(h.getStock().getStockCode());
                    BigDecimal currentPrice = BigDecimal.ZERO;
                    String currentPriceFormatted = "0";

                    if (priceDto != null) {
                        try {
                            String cpStr = priceDto.getCurrentPrice().replace(",", "");
                            currentPrice = new BigDecimal(cpStr);
                            currentPriceFormatted = CURRENCY_FORMAT.format(currentPrice);
                        } catch (Exception e) {
                            log.warn("가격 변환 실패 ({}): {}", h.getStock().getStockCode(), e.getMessage());
                        }
                    }

                    BigDecimal holdingValue = currentPrice.multiply(BigDecimal.valueOf(h.getQuantity()));

                    return AccountDashboardDto.HoldingItemDto.builder()
                            .stockName(h.getStock().getStockName())
                            .quantity(h.getQuantity())
                            .averageBuyPrice(CURRENCY_FORMAT.format(h.getAverageBuyPrice()))
                            .currentPrice(currentPriceFormatted)
                            .holdingValue(CURRENCY_FORMAT.format(holdingValue))
                            .holdingValueRaw(holdingValue)
                            .build();
                })
                .toList();

        // 합산 계산 (parallelStream에서 생성된 DTO의 원시값 활용)
        BigDecimal totalStockValue = holdingDtos.stream()
                .map(AccountDashboardDto.HoldingItemDto::getHoldingValueRaw)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // [참고] totalInvestedValue 계산 (Holding 엔티티에서 직접 계산)
        BigDecimal totalInvestedValue = holdingsList.stream()
                .map(h -> h.getAverageBuyPrice().multiply(BigDecimal.valueOf(h.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAsset = cashBalance.add(totalStockValue);
        BigDecimal totalProfit = totalStockValue.subtract(totalInvestedValue);

        BigDecimal returnRate = BigDecimal.ZERO;
        if (totalInvestedValue.compareTo(BigDecimal.ZERO) > 0) {
            returnRate = totalProfit.divide(totalInvestedValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }

        return AccountDashboardDto.builder()
                .accountId(accountId)
                .totalAsset(CURRENCY_FORMAT.format(totalAsset))
                .cashBalance(CURRENCY_FORMAT.format(cashBalance))
                .rawCashBalance(cashBalance)
                .totalProfitAmount((totalProfit.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + CURRENCY_FORMAT.format(totalProfit))
                .totalReturnRate((returnRate.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + String.format("%.2f", returnRate) + "%")
                .holdings(holdingDtos)
                .favoriteStocks(favoriteStockDtos)
                .build();
    }

    public List<MyAccountBalanceDto> getMyAccountBalancesByEmail(String email) {
        if (email == null) throw new IllegalArgumentException("Email is required");

        String cleanEmail = email.trim();
        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + cleanEmail));
        List<Account> accounts = accountRepository.findByUserId(user.getId());

        return accounts.stream()
                .map(account -> MyAccountBalanceDto.builder()
                        .accountId(account.getId())
                        .accountName(account.getAccountName())
                        .accountType(account.getAccountType())
                        .cashBalance(CURRENCY_FORMAT.format(account.getCashBalance()))
                        .build())
                .toList();
    }

    @Transactional
    public MyAccountBalanceDto resetMainAccountBalance(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없습니다."));

        if (!"MAIN".equals(account.getAccountType())) {
            throw new IllegalArgumentException("기본 계좌만 예수금을 리셋할 수 있습니다.");
        }

        holdingRepository.deleteByAccountId(accountId);
        account.resetCashBalance(new BigDecimal("5000000"));
        Account savedAccount = accountRepository.save(account);

        return MyAccountBalanceDto.builder()
                .accountId(savedAccount.getId())
                .accountName(savedAccount.getAccountName())
                .accountType(savedAccount.getAccountType())
                .cashBalance(CURRENCY_FORMAT.format(savedAccount.getCashBalance()))
                .build();
    }
}

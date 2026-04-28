package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.entity.Account;
import org.team12.teamproject.entity.Holding;
import org.team12.teamproject.entity.PortfolioSnapshot;
import org.team12.teamproject.repository.AccountRepository;
import org.team12.teamproject.repository.HoldingRepository;
import org.team12.teamproject.repository.PortfolioSnapshotRepository;
import org.team12.teamproject.dto.StockResponseDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioSnapshotService {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final StockService stockService;

    /**
     * 모든 활성 계좌에 대해 오늘의 자산 스냅샷을 저장합니다.
     */
    @Transactional
    public void captureDailySnapshots() {
        LocalDate today = LocalDate.now();
        List<Account> activeAccounts = accountRepository.findAll().stream()
                .filter(Account::getIsActive)
                .toList();

        log.info(">>> [Snapshot] {}개 계좌에 대한 자산 스냅샷 캡처 시작 (날짜: {})", activeAccounts.size(), today);

        for (Account account : activeAccounts) {
            try {
                captureSnapshotForAccount(account, today);
            } catch (Exception e) {
                log.error(">>> [Snapshot] 계좌 ID {} 스냅샷 저장 실패: {}", account.getId(), e.getMessage());
            }
        }
        
        log.info(">>> [Snapshot] 자산 스냅샷 완료");
    }

    /**
     * 특정 계좌의 자산을 계산하여 스냅샷으로 저장합니다.
     */
    @Transactional
    public void captureSnapshotForAccount(Account account, LocalDate date) {
        // 이미 해당 날짜의 스냅샷이 있다면 건너뜁니다 (중복 방지)
        if (snapshotRepository.findByAccountIdAndSnapshotDate(account.getId(), date).isPresent()) {
            return;
        }

        BigDecimal cashBalance = account.getCashBalance();
        List<Holding> holdings = holdingRepository.findByAccountId(account.getId());
        
        // 보유 종목 시세 조회
        List<String> symbols = holdings.stream()
                .map(h -> h.getStock().getStockCode())
                .toList();
        
        BigDecimal stockValue = BigDecimal.ZERO;
        if (!symbols.isEmpty()) {
            List<StockResponseDto> details = stockService.getStockDetails(symbols);
            Map<String, BigDecimal> priceMap = details.stream()
                    .collect(Collectors.toMap(
                        StockResponseDto::getSymbol, 
                        d -> new BigDecimal(d.getCurrentPrice().replace(",", ""))
                    ));
            
            for (Holding h : holdings) {
                BigDecimal price = priceMap.getOrDefault(h.getStock().getStockCode(), BigDecimal.ZERO);
                stockValue = stockValue.add(price.multiply(BigDecimal.valueOf(h.getQuantity())));
            }
        }

        BigDecimal totalAsset = cashBalance.add(stockValue);

        PortfolioSnapshot snapshot = PortfolioSnapshot.builder()
                .account(account)
                .totalAsset(totalAsset)
                .cashBalance(cashBalance)
                .stockValue(stockValue)
                .snapshotDate(date)
                .createdAt(LocalDateTime.now())
                .build();

        snapshotRepository.save(snapshot);
    }

    /**
     * 특정 계좌의 자산 변동 히스토리를 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<PortfolioSnapshot> getSnapshotHistory(Long accountId) {
        return snapshotRepository.findByAccountIdOrderBySnapshotDateAsc(accountId);
    }
}

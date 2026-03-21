package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.entity.Account;
import org.team12.teamproject.entity.Holding;
import org.team12.teamproject.entity.Order;
import org.team12.teamproject.entity.Stock;
import org.team12.teamproject.repository.AccountRepository;
import org.team12.teamproject.repository.HoldingRepository;
import org.team12.teamproject.repository.OrderRepository;
import org.team12.teamproject.repository.StockRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final StockRepository stockRepository;
    private final HoldingRepository holdingRepository;
    private final StockService stockService; // KIS API 조회용
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 주식 시장가 구매 로직
     */
    @Transactional
    public Order placeMarketBuyOrder(Long accountId, String stockCode, Long quantity) {
        // 1. 엔티티 조회
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found"));

        // 2. KIS 최신 현재가 조회
        // (StockService의 getStockDetail은 Dto를 반환하므로 현재가를 파싱하거나 별도 메서드 필요)
        String currentPriceStr = stockService.getStockDetail(stockCode).getCurrentPrice();
        BigDecimal currentPrice = new BigDecimal(currentPriceStr);
        BigDecimal totalAmount = currentPrice.multiply(BigDecimal.valueOf(quantity));

        // 3. 계좌 잔액 확인 및 차감
        account.deductBalance(totalAmount);

        // 4. 주문 생성 (COMPLETED 처리)
        Order order = Order.builder()
                .account(account)
                .stock(stock)
                .orderSide("BUY")
                .orderType("MARKET")
                .quantity(quantity)
                .price(currentPrice)
                .remainingQuantity(0L)
                .orderStatus("COMPLETED")
                .orderedAt(LocalDateTime.now())
                .build();
        orderRepository.save(order);

        // 5. 포트폴리오(Holding) 추가
        Holding holding = holdingRepository.findByAccountIdAndStockId(accountId, stock.getId())
                .orElseGet(() -> Holding.builder()
                        .account(account)
                        .stock(stock)
                        .quantity(0L)
                        .averageBuyPrice(BigDecimal.ZERO)
                        .updatedAt(LocalDateTime.now())
                        .build());
        
        holding.addQuantity(quantity, currentPrice);
        holdingRepository.save(holding);

        // 6. Redis에 거래 상태 로깅 또는 캐싱 (예시)
        redisTemplate.opsForList().rightPush("orders:completed:" + stockCode, order.getId().toString());

        return order;
    }

    /**
     * 주식 지정가 구매 로직 (뼈대만)
     */
    @Transactional
    public Order placeLimitBuyOrder(Long accountId, String stockCode, Long quantity, BigDecimal limitPrice) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found"));

        BigDecimal totalAmount = limitPrice.multiply(BigDecimal.valueOf(quantity));

        // 1. 잔고 차감(Lock)
        account.deductBalance(totalAmount);

        // 2. 주문 생성 (PENDING 처리)
        Order order = Order.builder()
                .account(account)
                .stock(stock)
                .orderSide("BUY")
                .orderType("LIMIT")
                .quantity(quantity)
                .price(limitPrice)
                .remainingQuantity(quantity)
                .orderStatus("PENDING")
                .orderedAt(LocalDateTime.now())
                .build();
        orderRepository.save(order);

        // 3. Redis 대기열 큐(Sorted Set 등)에 주문 ID 적재하여 실시간 체결 모니터링
        String redisKey = "orders:pending:buy:" + stockCode;
        redisTemplate.opsForZSet().add(redisKey, order.getId().toString(), limitPrice.doubleValue());

        return order;
    }
}

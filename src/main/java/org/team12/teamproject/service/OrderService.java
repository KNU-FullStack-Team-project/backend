package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.entity.Account;
import org.team12.teamproject.entity.Holding;
import org.team12.teamproject.entity.Order;
import org.team12.teamproject.entity.Stock;
import org.team12.teamproject.repository.AccountRepository;
import org.team12.teamproject.dto.OrderResponseDto;
import org.team12.teamproject.repository.HoldingRepository;
import org.team12.teamproject.repository.OrderRepository;
import org.team12.teamproject.repository.StockRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final StockRepository stockRepository;
    private final HoldingRepository holdingRepository;
    private final StockService stockService;
    private final StringRedisTemplate redisTemplate;

    private static final String ORDER_QUEUE_KEY = "orders:queue";

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

        // 4. 주문 생성 (QUEUED 처리)
        Order order = Order.builder()
                .account(account)
                .stock(stock)
                .orderSide("BUY")
                .orderType("MARKET")
                .quantity(quantity)
                .price(currentPrice)
                .remainingQuantity(quantity)
                .orderStatus("QUEUED")
                .orderedAt(LocalDateTime.now())
                .build();
        orderRepository.save(order);

        // 5. Redis Queue에 주문 정보 푸시 (비동기 처리를 위해)
        try {
            redisTemplate.opsForList().rightPush(ORDER_QUEUE_KEY, "MARKET_BUY:" + order.getId());
        } catch (Exception e) {
            log.warn("Redis 주문 큐 푸시 실패: {}", e.getMessage());
        }

        return order;
    }

    /**
     * 주식 시장가 매도 로직
     */
    @Transactional
    public Order placeMarketSellOrder(Long accountId, String stockCode, Long quantity) {
        // 1. 엔티티 조회
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found"));

        // 2. 보유 수량 검증
        Holding holding = holdingRepository.findByAccountIdAndStockId(accountId, stock.getId())
                .orElseThrow(() -> new IllegalArgumentException("보유 중인 주식이 없습니다."));
        
        if (holding.getQuantity() < quantity) {
            throw new IllegalArgumentException("보유 수량이 부족합니다.");
        }

        // 3. KIS 최신 현재가 조회
        String currentPriceStr = stockService.getStockDetail(stockCode).getCurrentPrice();
        BigDecimal currentPrice = new BigDecimal(currentPriceStr);

        // 4. 주문 생성 (PENDING or QUEUED)
        Order order = Order.builder()
                .account(account)
                .stock(stock)
                .orderSide("SELL")
                .orderType("MARKET")
                .quantity(quantity)
                .price(currentPrice)
                .remainingQuantity(quantity)
                .orderStatus("QUEUED")
                .orderedAt(LocalDateTime.now())
                .build();
        orderRepository.save(order);

        // 5. Redis Queue에 주문 정보 푸시
        try {
            redisTemplate.opsForList().rightPush(ORDER_QUEUE_KEY, "MARKET_SELL:" + order.getId());
        } catch (Exception e) {
            log.warn("Redis 주문 큐 푸시 실패: {}", e.getMessage());
        }

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

    /**
     * Redis Queue에서 꺼낸 주문을 실제 체결 처리 (비동기 워커가 호출)
     */
    @Transactional
    public void processOrder(String queueData) {
        String[] parts = queueData.split(":");
        String type = parts[0]; // MARKET_BUY, MARKET_SELL 등
        Long orderId = Long.parseLong(parts[1]);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!"QUEUED".equals(order.getOrderStatus()) && !"PENDING".equals(order.getOrderStatus())) {
            return; // 이미 처리된 주문
        }

        if ("MARKET_BUY".equals(type)) {
            // 매수 체결: Holding 추가
            Holding holding = holdingRepository.findByAccountIdAndStockId(order.getAccount().getId(), order.getStock().getId())
                    .orElseGet(() -> Holding.builder()
                            .account(order.getAccount())
                            .stock(order.getStock())
                            .quantity(0L)
                            .averageBuyPrice(BigDecimal.ZERO)
                            .updatedAt(LocalDateTime.now())
                            .build());
            
            holding.addQuantity(order.getQuantity(), order.getPrice());
            holdingRepository.save(holding);
            
            order.updateStatus("COMPLETED");
            log.info("매수 주문 체결 완료: OrderID={}", orderId);

        } else if ("MARKET_SELL".equals(type)) {
            // 매도 체결: Account 잔액 증가, Holding 차감
            Account account = order.getAccount();
            BigDecimal totalAmount = order.getPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            account.addBalance(totalAmount);
            accountRepository.save(account);

            Holding holding = holdingRepository.findByAccountIdAndStockId(account.getId(), order.getStock().getId())
                    .orElseThrow(() -> new IllegalStateException("보유 주식 데이터가 없습니다."));
            
            holding.deductQuantity(order.getQuantity());
            holdingRepository.save(holding);

            order.updateStatus("COMPLETED");
            log.info("매도 주문 체결 완료: OrderID={}", orderId);
        }
        
        orderRepository.save(order);
    }

    /**
     * 사용자의 주문 내역 조회
     */
    @Transactional(readOnly = true)
    public List<OrderResponseDto> getOrdersByAccountId(Long accountId) {
        return orderRepository.findByAccountIdOrderByOrderedAtDesc(accountId).stream()
                .map(order -> OrderResponseDto.builder()
                        .id(order.getId())
                        .stock(OrderResponseDto.StockInfo.builder()
                                .stockCode(order.getStock().getStockCode())
                                .stockName(order.getStock().getStockName())
                                .build())
                        .orderSide(order.getOrderSide())
                        .orderType(order.getOrderType())
                        .quantity(order.getQuantity())
                        .price(order.getPrice())
                        .orderStatus(order.getOrderStatus())
                        .orderedAt(order.getOrderedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 주문 취소 로직
     */
    @Transactional
    public void cancelOrder(Long orderId, Long accountId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        // 1. 소유권 확인
        if (!order.getAccount().getId().equals(accountId)) {
            throw new IllegalArgumentException("본인의 주문만 취소할 수 있습니다.");
        }

        // 2. 취소 가능한 상태인지 확인 (PENDING, QUEUED)
        String status = order.getOrderStatus();
        if (!"PENDING".equals(status) && !"QUEUED".equals(status)) {
            throw new IllegalStateException("이미 체결되었거나 취소된 주문은 취소할 수 없습니다. (현재 상태: " + status + ")");
        }

        // 3. Redis에서 주문 제거
        if ("QUEUED".equals(status)) {
            // MARKET 주문 (List)
            String fullRedisValue = "MARKET_" + order.getOrderSide().toUpperCase() + ":" + order.getId();
            redisTemplate.opsForList().remove(ORDER_QUEUE_KEY, 1, fullRedisValue);
        } else if ("PENDING".equals(status)) {
            // LIMIT 주문 (ZSet)
            String redisKey = "orders:pending:" + order.getOrderSide().toLowerCase() + ":" + order.getStock().getStockCode();
            redisTemplate.opsForZSet().remove(redisKey, order.getId().toString());
        }

        // 4. DB 상태 업데이트 및 취소 시간 기록
        order.cancel();
        
        // 5. 매수 주문인 경우 차감된 금액 환불
        if ("BUY".equals(order.getOrderSide())) {
            BigDecimal refundAmount = order.getPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            order.getAccount().addBalance(refundAmount);
        }
        
        orderRepository.save(order);
        log.info("주문 취소 완료: OrderID={}, AccountID={}", orderId, accountId);
    }
}

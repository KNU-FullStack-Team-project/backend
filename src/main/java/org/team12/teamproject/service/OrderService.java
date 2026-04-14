package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.dto.OrderResponseDto;
import org.team12.teamproject.dto.StockResponseDto;
import org.team12.teamproject.entity.Account;
import org.team12.teamproject.entity.Holding;
import org.team12.teamproject.entity.NotificationType;
import org.team12.teamproject.entity.Order;
import org.team12.teamproject.entity.Stock;
import org.team12.teamproject.event.PriceUpdateEvent;
import org.team12.teamproject.repository.AccountRepository;
import org.team12.teamproject.repository.HoldingRepository;
import org.team12.teamproject.repository.OrderRepository;
import org.team12.teamproject.repository.StockRepository;
import org.team12.teamproject.util.MarketUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
    private final NotificationService notificationService;

    private static final String ORDER_QUEUE_KEY = "orders:queue";

    @Transactional
    public Order placeMarketBuyOrder(Long accountId, String stockCode, Long quantity, String requestId) {
        checkIdempotency(requestId);
        validateStockCode(stockCode);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        Stock stock = stockService.getOrRegisterStock(stockCode);

        StockResponseDto stockDetail = stockService.getStockDetail(stockCode);
        String currentPriceStr = stockDetail.getCurrentPrice();
        
        // 실시간 현재가가 없거나 "0"인 경우 (장마감 후 등), 기준가(전일 종가)를 fallback으로 사용
        if (currentPriceStr == null || "null".equals(currentPriceStr) || "0".equals(currentPriceStr) || currentPriceStr.trim().isEmpty()) {
            log.info("[OrderService] 현재가를 불러올 수 없어 기준가(전일 종가)를 사용합니다. 종목: {}", stockCode);
            currentPriceStr = stockDetail.getBasePrice();
        }

        if (currentPriceStr == null || "null".equals(currentPriceStr) || "0".equals(currentPriceStr) || currentPriceStr.trim().isEmpty()) {
            throw new IllegalArgumentException("해당 종목의 가격 정보를 외부(KIS)에서 불러오지 못하여 매수할 수 없습니다.");
        }

        BigDecimal currentPrice = new BigDecimal(currentPriceStr);
        BigDecimal totalAmount = currentPrice.multiply(BigDecimal.valueOf(quantity));

        account.deductBalance(totalAmount);
        
        // stock을 DB에 확실히 반영하여 외래키 제약 오류(ORA-02291) 방지
        stockRepository.saveAndFlush(stock);

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
        
        orderRepository.saveAndFlush(order);

        // 시장가 주문은 큐를 거치지 않고 즉시 체결 처리
        processOrder("MARKET_BUY:" + order.getId());

        return order;
    }

    @Transactional
    public Order placeMarketSellOrder(Long accountId, String stockCode, Long quantity, String requestId) {
        checkIdempotency(requestId);
        validateStockCode(stockCode);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        Stock stock = stockService.getOrRegisterStock(stockCode);

        Holding holding = holdingRepository.findByAccountIdAndStockId(accountId, stock.getId())
                .orElseThrow(() -> new IllegalArgumentException("보유 중인 주식이 없습니다."));

        if (holding.getQuantity() < quantity) {
            throw new IllegalArgumentException("보유 수량이 부족합니다.");
        }

        StockResponseDto stockDetail = stockService.getStockDetail(stockCode);
        String currentPriceStr = stockDetail.getCurrentPrice();

        // 실시간 현재가가 없거나 "0"인 경우, 기준가(전일 종가)를 fallback으로 사용
        if (currentPriceStr == null || "null".equals(currentPriceStr) || "0".equals(currentPriceStr) || currentPriceStr.trim().isEmpty()) {
            log.info("[OrderService] 현재가를 불러올 수 없어 기준가(전일 종가)를 사용합니다. 종목: {}", stockCode);
            currentPriceStr = stockDetail.getBasePrice();
        }

        if (currentPriceStr == null || "null".equals(currentPriceStr) || "0".equals(currentPriceStr) || currentPriceStr.trim().isEmpty()) {
            throw new IllegalArgumentException("해당 종목의 가격 정보를 외부(KIS)에서 불러오지 못하여 매도할 수 없습니다.");
        }

        BigDecimal currentPrice = new BigDecimal(currentPriceStr);

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

        // 시장가 주문은 큐를 거치지 않고 즉시 체결 처리
        processOrder("MARKET_SELL:" + order.getId());

        return order;
    }

    @Transactional
    public Order placeLimitBuyOrder(Long accountId, String stockCode, Long quantity, BigDecimal limitPrice, String requestId) {
        checkIdempotency(requestId);
        validateStockCode(stockCode);
        
        // 주식 시장 운영 시간 검증 활성화
        // if (!MarketUtils.isMarketOpen()) {
        //     throw new IllegalStateException("주식 시장 운영 시간(평일 09:00 ~ 15:30)에만 주문이 가능합니다.");
        // }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        Stock stock = stockService.getOrRegisterStock(stockCode);

        StockResponseDto stockDetail = stockService.getStockDetail(stockCode);
        String currentPriceStr = stockDetail.getCurrentPrice();
        if (currentPriceStr == null || "null".equals(currentPriceStr)) {
            throw new IllegalArgumentException("해당 종목의 가격 정보를 불러오지 못하여 매수할 수 없습니다.");
        }
        BigDecimal currentPrice = new BigDecimal(currentPriceStr);
        BigDecimal basePrice = (stockDetail.getBasePrice() != null && !stockDetail.getBasePrice().isEmpty()) 
                ? new BigDecimal(stockDetail.getBasePrice()) 
                : currentPrice;
        BigDecimal lowerLimit = basePrice.multiply(BigDecimal.valueOf(0.7)).setScale(0, RoundingMode.FLOOR);
        BigDecimal upperLimit = basePrice.multiply(BigDecimal.valueOf(1.3)).setScale(0, RoundingMode.CEILING);

        // 상하한가 검증 활성화 (단, 데이터 미동기화로 인해 basePrice가 0인 경우는 제외)
        if (basePrice.compareTo(BigDecimal.ZERO) > 0 && (limitPrice.compareTo(lowerLimit) < 0 || limitPrice.compareTo(upperLimit) > 0)) {
            throw new IllegalArgumentException("지정가는 전일 종가(" + basePrice + ") 기준 ±30% 이내여야 합니다. (범위: " + lowerLimit + " ~ " + upperLimit + ")");
        }

        BigDecimal totalAmount = limitPrice.multiply(BigDecimal.valueOf(quantity));
        account.deductBalance(totalAmount);

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

        String redisKey = "orders:pending:buy:" + stockCode;
        redisTemplate.opsForZSet().add(redisKey, order.getId().toString(), limitPrice.doubleValue());

        return order;
    }

    @Transactional
    public Order placeLimitSellOrder(Long accountId, String stockCode, Long quantity, BigDecimal limitPrice, String requestId) {
        checkIdempotency(requestId);
        validateStockCode(stockCode);

        // 주식 시장 운영 시간 검증 활성화
        // if (!MarketUtils.isMarketOpen()) {
        //     throw new IllegalStateException("주식 시장 운영 시간(평일 09:00 ~ 15:30)에만 주문이 가능합니다.");
        // }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        Stock stock = stockService.getOrRegisterStock(stockCode);

        Holding holding = holdingRepository.findByAccountIdAndStockId(accountId, stock.getId())
                .orElseThrow(() -> new IllegalArgumentException("보유 중인 주식이 없습니다."));

        if (holding.getQuantity() < quantity) {
            throw new IllegalArgumentException("보유 수량이 부족합니다.");
        }

        StockResponseDto stockDetail = stockService.getStockDetail(stockCode);
        String currentPriceStr = stockDetail.getCurrentPrice();
        if (currentPriceStr == null || "null".equals(currentPriceStr)) {
            throw new IllegalArgumentException("해당 종목의 가격 정보를 불러오지 못하여 매도할 수 없습니다.");
        }
        BigDecimal currentPrice = new BigDecimal(currentPriceStr);
        BigDecimal basePrice = (stockDetail.getBasePrice() != null && !stockDetail.getBasePrice().isEmpty()) 
                ? new BigDecimal(stockDetail.getBasePrice()) 
                : currentPrice;
                
        BigDecimal lowerLimit = basePrice.multiply(BigDecimal.valueOf(0.7)).setScale(0, RoundingMode.FLOOR);
        BigDecimal upperLimit = basePrice.multiply(BigDecimal.valueOf(1.3)).setScale(0, RoundingMode.CEILING);

        // 상하한가 검증 활성화
        if (basePrice.compareTo(BigDecimal.ZERO) > 0 && (limitPrice.compareTo(lowerLimit) < 0 || limitPrice.compareTo(upperLimit) > 0)) {
            throw new IllegalArgumentException("지정가는 전일 종가(" + basePrice + ") 기준 ±30% 이내여야 합니다. (범위: " + lowerLimit + " ~ " + upperLimit + ")");
        }

        Order order = Order.builder()
                .account(account)
                .stock(stock)
                .orderSide("SELL")
                .orderType("LIMIT")
                .quantity(quantity)
                .price(limitPrice)
                .remainingQuantity(quantity)
                .orderStatus("PENDING")
                .orderedAt(LocalDateTime.now())
                .build();
        orderRepository.save(order);

        String redisKey = "orders:pending:sell:" + stockCode;
        redisTemplate.opsForZSet().add(redisKey, order.getId().toString(), limitPrice.doubleValue());

        return order;
    }

    @EventListener
    public void handlePriceUpdate(PriceUpdateEvent event) {
        matchLimitOrders(event.getStockCode(), event.getCurrentPrice());
    }

    @Transactional
    public void matchLimitOrders(String stockCode, BigDecimal currentPrice) {
        // 1. 매수 체결 확인 (ZSet에서 score >= currentPrice인 주문 조회)
        // 사용자가 설정한 '지정가'가 '현재가'보다 크거나 같으면 (더 싼 가격이므로) 체결
        String buyKey = "orders:pending:buy:" + stockCode;
        java.util.Set<String> matchingBuys = redisTemplate.opsForZSet().rangeByScore(buyKey, currentPrice.doubleValue(), Double.MAX_VALUE);
        
        if (matchingBuys != null) {
            for (String orderIdStr : matchingBuys) {
                Long orderId = Long.parseLong(orderIdStr);
                processOrder("MARKET_BUY:" + orderId);
                redisTemplate.opsForZSet().remove(buyKey, orderIdStr);
            }
        }

        // 2. 매도 체결 확인 (ZSet에서 score <= currentPrice인 주문 조회)
        // 사용자가 설정한 '지정가'가 '현재가'보다 작거나 같으면 (더 비싼 가격에 파는 것이므로) 체결
        String sellKey = "orders:pending:sell:" + stockCode;
        java.util.Set<String> matchingSells = redisTemplate.opsForZSet().rangeByScore(sellKey, 0, currentPrice.doubleValue());
        
        if (matchingSells != null) {
            for (String orderIdStr : matchingSells) {
                Long orderId = Long.parseLong(orderIdStr);
                processOrder("MARKET_SELL:" + orderId);
                redisTemplate.opsForZSet().remove(sellKey, orderIdStr);
            }
        }
    }

    @Transactional
    public void processOrder(String queueData) {
        String[] parts = queueData.split(":");
        String type = parts[0];
        Long orderId = Long.parseLong(parts[1]);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!"QUEUED".equals(order.getOrderStatus()) && !"PENDING".equals(order.getOrderStatus())) {
            return;
        }

        if ("MARKET_BUY".equals(type)) {
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

        } else if ("MARKET_SELL".equals(type)) {
            Account account = order.getAccount();
            BigDecimal totalAmount = order.getPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            account.addBalance(totalAmount);
            accountRepository.save(account);

            Holding holding = holdingRepository.findByAccountIdAndStockId(account.getId(), order.getStock().getId())
                    .orElseThrow(() -> new IllegalStateException("보유 주식 데이터가 없습니다."));
            
            holding.deductQuantity(order.getQuantity());
            holdingRepository.save(holding);
            order.updateStatus("COMPLETED");
        }
        
        orderRepository.save(order);

        // 알림 발송
        String sideStr = "BUY".equals(order.getOrderSide()) ? "매수" : "매도";
        String title = "주문 체결 소식";
        String message = String.format("[%s] %d주 %s 체결 완료 (가격: %s)",
                order.getStock().getStockName(),
                order.getQuantity(),
                sideStr,
                order.getPrice().setScale(0).toString());

        notificationService.sendNotification(order.getAccount().getUser(), title, message,
                NotificationType.ORDER_COMPLETED);
    }

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

    @Transactional
    public void cancelOrder(Long orderId, Long accountId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        if (!order.getAccount().getId().equals(accountId)) {
            throw new IllegalArgumentException("본인의 주문만 취소할 수 있습니다.");
        }

        String status = order.getOrderStatus();
        if (!"PENDING".equals(status) && !"QUEUED".equals(status)) {
            throw new IllegalStateException("이미 체결되었거나 취소된 주문은 취소할 수 없습니다.");
        }

        if ("QUEUED".equals(status)) {
            String fullRedisValue = "MARKET_" + order.getOrderSide().toUpperCase() + ":" + order.getId();
            redisTemplate.opsForList().remove(ORDER_QUEUE_KEY, 1, fullRedisValue);
        } else if ("PENDING".equals(status)) {
            String redisKey = "orders:pending:" + order.getOrderSide().toLowerCase() + ":"
                    + order.getStock().getStockCode();
            redisTemplate.opsForZSet().remove(redisKey, order.getId().toString());

        }

        order.cancel();
        
        if ("BUY".equals(order.getOrderSide())) {
            BigDecimal refundAmount = order.getPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            order.getAccount().addBalance(refundAmount);
        }
        
        orderRepository.save(order);
    }

    private void checkIdempotency(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        String lockKey = "order:idempotency:" + requestId;
        Boolean isFirstRequest = redisTemplate.opsForValue().setIfAbsent(lockKey, "processed", Duration.ofSeconds(20));
        if (Boolean.FALSE.equals(isFirstRequest)) {
            throw new IllegalStateException("이미 처리 중이거나 완료된 주문 요청입니다. (중복 방지)");
        }
    }

    private void validateStockCode(String stockCode) {
        if (stockCode == null || stockCode.length() != 6 || !stockCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("올바르지 않은 종목 코드 형식입니다. 국내 주식은 6자리 숫자여야 합니다. (입력값: " + stockCode + ")");
        }
    }
}

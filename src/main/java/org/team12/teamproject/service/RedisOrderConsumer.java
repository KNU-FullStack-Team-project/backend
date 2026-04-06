package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisOrderConsumer {

    private final StringRedisTemplate redisTemplate;
    private final OrderService orderService;
    private static final String ORDER_QUEUE_KEY = "orders:queue";

    /**
     * 1초마다 Redis Queue를 확인하여 주문 처리
     */
    @Scheduled(fixedRate = 1000)
    public void consumeOrders() {
        try {
            while (true) {
                String orderData = redisTemplate.opsForList().leftPop(ORDER_QUEUE_KEY);
                if (orderData == null) {
                    break;
                }

                try {
                    log.info("큐에서 주문 추출: {}", orderData);
                    orderService.processOrder(orderData);
                } catch (Exception e) {
                    log.error("주문 처리 중 오류 발생: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            // Connection refused 등 Redis 연결 오류 발생 시 로그만 출력
            // log.trace 사용 혹은 주기적인 warning 방지 필요 시 조절 가능
        }
    }
}

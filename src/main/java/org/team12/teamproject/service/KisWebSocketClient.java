package org.team12.teamproject.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.team12.teamproject.event.PriceUpdateEvent;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisWebSocketClient {

    private final KisWebSocketService authService;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebSocketSession currentSession;
    private final Set<String> subscribedSymbols = ConcurrentHashMap.newKeySet();
    
    // 모의투자 실시간 웹소켓 URL
    private final String WS_URL = "ws://ops.koreainvestment.com:31000/tryitout/H0STCNT0";

    @PostConstruct
    public void connect() {
        try {
            StandardWebSocketClient client = new StandardWebSocketClient();
            this.currentSession = client.execute(new KisWebSocketHandler(), WS_URL).get();
            log.info(">>> KIS 실시간 웹소켓 연결 성공!");
        } catch (Exception e) {
            log.error(">>> KIS 웹소켓 연결 실패: {}", e.getMessage());
            // 재연결 로직은 스케줄러 등을 통해 보완 가능
        }
    }

    @PreDestroy
    public void disconnect() {
        if (currentSession != null && currentSession.isOpen()) {
            try {
                currentSession.close();
            } catch (Exception e) {
                log.warn("웹소켓 종료 오류: {}", e.getMessage());
            }
        }
    }

    /**
     * 특정 종목의 실시간 시세를 구독(Subscribe)
     */
    public void subscribe(String symbol) {
        if (currentSession == null || !currentSession.isOpen()) {
            log.warn("웹소켓이 연결되어 있지 않아 {} 주식 구독을 실패했습니다.", symbol);
            return;
        }

        if (subscribedSymbols.contains(symbol)) {
            return; // 이미 구독 중
        }

        String approvalKey = authService.getApprovalKey();
        if (approvalKey == null) {
            log.warn("Approval Key 획득 실패로 구독 취소");
            return;
        }

        try {
            Map<String, Object> header = new HashMap<>();
            header.put("approval_key", approvalKey);
            header.put("custtype", "P");
            header.put("tr_type", "1"); // 1: 등록(구독)
            header.put("content-type", "utf-8");

            Map<String, Object> input = new HashMap<>();
            input.put("tr_id", "H0STCNT0");
            input.put("tr_key", symbol);

            Map<String, Object> body = new HashMap<>();
            body.put("input", input);

            Map<String, Object> request = new HashMap<>();
            request.put("header", header);
            request.put("body", body);

            String payload = objectMapper.writeValueAsString(request);
            currentSession.sendMessage(new TextMessage(payload));
            
            subscribedSymbols.add(symbol);
            log.info(">>> 실시간 웹소켓 구독 요청 완료: {}", symbol);

        } catch (Exception e) {
            log.error("구독 송신 에러: {}", e.getMessage());
        }
    }

    /**
     * KIS 서버에서 내려오는 실시간 데이터를 처리하는 내부 핸들러
     */
    private class KisWebSocketHandler extends TextWebSocketHandler {
        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            String payload = message.getPayload();

            // JSON 메시지인지 (구독 응답/핑퐁 등), 아니면 데이터 메시지(파이프 통신 구조)인지 판별
            if (payload.startsWith("{")) {
                handleJsonMessage(payload);
            } else {
                handleRealtimeData(payload);
            }
        }

        private void handleJsonMessage(String payload) {
            try {
                JsonNode node = objectMapper.readTree(payload);
                if (node.has("header") && node.get("header").has("tr_id")) {
                    if ("PINGPONG".equals(node.get("header").get("tr_id").asText())) {
                        // KIS 서버에서 세션 유지를 위해 핑퐁을 요구하면 그대로 반사 (요즘은 불필요할 수도 있음)
                        log.debug("PINGPONG recv");
                    }
                } else if (node.has("body") && node.get("body").has("msg1")) {
                    log.info("웹소켓 응답: {}", node.get("body").get("msg1").asText());
                }
            } catch (Exception e) {
                log.warn("JSON 응답 처리 에러: {}", e.getMessage());
            }
        }

        private void handleRealtimeData(String payload) {
            // 구조: 통신구분(0/1) | TR_ID | Data_Cnt | Data1^Data2^...
            String[] parts = payload.split("\\|");
            if (parts.length < 4) return;

            String trId = parts[1];
            if (!"H0STCNT0".equals(trId)) return; // 체결가 응답만 처리

            String[] dataFields = parts[3].split("\\^");
            if (dataFields.length < 14) return;

            // KIS H0STCNT0 필드 매핑 
            // 0: 종목코드, 1: 체결시간, 2: 현재가, 3: 전일대비부호, 4: 전일대비, 5: 등락율, 13: 누적거래량
            String symbol = dataFields[0];
            String currentPrice = dataFields[2];
            String changeAmount = dataFields[4];
            String changeRate = dataFields[5];
            String volume = dataFields[13];

            // 0원이면 제외 (장외 등 이상치)
            if ("0".equals(currentPrice) || currentPrice.isEmpty()) return;

            // Redis에서 해당 종목의 기존 데이터 로드 (기준가는 실시간으로 내려오지 않기 때문)
            updateRedisAndEmit(symbol, currentPrice, changeAmount, changeRate, volume);
        }

        private void updateRedisAndEmit(String symbol, String currentPrice, String changeAmount, String changeRate, String volume) {
            String cacheKey = "stock:price:" + symbol;
            String basePrice = "0";

            try {
                String existing = redisTemplate.opsForValue().get(cacheKey);
                if (existing != null) {
                    String[] existParts = existing.split(":");
                    if (existParts.length >= 5) {
                        basePrice = existParts[4]; // 기존 basePrice 보존
                    }
                }

                // 새로운 데이터 포맷 조합
                String newValue = String.format("%s:%s:%s:%s:%s",
                        currentPrice,
                        changeAmount,
                        changeRate,
                        volume,
                        basePrice);

                redisTemplate.opsForValue().set(cacheKey, newValue, Duration.ofMinutes(30));
                
                // 프론트엔드로 즉각적으로 전달되게끔 내부 Event Bus에 푸시
                eventPublisher.publishEvent(new PriceUpdateEvent(
                        this, symbol, new BigDecimal(currentPrice)
                ));

                log.debug("실시간 웹소켓 시세 갱신 -> {}: {}원", symbol, currentPrice);
            } catch (Exception e) {
                log.warn("실시간 시세 갱신 중 오류: {}", e.getMessage());
            }
        }
        
        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
            log.warn("웹소켓 연결이 종료되었습니다. (사유: {})", status.getReason());
            currentSession = null;
            subscribedSymbols.clear();
        }
    }
}

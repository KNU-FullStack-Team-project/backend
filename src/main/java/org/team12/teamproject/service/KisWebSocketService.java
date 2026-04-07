package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisWebSocketService {

    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${kis.api.url}")
    private String apiUrl;

    @Value("${kis.api.app-key}")
    private String appKey;

    @Value("${kis.api.app-secret}")
    private String appSecret;

    // 모의투자/실투자 여부에 따라 웹소켓 도메인이 다르므로, 설정 파일 기반으로 추후 확장 가능
    private static final String WS_URL_MOCK = "ws://ops.koreainvestment.com:31000";

    /**
     * 웹소켓 연결을 위한 접속키(Approval Key) 발급
     */
    public String getApprovalKey() {
        String redisKey = "kis:ws_approval_key:" + appKey;
        String cachedKey = redisTemplate.opsForValue().get(redisKey);

        if (cachedKey != null) {
            return cachedKey; // 기존 캐시 반환
        }

        return issueNewApprovalKey(redisKey);
    }

    private String issueNewApprovalKey(String redisKey) {
        String url = apiUrl + "/oauth2/Approval";
        log.info(">>> KIS Websocket Approval Key 발급 요청: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", appKey);
        body.put("secretkey", appSecret); // 웹소켓 Approval은 secretkey 명칭 사용

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> responseBody = response.getBody();

            if (responseBody != null && responseBody.containsKey("approval_key")) {
                String newKey = (String) responseBody.get("approval_key");
                // Approval Key의 유효기간은 24시간
                redisTemplate.opsForValue().set(redisKey, newKey, Duration.ofHours(23));
                log.info(">>> KIS Websocket Approval Key 발급 및 Redis 저장 완료");
                return newKey;
            }
        } catch (Exception e) {
            log.error(">>> KIS Websocket Approval Key 발급 실패: {}", e.getMessage());
        }

        return null;
    }

    // TODO: (Phase 2) StandardWebSocketClient를 이용한 실시간 시세 구독(SUBSCRIBE) 수신 및 콜백 구현
}

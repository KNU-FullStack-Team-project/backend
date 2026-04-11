package org.team12.teamproject.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseService {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        // 타임아웃을 30분(1,800,000ms)으로 설정합니다.
        SseEmitter emitter = new SseEmitter(1800000L); 
        
        // 연결 즉시 더미 데이터를 보내 연결 성공을 알림
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected"));
        } catch (IOException e) {
            log.error("SSE 연결 초기 데이터 전송 실패: {}", e.getMessage());
        }

        emitters.put(userId, emitter);

        emitter.onCompletion(() -> {
            log.info("SSE 연결 종료: Completion (UserId: {})", userId);
            emitters.remove(userId);
        });
        emitter.onTimeout(() -> {
            log.info("SSE 연결 종료: Timeout (UserId: {})", userId);
            emitters.remove(userId);
        });
        emitter.onError((e) -> {
            log.debug("SSE 연결 중단 (UserId: {}): {}", userId, e.getMessage());
            emitters.remove(userId);
        });

        return emitter;
    }

    public void sendNotification(Long userId, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(data));
            } catch (IOException e) {
                log.error("SSE 알림 전송 실패 (UserId: {}): {}", userId, e.getMessage());
                emitters.remove(userId);
            }
        }
    }
}

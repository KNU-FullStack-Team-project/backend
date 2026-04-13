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

        // ✅ 1. 기존 emitter 있으면 종료 (중복 방지)
        if (emitters.containsKey(userId)) {
            emitters.get(userId).complete();
        }

        SseEmitter emitter = new SseEmitter(1800000L);
        emitters.put(userId, emitter);

        // ✅ 2. 초기 연결 확인 이벤트
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected"));
        } catch (IOException e) {
            log.debug("초기 SSE 연결 실패 (UserId: {})", userId);
            emitter.complete(); // ✅ 중요
        }

        // ✅ 3. 공통 제거 로직
        emitter.onCompletion(() -> {
            log.info("SSE 종료: Completion (UserId: {})", userId);
            emitters.remove(userId);
        });

        emitter.onTimeout(() -> {
            log.info("SSE 종료: Timeout (UserId: {})", userId);
            emitters.remove(userId);
        });

        emitter.onError((e) -> {
            log.debug("SSE 에러 (UserId: {})", userId);
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
                // ✅ 4. 핵심: 에러 로그 → debug + complete
                log.debug("SSE 연결 끊김 (UserId: {})", userId);

                emitter.complete();   // ✅ 반드시 추가
                emitters.remove(userId);
            }
        }
    }
}
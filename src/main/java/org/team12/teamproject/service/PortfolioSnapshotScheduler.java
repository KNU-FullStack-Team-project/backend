package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioSnapshotScheduler {

    private final PortfolioSnapshotService snapshotService;

    /**
     * 매일 밤 11시 50분에 실행 (장 마감 후 충분한 시간 뒤)
     * 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 50 23 * * *")
    public void runDailySnapshot() {
        log.info(">>> [Scheduler] 일일 자산 스냅샷 작업 시작...");
        snapshotService.captureDailySnapshots();
    }

    /**
     * [테스트용] 서버가 켜질 때 오늘자 스냅샷이 없다면 즉시 하나 생성합니다.
     * 데이터가 아예 없으면 그래프를 볼 수 없으므로 초기 데이터 확보용입니다.
     */
    @PostConstruct
    public void initSnapshot() {
        try {
            log.info(">>> [Scheduler] 초기 자산 스냅샷 체크 시작...");
            snapshotService.captureDailySnapshots();
        } catch (Exception e) {
            log.error(">>> [Scheduler] 초기 스냅샷 생성 중 오류 발생 (하지만 서버는 계속 실행됩니다): {}", e.getMessage());
        }
    }
}

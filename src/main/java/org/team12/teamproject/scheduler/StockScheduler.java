package org.team12.teamproject.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.team12.teamproject.entity.Stock;
import org.team12.teamproject.repository.StockRepository;
import org.team12.teamproject.service.StockService;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockScheduler {

    private final StockRepository stockRepository;
    private final StockService stockService;

    /**
     * 평일(월-금) 장 시간(9시-16시) 동안 1분마다 시세 갱신
     * (테스트를 위해 현재는 1분마다 무조건 실행되도록 설정)
     */
    @Scheduled(fixedRate = 60000)
    public void updateStockPrices() {
        log.info("시세 데이터 자동 갱신 스케줄러 시작");
        
        List<Stock> activeStocks = stockRepository.findAll(); // 실제로는 isActive=true 필터링 권장
        
        for (Stock stock : activeStocks) {
            try {
                // getStockDetail 내부에서 API 호출 후 Redis 캐시를 수행함
                stockService.getStockDetail(stock.getStockCode());
                log.debug("종목 시세 갱신 완료: {}", stock.getStockName());
            } catch (Exception e) {
                log.error("종목 시세 갱신 실패 ({}): {}", stock.getStockName(), e.getMessage());
            }
        }
        
        log.info("시세 데이터 자동 갱신 스케줄러 완료");
    }
}

package org.team12.teamproject.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.team12.teamproject.dto.StockCandleDto;
import org.team12.teamproject.dto.StockDiagnosisDto;
import org.team12.teamproject.entity.FavoriteStock;
import org.team12.teamproject.entity.NotificationType;
import org.team12.teamproject.repository.FavoriteStockRepository;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockDiagnosisScheduler {

    private final FavoriteStockRepository favoriteStockRepository;
    private final StockService stockService;
    private final TechnicalIndicatorService indicatorService;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 매 10분마다 실행 (장 시간 중: 월~금 09:00 ~ 15:30 등 크론표현식 가능, 현재는 데모용 10분 주기)
    @Scheduled(fixedRate = 600000)
    public void diagnoseFavoriteStocks() {
        log.info("관심 종목 진단 및 알림 스케줄러 시작");

        List<String> symbols = favoriteStockRepository.findAllFavoriteSymbols();
        if (symbols.isEmpty()) {
            log.info("관심 종목으로 등록된 주식이 없습니다.");
            return;
        }

        for (String symbol : symbols) {
            try {
                // 1. 일봉 데이터 조회
                List<StockCandleDto> candles = stockService.getStockHistory(symbol, "D");
                if (candles == null || candles.isEmpty()) continue;

                // 2. 진단 점수 계산
                StockDiagnosisDto diagnosis = indicatorService.diagnose(symbol, candles);
                
                // 3. Redis에 캐싱
                String cacheKey = "stock:diagnosis:" + symbol;
                String jsonResult = objectMapper.writeValueAsString(diagnosis);
                redisTemplate.opsForValue().set(cacheKey, jsonResult, Duration.ofMinutes(60));

                // 4. 점수 변동 감지 및 알림 발송 (STRONG_BUY, BUY 판별)
                checkAndSendAlerts(symbol, diagnosis);

            } catch (Exception e) {
                log.error("종목 [{}] 진단 중 오류 발생: {}", symbol, e.getMessage());
            }
        }
        
        log.info("관심 종목 진단 스케줄러 종료");
    }

    private void checkAndSendAlerts(String symbol, StockDiagnosisDto diagnosis) {
        String prevScoreKey = "stock:diagnosis_prev:" + symbol;
        String prevScoreStr = redisTemplate.opsForValue().get(prevScoreKey);
        
        double currentScore = diagnosis.getTotalScore();
        double prevScore = prevScoreStr != null ? Double.parseDouble(prevScoreStr) : 0.0;
        
        // 매수/매도 신호 감지 (9점, 5점 기준)
        boolean isStrongBuy = prevScore < 9.0 && currentScore >= 9.0;
        boolean isBuy = prevScore < 5.0 && currentScore >= 5.0 && currentScore < 9.0;
        boolean isStrongSell = prevScore > -9.0 && currentScore <= -9.0;
        boolean isSell = prevScore > -5.0 && currentScore <= -5.0 && currentScore > -9.0;

        if (isStrongBuy || isBuy || isStrongSell || isSell) {
            String signal = isStrongBuy ? "STRONG_BUY" : (isBuy ? "BUY" : (isStrongSell ? "STRONG_SELL" : "SELL"));
            List<FavoriteStock> favorites = favoriteStockRepository.findAlertCandidatesBySymbol(symbol);
            
            for (FavoriteStock fav : favorites) {
                boolean shouldSend = false;
                String buyLvl = fav.getBuyAlertLevel();
                String sellLvl = fav.getSellAlertLevel();

                if ("STRONG_BUY".equals(signal)) shouldSend = "STRONG_BUY".equals(buyLvl) || "BUY".equals(buyLvl);
                else if ("BUY".equals(signal)) shouldSend = "BUY".equals(buyLvl);
                else if ("STRONG_SELL".equals(signal)) shouldSend = "STRONG_SELL".equals(sellLvl) || "SELL".equals(sellLvl);
                else if ("SELL".equals(signal)) shouldSend = "SELL".equals(sellLvl);

                if (shouldSend && fav.getUser() != null) {
                    String emoji = signal.contains("BUY") ? "🔥" : "⚠️";
                    String signalKr = isStrongBuy ? "강한 매수" : (isBuy ? "매수" : (isStrongSell ? "강한 매도" : "매도"));
                    String title = emoji + " [" + symbol + "] " + signalKr + " 시그널 발생!";
                    String message = "진단 점수가 " + currentScore + "점에 도달했습니다. " + signalKr + " 타이밍을 확인해보세요.";
                    notificationService.sendNotification(fav.getUser(), title, message, NotificationType.PRICE_ALERT);
                }
            }
        }

        redisTemplate.opsForValue().set(prevScoreKey, String.valueOf(currentScore), Duration.ofDays(7));
    }
}

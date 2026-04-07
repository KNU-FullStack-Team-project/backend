package org.team12.teamproject.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.entity.Stock;
import org.team12.teamproject.repository.StockRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisMasterSyncService {

    private final StockRepository stockRepository;

    private static final String KOSPI_URL = "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip";
    private static final String KOSDAQ_URL = "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip";

    /**
     * 서버 시작 시 한 번 실행하여 전 종목을 초기화 / 업데이트 합니다.
     * ApplicationReadyEvent를 사용하여 다른 빈들이 모두 준비된 후 안전하게 실행합니다.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void syncOnStartup() {
        log.info(">>> [Startup] KIS 전 종목(KOSPI/KOSDAQ) 동기화를 시작합니다.");
        try {
            syncMarket(KOSPI_URL, "KOSPI");
            syncMarket(KOSDAQ_URL, "KOSDAQ");
            log.info(">>> [Startup] KIS 전 종목 동기화 완료!");
        } catch (Exception e) {
            log.error(">>> [Startup] 동기화 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 매일 오전 8시(시장 개장 전) 정기 업데이트 스케줄러
     */
    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    public void syncDaily() {
        log.info(">>> [Scheduled] KIS 일일 전 종목 동기화를 시작합니다.");
        try {
            syncMarket(KOSPI_URL, "KOSPI");
            syncMarket(KOSDAQ_URL, "KOSDAQ");
            log.info(">>> [Scheduled] 일일 동기화 완료!");
        } catch (Exception e) {
            log.error(">>> [Scheduled] 동기화 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    private void syncMarket(String zipUrl, String marketType) throws Exception {
        log.info("{} 마스터 파일 다운로드 및 파싱 시작...", marketType);
        
        // 기존 DB에 저장된 주식 목록 해시맵 구성 (빠른 검색용)
        Map<String, Stock> existingStocks = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getStockCode, Function.identity(), (v1, v2) -> v1)); // 중복 예방

        List<Stock> newStocks = new ArrayList<>();
        List<Stock> updatedStocks = new ArrayList<>();
        int insertCount = 0;
        int updateCount = 0;

        try (ZipInputStream zis = new ZipInputStream(new URL(zipUrl).openStream())) {
            ZipEntry entry = zis.getNextEntry();
            if (entry != null) {
                // MS949 (또는 EUC-KR) 인코딩 필수
                BufferedReader br = new BufferedReader(new InputStreamReader(zis, "MS949"));
                String line;
                
                while ((line = br.readLine()) != null) {
                    byte[] bytes = line.getBytes("MS949");
                    
                    if (bytes.length < 61) {
                        continue; // 비정상 라인 무시
                    }

                    // KIS 마스터 기준: 0~9 단축코드, 9~21 표준코드, 21~61 한글명
                    String shortCode = new String(bytes, 0, 9, "MS949").trim();
                    String stockName = new String(bytes, 21, 40, "MS949").trim();

                    // KOSDAQ 파일에는 단축코드 뒤에 KOSDAQ 정보 등이 더 붙을 수 있으므로 trim 필수
                    // 스팩주 등 특수 문자가 들어간 경우에도 정상 파싱됨.

                    // DB 체크
                    Stock existing = existingStocks.get(shortCode);
                    if (existing == null) {
                        Stock newStock = Stock.builder()
                                .stockCode(shortCode)
                                .stockName(stockName)
                                .marketType(marketType)
                                .isActive(true)
                                .createdAt(LocalDateTime.now())
                                .build();
                        newStocks.add(newStock);
                        insertCount++;
                    } else {
                        // 이름이 바뀐 경우 (상호 변경 등) 업데이트
                        if (!existing.getStockName().equals(stockName)) {
                            existing.updateName(stockName);
                            updatedStocks.add(existing);
                            updateCount++;
                        }
                    }
                    
                    // 대량 배치 처리를 위함 방지 차원 (필요 시 조정)
                    if (newStocks.size() >= 1000) {
                        stockRepository.saveAll(newStocks);
                        newStocks.clear();
                    }
                }
            }
        }

        // 잔여분 저장
        if (!newStocks.isEmpty()) {
            stockRepository.saveAll(newStocks);
        }
        
        if (!updatedStocks.isEmpty()) {
            stockRepository.saveAll(updatedStocks);
        }

        log.info("{} 동기화 완료: 신규 추가 {}건, 상호 변경 {}건", marketType, insertCount, updateCount);
    }
}

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
     * [최적화] 거대 트랜잭션을 방지하기 위해 @Transactional 제거
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        // [최적화] 서버 시작 시 동기화 작업을 별도 스레드에서 실행하여 Tomcat 스레드 차단 방지
        new Thread(() -> {
            log.info(">>> [Startup] KIS 전 종목(KOSPI/KOSDAQ) 동기화를 백그라운드에서 시작합니다.");
            try {
                syncMarket(KOSPI_URL, "KOSPI");
                syncMarket(KOSDAQ_URL, "KOSDAQ");

                // 신규: 필터링 대상 기존 종목 완전 삭제 실행
                cleanupIrrelevantStocks();

                log.info(">>> [Startup] KIS 전 종목 동기화 및 정제 완료!");
            } catch (Exception e) {
                log.error(">>> [Startup] 동기화 중 오류 발생: {}", e.getMessage(), e);
            }
        }).start();
    }

    /* 
     * 매일 오전 8시(시장 개장 전) 정기 업데이트 스케줄러
     * [최적화] 거대 트랜잭션을 방지하기 위해 @Transactional 제거
     */
    @Scheduled(cron = "0 0 8 * * ?")
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
                .collect(Collectors.toMap(Stock::getStockCode, Function.identity(), (existing, replacement) -> existing));

        
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

                    
                    // KIS 마스터 기준: 0~9 단축코드, 9~21 표준코드, 21~61 한글명, 76 시장구분
                    String shortCode = new String(bytes, 0, 9, "MS949").trim();
                    String stockName = new String(bytes, 21, 40, "MS949").trim();
                    
                    // 시장구분 코드 파싱 (인덱스 76)
                    char marketDivCode = ' ';
                    if (bytes.length > 76) {
                        marketDivCode = (char) bytes[76];
                    }

                    // 필터링 대상 확인 (5로 시작하거나 주식 '1'이 아닌 경우 등)
                    if (shouldSkipStock(stockName, shortCode, marketDivCode)) {
                        continue; 
                    }

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
                    
                    // 대량 배치 처리를 위한 저장 (부하 분산을 위해 배치 사이즈 하향 및 지연 시간 추가)
                    if (newStocks.size() >= 200) {
                        stockRepository.saveAll(newStocks);
                        newStocks.clear();
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
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

    private boolean shouldSkipStock(String name, String code, char marketDivCode) {
        if (name == null || name.isEmpty()) return true;
        
        String upperName = name.toUpperCase();

        // 1. 단축코드가 '5'로 시작하는 경우 (일반적으로 수익증권 등)
        if (code != null && code.startsWith("5")) return true;
        
        // 2. 시장구분 코드가 '1'(주식)이 아닌 경우
        if (marketDivCode != ' ' && marketDivCode != '1') return true;

        // 3. 펀드/리츠/수익증권/ETN 클래스 패턴 및 키워드 필터링
        if (upperName.contains("부동산") || 
            upperName.contains("오피스") || 
            upperName.contains("물류") || 
            upperName.contains("하이일드") ||
            upperName.contains("파생") || 
            upperName.contains("CLASS") ||
            upperName.contains("종류") || 
            upperName.contains("공모주") ||
            upperName.contains("인프라") ||
            upperName.contains("핵심성장") ||
            upperName.contains("혁신산업") ||
            upperName.contains("포커스") ||
            upperName.contains("액티브") || 
            upperName.contains("채권") ||
            upperName.contains("그로쓰") ||
            upperName.contains("인덱스") ||
            upperName.contains("밸류")) {
            return true;
        }
                
                
        // 4. 명칭 끝에 붙는 클래스 구분자 ( A, C, Ae, C-I 등) 및 괄호 패턴
        // 괄호 안에 영문이 들어간 명칭이나, 공백 뒤에 영문 클래스가 붙는 경우 제외
        if (upperName.matches(".*[\\(\\s][A-Z,a-z].*")) {
            // (우), (주), (전환) 등은 주식이므로 제외 로직
            if (!upperName.contains("(우)") && !upperName.contains("(주)") && !upperName.contains("(전환)")) {
                return true; 
            }
        }

        // 5. 스팩(SPAC) 및 기타 필터
        return name.contains("스팩") || 
               name.contains("기업인수목적") || 
               name.contains("넥스트웨이브") || 
               name.matches(".*제\\d+호.*");
    }
                
                
    /**
     * DB에 남아있는 불필요 종목들을 완전히 삭제합니다.
     */

    @Transactional
    public void cleanupIrrelevantStocks() {
        log.info(">>> [Cleanup] 불필요 종목(스팩, 코드'5', 비주식 등) 데이터 정리 시작...");
        List<Stock> allStocks = stockRepository.findAll();
        log.info(">>> [Cleanup] 전체 종목 수: {}건", allStocks.size());
        
        List<Stock> toDelete = allStocks.stream()
                .filter(s -> {
                    // marketDivCode를 알 수 없으므로 ' '로 전달 (이름과 코드로만 필터링)
                    boolean skip = shouldSkipStock(s.getStockName(), s.getStockCode(), ' ');
                    if (skip) {
                        log.info(">>> [Cleanup] 삭제 대상 감지: {} ({})", s.getStockName(), s.getStockCode());
                    }
                    return skip;
                })
                .collect(Collectors.toList());

        if (!toDelete.isEmpty()) {
            stockRepository.deleteAllInBatch(toDelete);
            log.info(">>> [Cleanup] 총 {}건의 불필요 종목을 삭제했습니다.", toDelete.size());
        } else {
            log.info(">>> [Cleanup] 삭제할 불필요 종목이 없습니다.");
        }
    }
}

package org.team12.teamproject.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.team12.teamproject.dto.PageResponseDto;
import org.team12.teamproject.dto.StockCandleDto;
import org.team12.teamproject.dto.StockResponseDto;
import org.team12.teamproject.entity.Stock;
import org.team12.teamproject.repository.StockRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import jakarta.annotation.PreDestroy;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.team12.teamproject.event.PriceUpdateEvent;
import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {
    private final ApplicationEventPublisher eventPublisher;
    private final StockRepository stockRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KisWebSocketClient webSocketClient; // 추가

    // KIS API 호출 시 최소 간격 보장 (초당 2건 제한 => 500ms 필요, 안전하게 550ms 설정)
    private long lastApiCallTime = 0;
    private final long MIN_API_INTERVAL_MS = 550;

    private synchronized void enforceApiRateLimit() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastApiCallTime;
        if (elapsed < MIN_API_INTERVAL_MS) {
            try {
                Thread.sleep(MIN_API_INTERVAL_MS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastApiCallTime = System.currentTimeMillis();
    }

    // 우선순위 큐 시스템
    private final PriorityBlockingQueue<StockUpdateTask> updateQueue = new PriorityBlockingQueue<>();
    private final Set<String> enqueuedSymbols = ConcurrentHashMap.newKeySet();
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor();

    private static class StockUpdateTask implements Comparable<StockUpdateTask> {
        final String symbol;
        final int priority; // 1: 최우선(0원/단건조회), 2: 활성(보유/관심), 3: 상위(거래량), 4: 일반(전체)
        final long enqueueTime;

        StockUpdateTask(String symbol, int priority) {
            this.symbol = symbol;
            this.priority = priority;
            this.enqueueTime = System.currentTimeMillis();
        }

        @Override
        public int compareTo(StockUpdateTask o) {
            if (this.priority != o.priority) {
                return Integer.compare(this.priority, o.priority);
            }
            return Long.compare(this.enqueueTime, o.enqueueTime);
        }
    }

    @PostConstruct
    public void startWorker() {
        workerExecutor.submit(() -> {
            log.info(">>> KIS API 통신 전용 백그라운드 워커 스레드 시작 (제한: {}ms)", MIN_API_INTERVAL_MS);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    StockUpdateTask task = updateQueue.take(); // 블로킹 대기
                    enqueuedSymbols.remove(task.symbol);

                    // Rate Limit 적용 후 통신
                    StockResponseDto latest = fetchPriceFromKisApi(task.symbol);
                    // [수정] API 호출 결과가 유효할 때만 저장 및 전파 (0원 오염 방지)
                    if (latest != null && latest.getCurrentPrice() != null && !"0".equals(latest.getCurrentPrice())) {
                        saveToRedis(latest);
                        // DB의 volume 필드도 즉시 동기화 (필터링 및 정렬용)
                        try {
                            long vol = Long.parseLong(latest.getVolume());
                            stockRepository.updateStockVolume(task.symbol, vol);
                        } catch (Exception e) {
                            log.warn("DB 거래량 업데이트 실패 ({}): {}", task.symbol, e.getMessage());
                        }
                        
                        if (task.priority <= 2) {
                            eventPublisher.publishEvent(new PriceUpdateEvent(this, task.symbol, new BigDecimal(latest.getCurrentPrice())));
                        }
                    } else {
                        log.debug("정상적인 시세 데이터를 가져오지 못함 ({}). 기존 데이터를 유지합니다.", task.symbol);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("워커 스레드 실행 중 오류: {}", e.getMessage());
                }
            }
        });
    }

    @PreDestroy
    public void stopWorker() {
        workerExecutor.shutdownNow();
    }

    private void enqueueUpdate(String symbol, int priority) {
        if (!enqueuedSymbols.contains(symbol)) {
            enqueuedSymbols.add(symbol);
            updateQueue.offer(new StockUpdateTask(symbol, priority));
        }
    }

    // API 호출 타임아웃 설정을 포함한 RestTemplate 생성 (429/504 오류 방지)
    private final RestTemplate restTemplate = createRestTemplate();

    private RestTemplate createRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 연결 대기 시간 5초
        factory.setReadTimeout(10000);    // 데이터 읽기 대기 시간 10초
        return new RestTemplate(factory);
    }

    @Value("${kis.api.url}")
    private String apiUrl;

    @Value("${kis.api.app-key}")
    private String appKey;

    @Value("${kis.api.app-secret}")
    private String appSecret;

    private String cachedAccessToken = null;

    /**
     * 더미 데이터 초기 삽입
     */
    @PostConstruct
    public void initDummyData() {
        // KisMasterSyncService를 통해 전 종목을 동기화하므로 더 이상 더미 데이터를 사용하지 않습니다.
        // log.info("더미 데이터 대신 전체 KIS 마스터 파일을 동기화합니다.");
    }

    /**
     * 주식 목록 조회 (페이징)
     */
    public PageResponseDto<StockResponseDto> getStockList(int page, int size, String industry, String stockType) {
        int lower = (page - 1) * size;
        int upper = page * size;
        List<Stock> stockList = stockRepository.findStocksNativeWithFilters(lower, upper, industry, stockType);
        long totalElements = stockRepository.countValidStocksWithFilters(industry, stockType);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        List<StockResponseDto> content = new ArrayList<>();

        try {
            List<String> keys = stockList.stream()
                    .map(s -> "stock:price:" + s.getStockCode())
                    .collect(Collectors.toList());

            List<String> cachedValues = redisTemplate.opsForValue().multiGet(keys);

            for (int i = 0; i < stockList.size(); i++) {
                Stock stock = stockList.get(i);
                String cachedData = (cachedValues != null && i < cachedValues.size()) ? cachedValues.get(i) : null;

                StockResponseDto.StockResponseDtoBuilder builder = StockResponseDto.builder()
                        .symbol(stock.getStockCode())
                        .name(stock.getStockName())
                        .industry(stock.getIndustry());

                if (cachedData != null) {
                    String[] parts = cachedData.split(":");
                    if (parts.length >= 5) {
                        builder.currentPrice(parts[0])
                               .changeAmount(parts[1])
                               .changeRate(parts[2])
                               .volume(parts[3])
                               .basePrice(parts[4]);
                        
                        content.add(builder.build());
                        continue;
                    }
                }

                // [최적화] Redis에 데이터가 없는 경우: DB에 저장된 마지막 시세(Last Known Price)를 반환
                if (stock.getCurrentPrice() != null) {
                    builder.currentPrice(stock.getCurrentPrice().toString())
                           .changeAmount(stock.getChangeAmount() != null ? stock.getChangeAmount().toString() : "0")
                           .changeRate(stock.getChangeRate() != null ? stock.getChangeRate().toString() : "0")
                           .volume(stock.getVolume() != null ? stock.getVolume().toString() : "0")
                           .basePrice(stock.getCurrentPrice().toString()); // Fallback으로 현재가 사용
                } else {
                    // DB에도 없으면 0원 반환
                    builder.currentPrice("0").changeAmount("0").changeRate("0").volume("0").basePrice("0");
                }
                
                StockResponseDto dto = builder.build();
                content.add(dto);

                // [최적화] 데이터가 부실하거나 없는 경우 최우선(P1)으로 업데이트 예약
                if (dto.getCurrentPrice().equals("0")) {
                    enqueueUpdate(stock.getStockCode(), 1);
                } else {
                    // 리스트 노출 종목은 일반 우선순위(P3)로 최신화 유지
                    enqueueUpdate(stock.getStockCode(), 3);
                }
            }
        } catch (Exception e) {
            log.warn("주식 목록 조회 중 오류 (기본 정보로 대체): {}", e.getMessage());
            content = stockList.stream()
                    .map(s -> StockResponseDto.builder()
                            .symbol(s.getStockCode())
                            .name(s.getStockName())
                            .industry(s.getIndustry())
                            .currentPrice("0")
                            .changeAmount("0")
                            .changeRate("0")
                            .volume("0")
                            .basePrice("0")
                            .build())
                    .collect(Collectors.toList());
        }

        return PageResponseDto.<StockResponseDto>builder()
                .content(content)
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements((int) totalElements)
                .build();
    }

    /**
     * 서버 시작 시 전 종목 시세를 한 번 업데이트 (사용자 요청)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncPricesOnStartup() {
        log.info(">>> [Startup] 서버 시작 시 전 종목 시세 초기 동기화 및 활성 종목 실시간 웹소켓 구독을 시작합니다...");
        
        // 활성 종목을 찾아 웹소켓 구독 등재
        List<String> activeCodes = stockRepository.findAllActiveStockCodes();
        for (String code : activeCodes) {
            webSocketClient.subscribe(code);
        }

        // 별도 스레드에서 백그라운드 갱신 큐 밀어넣기
        new Thread(this::updateAllStockPricesToRedis).start();
    }

    /**
     * 검색 기능 추가
     */
    public List<StockResponseDto> searchStocks(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String searchKeyword = keyword.trim().toUpperCase();

        // 사용자 편의를 위한 공통 영문명 - 한글 발음 매핑 (부분 일치 치환)
        searchKeyword = searchKeyword
                .replace("엘지", "LG")
                .replace("에스케이", "SK")
                .replace("케이티", "KT")
                .replace("에이치디", "HD")
                .replace("엔에이치", "NH")
                .replace("씨제이", "CJ")
                .replace("지에스", "GS")
                .replace("엘에스", "LS")
                .replace("엘엑스", "LX")
                .replace("디비", "DB")
                .replace("에이치엘", "HL")
                .replace("케이비", "KB")
                .replace("케이씨씨", "KCC")
                .replace("포스코", "POSCO")
                .replace("제이와이피", "JYP")
                .replace("와이지", "YG")
                .replace("에스엠", "SM");

        // 사용자 편의를 위한 줄임말 및 별명 매핑
        if (searchKeyword.equals("삼전")) {
            searchKeyword = "삼성전자";
        } else if (searchKeyword.equals("삼바")) {
            searchKeyword = "삼성바이오로직스";
        } else if (searchKeyword.equals("현차")) {
            searchKeyword = "현대차";
        } else if (searchKeyword.equals("하닉")) {
            searchKeyword = "SK하이닉스";
        } else if (searchKeyword.equals("하닉스")) {
            searchKeyword = "SK하이닉스";
        } else if (searchKeyword.equals("셀트")) {
            searchKeyword = "셀트리온";
        } else if (searchKeyword.equals("카카")) {
            searchKeyword = "카카오";
        } else if (searchKeyword.equals("에코")) {
            searchKeyword = "에코프로";
        } else if (searchKeyword.equals("에코비")) {
            searchKeyword = "에코프로비엠";
        } else if (searchKeyword.equals("네") || searchKeyword.equals("네이") || searchKeyword.equals("네이버")) {
            searchKeyword = "NAVER";
        }

        List<Stock> searchResults = stockRepository.searchStocks(searchKeyword);
        return searchResults.stream()
                .map(s -> {
                    String cacheKey = "stock:price:" + s.getStockCode();
                    String cachedData = redisTemplate.opsForValue().get(cacheKey);
                    
                    StockResponseDto.StockResponseDtoBuilder builder = StockResponseDto.builder()
                            .symbol(s.getStockCode())
                            .name(s.getStockName())
                            .industry(s.getIndustry());

                    if (cachedData != null) {
                        String[] parts = cachedData.split(":");
                        if (parts.length >= 5) {
                            builder.currentPrice(parts[0])
                                   .changeAmount(parts[1])
                                   .changeRate(parts[2])
                                   .volume(parts[3])
                                   .basePrice(parts[4]);
                        }
                    } else {
                        // Redis 캐시 미스 시 DB 데이터 Fallback
                        if (s.getCurrentPrice() != null) {
                            builder.currentPrice(s.getCurrentPrice().toString())
                                   .changeAmount(s.getChangeAmount() != null ? s.getChangeAmount().toString() : "0")
                                   .changeRate(s.getChangeRate() != null ? s.getChangeRate().toString() : "0")
                                   .volume(s.getVolume() != null ? s.getVolume().toString() : "0")
                                   .basePrice(s.getCurrentPrice().toString());
                        } else {
                            builder.currentPrice("0").changeAmount("0").changeRate("0").volume("0").basePrice("0");
                        }
                        enqueueUpdate(s.getStockCode(), 1); // 검색 결과도 최우선으로 수집 요청
                    }
                    return builder.build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 주식 상세 일괄 조회 (Redis Multi-Get 및 DB 일괄 조회로 최적화)
     */
    public List<StockResponseDto> getStockDetails(List<String> stockCodes) {
        if (stockCodes == null || stockCodes.isEmpty()) return new ArrayList<>();

        // 1. Redis 일괄 조회
        List<String> keys = stockCodes.stream()
                .map(code -> "stock:price:" + code)
                .collect(Collectors.toList());
        List<String> cachedValues = redisTemplate.opsForValue().multiGet(keys);
        
        // 2. DB에서 종목 정보 일괄 조회 (이름 및 백업 시세용)
        List<Stock> stocksInDb = stockRepository.findAllByStockCodeIn(stockCodes);
        Map<String, Stock> stockMap = stocksInDb.stream()
                .collect(Collectors.toMap(Stock::getStockCode, s -> s));

        List<StockResponseDto> results = new ArrayList<>();

        for (int i = 0; i < stockCodes.size(); i++) {
            String code = stockCodes.get(i);
            String cachedData = (cachedValues != null && i < cachedValues.size()) ? cachedValues.get(i) : null;
            
            Stock stock = stockMap.get(code);
            String stockName = (stock != null) ? stock.getStockName() : "이름없음";

            StockResponseDto.StockResponseDtoBuilder builder = StockResponseDto.builder()
                    .symbol(code)
                    .name(stockName);

            if (cachedData != null) {
                String[] parts = cachedData.split(":");
                if (parts.length >= 5) {
                    results.add(builder
                            .currentPrice(parts[0])
                            .changeAmount(parts[1])
                            .changeRate(parts[2])
                            .volume(parts[3])
                            .basePrice(parts[4])
                            .build());
                    continue;
                }
            }
            
            // 3. [최적화] Redis 캐시 부재 시 DB Fallback 적용
            if (stock != null && stock.getCurrentPrice() != null) {
                results.add(builder
                        .currentPrice(stock.getCurrentPrice().toString())
                        .changeAmount(stock.getChangeAmount() != null ? stock.getChangeAmount().toString() : "0")
                        .changeRate(stock.getChangeRate() != null ? stock.getChangeRate().toString() : "0")
                        .volume(stock.getVolume() != null ? stock.getVolume().toString() : "0")
                        .basePrice(stock.getCurrentPrice().toString())
                        .build());
            } else {
                results.add(builder.currentPrice("0").changeAmount("0").changeRate("0").volume("0").basePrice("0").build());
            }

            enqueueUpdate(code, 1);
        }
        return results;
    }

    /**
     * 주식 상세 조회 (가격 포함)
     */
    public StockResponseDto getStockDetail(String stockCode) {
        String cacheKey = "stock:price:" + stockCode;

        try {
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                String[] parts = cachedData.split(":");
                if (parts.length >= 5) {
                    return StockResponseDto.builder()
                            .symbol(stockCode)
                            .name(stockRepository.findByStockCode(stockCode)
                                    .map(Stock::getStockName)
                                    .orElse("이름없음"))
                            .currentPrice(parts[0])
                            .changeAmount(parts[1])
                            .changeRate(parts[2])
                            .volume(parts[3])
                            .basePrice(parts[4])
                            .build();
                } else {
                    log.warn("Redis 캐시 포맷 불일치: {}", cachedData);
                    redisTemplate.delete(cacheKey);
                }
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 파싱 실패 ({}): {}", cacheKey, e.getMessage());
            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception ignore) {}
        }

        // [최적화] Redis 캐시가 없는 경우 DB 최신 가격(백업)을 즉시 반환하여 '시세 확인 중' 방지
        Optional<Stock> stockOpt = stockRepository.findByStockCode(stockCode);
        if (stockOpt.isPresent() && stockOpt.get().getCurrentPrice() != null) {
            Stock s = stockOpt.get();
            // 백그라운드 업데이트 예약만 하고 DB 값을 즉시 반환
            enqueueUpdate(stockCode, 1);
            return StockResponseDto.builder()
                    .symbol(stockCode)
                    .name(s.getStockName())
                    .industry(s.getIndustry())
                    .currentPrice(s.getCurrentPrice().toString())
                    .changeAmount(s.getChangeAmount() != null ? s.getChangeAmount().toString() : "0")
                    .changeRate(s.getChangeRate() != null ? s.getChangeRate().toString() : "0")
                    .volume(s.getVolume() != null ? s.getVolume().toString() : "0")
                    .basePrice(s.getCurrentPrice().toString())
                    .build();
        }

        // DB에도 정보가 없는 완전 초기 상황에서만 API 실시간 호출 진행
        StockResponseDto dto = fetchPriceFromKisApi(stockCode);

        if (dto == null) {
            throw new RuntimeException("종목 데이터를 불러올 수 없습니다.");
        }

        // DB에 잘못 저장된 종목명 교정 로직 (유한양행 vs 한국전력 혼선 방지)
        if ("000100".equals(stockCode) && !"\uC720\uD55C\uC591\uD589".equals(dto.getName())) {
             stockRepository.findByStockCode("000100").ifPresent(s -> {
                 s.updateName("\uC720\uD55C\uC591\uD589"); // 유한양행
                 stockRepository.save(s);
             });
        } else if ("015760".equals(stockCode) && !"\uD55C\uAD6D\uC804\uB825\uACF5\uC0AC".equals(dto.getName())) {
             stockRepository.findByStockCode("015760").ifPresent(s -> {
                 s.updateName("\uD55C\uAD6D\uC804\uB825\uACF5\uC0AC"); // 한국전력공사
                 stockRepository.save(s);
             });
        }

        try {
            String cp = dto.getCurrentPrice();
            String bp = dto.getBasePrice();
            
            // 현재가가 없거나 0인 경우 (장마감 후 등), 기준가(전일 종가)를 fallback으로 사용
            if (cp == null || cp.isEmpty() || "0".equals(cp) || "null".equals(cp)) {
                if (bp != null && !bp.isEmpty() && !"0".equals(bp) && !"null".equals(bp)) {
                    cp = bp;
                    log.info("[StockService] 현재가 부재로 기준가 사용: {} -> {}", stockCode, cp);
                } else {
                    log.warn("[StockService] 현재가와 기준가가 모두 없습니다. 종목: {}", stockCode);
                    // 1000원 대체 로직 제거: cp는 그대로 0 또는 null로 유지되어 주문 서비스에서 차단됨
                }
            }

            String value = String.format("%s:%s:%s:%s:%s",
                    cp != null ? cp : "0",
                    dto.getChangeAmount() != null ? dto.getChangeAmount() : "0",
                    dto.getChangeRate() != null ? dto.getChangeRate() : "0",
                    dto.getVolume() != null ? dto.getVolume() : "0",
                    bp != null && !bp.isEmpty() ? bp : (cp != null ? cp : "0"));

            redisTemplate.opsForValue().set(cacheKey, value, Duration.ofSeconds(60));
            
            // 가격 업데이트 이벤트 발행 (유효한 숫자일 때만)
            if (cp != null && !cp.isEmpty() && !"0".equals(cp) && !"null".equals(cp)) {
                try {
                    eventPublisher.publishEvent(new PriceUpdateEvent(this, stockCode, new BigDecimal(cp)));
                } catch (Exception ex) {
                    log.warn("이벤트 발행을 위한 숫자 변환 실패 ({}): {}", cp, ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Redis 저장 또는 이벤트 발행 실패: {}", e.getMessage());
        }

        return dto;
    }

    /**
     * 주식 캔들 데이터 조회 (기간별 동적 호출 및 캐싱 적용)
     * period: 1D(5분), 1W(일), 1M(일), 6M(월), 1Y(월)
     */
    public List<StockCandleDto> getStockHistory(String symbol, String period) {
        return getStockHistory(symbol, period, false);
    }

    public List<StockCandleDto> getStockHistory(String symbol, String period, boolean forceFetch) {
        String cacheKey = "stock:history:v4:" + symbol + ":" + period;

        if (!forceFetch) {
            try {
                String cachedData = redisTemplate.opsForValue().get(cacheKey);
                if (cachedData != null) {
                    TypeReference<List<StockCandleDto>> typeRef = new TypeReference<>() {
                    };
                    return objectMapper.readValue(cachedData, typeRef);
                }
            } catch (Exception e) {
                log.warn("Redis 캔들 캐시 확인 실패: {}", e.getMessage());
            }
        }

        List<StockCandleDto> resultData = new ArrayList<>();

        try {
            ensureAccessToken();

            LocalDateTime now = LocalDateTime.now();
            String endDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            if ("1D".equals(period)) {
                // 당일 분봉 (최근 약 500개 봉)
                resultData = fetchIntradayHistory(getRawCode(symbol), "0005");
            } else {
                String periodCode = "D";
                String startDate = now.minusMonths(3).format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 기본 3개월

                // 단일 문자 코드는 해당 단위의 전체 데이터를 의미함 (사용자 정의)
                if ("W".equals(period)) {
                    periodCode = "W";
                    startDate = now.minusYears(2).format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 주봉 2년
                } else if ("M".equals(period)) {
                    periodCode = "M";
                    startDate = now.minusYears(5).format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 월봉 5년
                } else if ("D".equals(period)) {
                    periodCode = "D";
                    startDate = now.minusMonths(6).format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 일봉 6개월
                } 
                // 기존 숫자형 코드들은 해당 기간의 '일봉'을 유지 (하위 호환성)
                else if ("1W".equals(period)) {
                    startDate = now.minusDays(7).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                } else if ("1M".equals(period)) {
                    startDate = now.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                } else if ("3M".equals(period)) {
                    startDate = now.minusMonths(3).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                } else if ("6M".equals(period)) {
                    periodCode = "M";
                    startDate = now.minusMonths(6).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                } else if ("1Y".equals(period)) {
                    periodCode = "D"; 
                    startDate = now.minusYears(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                } else {
                    startDate = now.minusDays(30).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                }

                String url = apiUrl + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
                        + "?FID_COND_MRKT_DIV_CODE=J"
                        + "&FID_INPUT_ISCD=" + getRawCode(symbol)
                        + "&FID_INPUT_DATE_1=" + startDate
                        + "&FID_INPUT_DATE_2=" + endDate
                        + "&FID_PERIOD_DIV_CODE=" + periodCode
                        + "&FID_ORG_ADJ_PRC=0";

                HttpHeaders headers = new HttpHeaders();
                headers.set("authorization", "Bearer " + cachedAccessToken);
                headers.set("appkey", appKey);
                headers.set("appsecret", appSecret);
                headers.set("tr_id", "FHKST03010100");

                HttpEntity<String> entity = new HttpEntity<>(headers);
                
                enforceApiRateLimit();
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

                Map<String, Object> body = response.getBody();
                if (body != null && body.containsKey("output2")) {
                    List<Map<String, Object>> output2 = (List<Map<String, Object>>) body.get("output2");
                    if (output2 != null) {
                        resultData = output2.stream().map(data -> StockCandleDto.builder()
                                .date(String.valueOf(data.get("stck_bsop_date")))
                                .open(String.valueOf(data.get("stck_oprc")))
                                .high(String.valueOf(data.get("stck_hgpr")))
                                .low(String.valueOf(data.get("stck_lwpr")))
                                .close(String.valueOf(data.get("stck_clpr")))
                                .volume(String.valueOf(data.get("acml_vol")))
                                .build())
                                .collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception e) {
            log.error("캔들 데이터 API 호출 에러 ({}): {}", period, e.getMessage());
        }

        if (!resultData.isEmpty()) {
            try {
                String jsonData = objectMapper.writeValueAsString(resultData);
                // 스케줄러가 백그라운드에서 1분마다 갱신해주므로 캐시는 여유롭게 10분 유지
                Duration ttl = Duration.ofMinutes(10);
                redisTemplate.opsForValue().set(cacheKey, jsonData, ttl);
            } catch (Exception e) {
                log.warn("Redis 캔들 캐시 저장 실패: {}", e.getMessage());
            }
        }

        return resultData;
    }

    private List<StockCandleDto> fetchIntradayHistory(String symbol, String intervalCode) {
        List<StockCandleDto> allData = new ArrayList<>();
        String nextHour = ""; // 다음 조회를 위한 시작 시간

        try {
            ensureAccessToken();

            // 최대 5회 호출하여 최근 구간들을 대량 확보 (약 500개 봉, 1주일치 분봉 데이터)
            for (int i = 0; i < 5; i++) {
                String url = apiUrl + "/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice"
                        + "?FID_COND_MRKT_DIV_CODE=J"
                        + "&FID_INPUT_ISCD=" + symbol
                        + "&FID_ETC_CLS_CODE="
                        + "&FID_INPUT_HOUR_1=" + nextHour
                        + "&FID_PW_DATA_INCU_YN=N";

                log.info("KIS 분봉 API 요청 ({}회차, symbol: {}): URL={}", i + 1, symbol, url);

                HttpHeaders headers = new HttpHeaders();
                headers.set("authorization", "Bearer " + cachedAccessToken);
                headers.set("appkey", appKey);
                headers.set("appsecret", appSecret);
                headers.set("tr_id", "FHKST03010200");

                HttpEntity<String> entity = new HttpEntity<>(headers);
                
                enforceApiRateLimit();
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

                Map<String, Object> body = response.getBody();
                if (body != null && body.containsKey("output2")) {
                    List<Map<String, Object>> output2 = (List<Map<String, Object>>) body.get("output2");
                    if (output2 != null && !output2.isEmpty()) {
                        List<StockCandleDto> batch = output2.stream().map(data -> StockCandleDto.builder()
                                .date(String.valueOf(data.get("stck_bsop_date")))
                                .time(String.valueOf(data.get("stck_cntg_hour")))
                                .open(String.valueOf(data.get("stck_oprc")))
                                .high(String.valueOf(data.get("stck_hgpr")))
                                .low(String.valueOf(data.get("stck_lwpr")))
                                .close(String.valueOf(data.get("stck_prpr")))
                                .volume(String.valueOf(data.get("cntg_vol")))
                                .build())
                                .collect(Collectors.toList());

                        allData.addAll(batch);

                        // 마지막 데이터의 시간을 다음 조회의 시작 시간으로 설정
                        String lastTime = String.valueOf(output2.get(output2.size() - 1).get("stck_cntg_hour"));
                        
                        // 장 시작 시간(09:00:00)에 도달했거나 데이터가 더 이상 없으면 중단
                        if (lastTime.compareTo("090000") <= 0) {
                            break;
                        }
                        
                        // KIS API 특성상 마지막 데이터 시간을 넘겨주면 그 시간 이전 데이터를 가져옴
                        nextHour = lastTime;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
                
                // TPS 제한 방지를 위해 아주 짧은 대기 추가 가능 (현재는 생략)
            }
            
            // 수집된 전체 데이터 반환 (SortedData에서 reverse하므로 그대로 반환)
            return allData;

        } catch (Exception e) {
            log.error("분봉 데이터 전수 조회 에러 (symbol: {}): {}", symbol, e.getMessage());
        }

        return allData;
    }

    // 하위 호환용
    public List<StockCandleDto> getStockHistory(String symbol) {
        return getStockHistory(symbol, "1M");
    }

    /**
     * 활성 종목(보유주, 관심주) 시세를 Redis에 우선 동기화
     * KIS API 초당 2건 제한 준수를 위해 1000ms 간격으로 호출
     */
    @Scheduled(fixedDelay = 60000) // 1분마다 실행 (이전 작업 종료 후 1분 뒤)
    public void updateActiveStockPrices() {
        log.info(">>> [Scheduled] 활성 종목(보유/관심) 시세 동기화 시작...");
        List<String> activeCodes = stockRepository.findAllActiveStockCodes();
        
        if (activeCodes.isEmpty()) {
            log.info(">>> [Scheduled] 활성 종목이 없습니다.");
            return;
        }

        for (String symbol : activeCodes) {
            webSocketClient.subscribe(symbol); // 실시간 구독이 풀렸을 수 있으니 재요청
            enqueueUpdate(symbol, 2); // 활성 종목은 High Priority(P2)
        }
        log.info(">>> [Scheduled] 활성 종목 시세 동기화 완료 (총 {}건)", activeCodes.size());
    }

    /**
     * 전체 종목 시세를 순차적으로 갱신 (사용자 요청으로 주기를 1시간에서 30분으로 단축)
     */
    @Scheduled(fixedDelay = 3600000) // 1시간마다 전체 갱신 (부하 방지를 위해 주기 연장)
    public void updateAllStockPricesToRedis() {
        log.info(">>> [Scheduled] 전체 종목 시세 백그라운드 갱신 시작 (1000ms 간격)...");
        List<Stock> allStocks = stockRepository.findAll();

        for (Stock stock : allStocks) {
            enqueueUpdate(stock.getStockCode(), 4); // 전체 종목은 Low Priority(P4)
        }
        log.info(">>> [Scheduled] 전체 종목 시세 백그라운드 갱신 완료");
    }

    private void saveToRedis(StockResponseDto dto) {
        String cacheKey = "stock:price:" + dto.getSymbol();
        String value = String.format("%s:%s:%s:%s:%s",
                dto.getCurrentPrice(),
                dto.getChangeAmount(),
                dto.getChangeRate(),
                dto.getVolume(),
                dto.getBasePrice());
        try {
            // [최적화] 중요도에 따른 캐시 TTL 차등 적용
            Duration ttl = Duration.ofMinutes(30); 
            redisTemplate.opsForValue().set(cacheKey, value, ttl);
        } catch (Exception e) {
            log.warn("Redis 시세 저장 실패 ({}): {}", dto.getSymbol(), e.getMessage());
        }
        
        // [중요] DB의 시세 정보도 비동기적으로 업데이트하여 Fallback 시스템 유지
        try {
            BigDecimal price = new BigDecimal(dto.getCurrentPrice().replace(",", ""));
            BigDecimal rate = new BigDecimal(dto.getChangeRate().replace(",", ""));
            BigDecimal amt = new BigDecimal(dto.getChangeAmount().replace(",", ""));
            long vol = Long.parseLong(dto.getVolume());
            stockRepository.updateStockPriceInfo(dto.getSymbol(), price, rate, amt, vol);
        } catch (Exception e) {
            log.warn("DB 시세 업데이트 실패 ({}): {}", dto.getSymbol(), e.getMessage());
        }
    }

    private void ensureAccessToken() {
        if (cachedAccessToken == null) {
            String redisKey = "kis:access_token:" + appKey;
            try {
                String token = redisTemplate.opsForValue().get(redisKey);
                if (token != null) {
                    this.cachedAccessToken = token;
                    log.info("Redis에서 기존 KIS 토큰 로드 완료 (AppKey: {}...)",
                            appKey.substring(0, Math.min(5, appKey.length())));
                    return;
                }
            } catch (Exception e) {
                log.warn("Redis 토큰 조회 실패: {}", e.getMessage());
            }

            issueAccessToken();
        }
    }

    private String getRawCode(String symbol) {
        if (symbol == null) return null;
        if (symbol.startsWith("K") || symbol.startsWith("Q")) {
            return symbol.substring(1);
        }
        return symbol;
    }

    /**
     * KIS API 호출
     */
    private StockResponseDto fetchPriceFromKisApi(String stockCode) {
        String rawCode = getRawCode(stockCode);
        String marketDiv = "J"; 
        try {
            ensureAccessToken();

            String url = apiUrl + "/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=" + marketDiv + "&FID_INPUT_ISCD=" + rawCode;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization", "Bearer " + cachedAccessToken);
            headers.set("appkey", appKey);
            headers.set("appsecret", appSecret);
            headers.set("tr_id", "FHKST01010100");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            enforceApiRateLimit();
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            
            Map<String, Object> body = response.getBody();
            if (body != null && "0".equals(String.valueOf(body.get("rt_cd")))) {
                Map<String, Object> output = (Map<String, Object>) body.get("output");
                if (output != null) {
                    String currentPrice = (String) output.get("stck_prpr");
                    
                    if (currentPrice != null) {
                        String stockName = stockRepository.findByStockCode(stockCode)
                                .map(Stock::getStockName)
                                .orElse("\uC774\uB984\uC5C6\uC74C");

                        return StockResponseDto.builder()
                                .symbol(stockCode)
                                .name(stockName)
                                .currentPrice(currentPrice)
                                .changeAmount((String) output.get("prdy_vrss"))
                                .changeRate((String) output.get("prdy_ctrt"))
                                .volume((String) output.get("acml_vol"))
                                .basePrice((String) output.get("stck_sdpr"))
                                .build();
                    }
                }
            } else if (body != null) {
                log.warn("KIS API 시세 조회 실패 ({}): {} - {}", stockCode, body.get("rt_cd"), body.get("msg1"));
            }
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            log.warn("KIS 토큰 만료(401). 재발급 후 재시도.");
            clearTokenCache();
            return fetchPriceFromKisApi(stockCode);
        } catch (Exception e) {
            log.error("KIS API 호출 에러 ({}): {}", stockCode, e.getMessage());
        }

        return null; // 실패 시 null 반환하여 기존 데이터 보호
    }

    /**
     * KIS 토큰 강제 갱신
     */
    public void refreshToken() {
        log.info("사용자 요청에 의한 KIS 토큰 강제 갱신 시작...");
        clearTokenCache();
        issueAccessToken();
    }

    private void clearTokenCache() {
        this.cachedAccessToken = null;
        try {
            redisTemplate.delete("kis:access_token:" + appKey);
        } catch (Exception e) {
            log.warn("Redis 토큰 삭제 실패: {}", e.getMessage());
        }
    }

    private void issueAccessToken() {
        String tokenUrl = apiUrl + "/oauth2/tokenP";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("grant_type", "client_credentials");
        bodyMap.put("appkey", appKey);
        bodyMap.put("appsecret", appSecret);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(bodyMap, headers);
        
        enforceApiRateLimit();
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                });

        Map<String, Object> resBody = response.getBody();
        if (resBody != null) {
            String token = (String) resBody.get("access_token");
            this.cachedAccessToken = token;

            String redisKey = "kis:access_token:" + appKey;
            try {
                redisTemplate.opsForValue().set(redisKey, token, Duration.ofHours(23));
                log.info("새 KIS 액세스 토큰 발급 및 Redis 저장 완료 (AppKey: {}...)",
                        appKey.substring(0, Math.min(5, appKey.length())));
            } catch (Exception e) {
                log.warn("Redis에 토큰 저장 실패: {}", e.getMessage());
            }
        }
    }
    public List<String> getAllIndustries() {
        return stockRepository.findAllIndustries();
    }

    @org.springframework.transaction.annotation.Transactional
    public Stock getOrRegisterStock(String stockCode) {
        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseGet(() -> {
                    log.info("DB에 없는 주식 조회/주문 시도. DB에 동적 강제 등록합니다. (코드: {})", stockCode);
                    Stock newStock = Stock.builder()
                            .stockCode(stockCode)
                            .stockName("동적등록종목_" + stockCode)
                            .marketType("KOSPI") // UNKNOWN 대신 KOSPI로 기본 설정하여 필터링 우회
                            .isActive(true)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return stockRepository.saveAndFlush(newStock);
                });
        
        // 만약 기존에 UNKNOWN으로 등록되어 있었다면 KOSPI로 업데이트 (필터링 방지)
        if ("UNKNOWN".equals(stock.getMarketType())) {
             stock.updateMarketType("KOSPI");
             return stockRepository.saveAndFlush(stock);
        }
        return stock;
    }
}
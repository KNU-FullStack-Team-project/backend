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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final StockRepository stockRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    public PageResponseDto<StockResponseDto> getStockList(int page, int size) {
        int lower = (page - 1) * size;
        int upper = page * size;

        List<Stock> stockList = stockRepository.findStocksNative(lower, upper);
        long totalElements = stockRepository.count();
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
                        .name(stock.getStockName());

                if (cachedData != null) {
                    String[] parts = cachedData.split(":");
                    if (parts.length >= 4) {
                        content.add(builder
                                .currentPrice(parts[0])
                                .changeAmount(parts[1])
                                .changeRate(parts[2])
                                .volume(parts[3])
                                .build());
                        continue;
                    }
                }

                content.add(getStockDetail(stock.getStockCode()));
            }
        } catch (Exception e) {
            log.warn("Redis 벌크 조회 실패, 개별 조회로 대체: {}", e.getMessage());
            content = stockList.stream()
                    .map(stock -> getStockDetail(stock.getStockCode()))
                    .toList();
        }

        return PageResponseDto.<StockResponseDto>builder()
                .content(content)
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements((int) totalElements)
                .build();
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
                if (parts.length >= 4) {
                    return StockResponseDto.builder()
                            .symbol(stockCode)
                            .name(stockRepository.findByStockCode(stockCode)
                                    .map(Stock::getStockName)
                                    .orElse("이름없음"))
                            .currentPrice(parts[0])
                            .changeAmount(parts[1])
                            .changeRate(parts[2])
                            .volume(parts[3])
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
            } catch (Exception ignore) {
            }
        }

        StockResponseDto dto = fetchPriceFromKisApi(stockCode);

        if (dto == null) {
            throw new RuntimeException("종목 데이터를 불러올 수 없습니다.");
        }

        try {
            String value = String.format("%s:%s:%s:%s",
                    dto.getCurrentPrice(),
                    dto.getChangeAmount(),
                    dto.getChangeRate(),
                    dto.getVolume());

            redisTemplate.opsForValue().set(cacheKey, value, Duration.ofSeconds(60));
        } catch (Exception e) {
            log.warn("Redis 저장 실패: {}", e.getMessage());
        }

        return dto;
    }

    /**
     * 주식 캔들 데이터 조회 (기간별 동적 호출 및 캐싱 적용)
     * period: 1D(5분), 1W(일), 1M(일), 6M(월), 1Y(월)
     */
    public List<StockCandleDto> getStockHistory(String symbol, String period) {
        String cacheKey = "stock:history:" + symbol + ":" + period;

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

        List<StockCandleDto> resultData = new ArrayList<>();

        try {
            ensureAccessToken();

            LocalDateTime now = LocalDateTime.now();
            String endDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            if ("1D".equals(period)) {
                resultData = fetchIntradayHistory(symbol, "0005");
            } else {
                String periodCode = "D";
                String startDate;

                if ("1W".equals(period)) {
                    startDate = now.minusDays(7).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                } else if ("1M".equals(period)) {
                    startDate = now.minusDays(31).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                } else if ("6M".equals(period)) {
                    periodCode = "M";
                    startDate = now.minusMonths(6).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                } else if ("1Y".equals(period)) {
                    periodCode = "M";
                    startDate = now.minusYears(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                } else {
                    startDate = now.minusDays(30).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                }

                String url = apiUrl + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
                        + "?FID_COND_MRKT_DIV_CODE=J"
                        + "&FID_INPUT_ISCD=" + symbol
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
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

                Map<String, Object> body = response.getBody();
                if (body != null && body.containsKey("output2")) {
                    List<Map<String, Object>> output2 = (List<Map<String, Object>>) body.get("output2");
                    if (output2 != null) {
                        resultData = output2.stream().map(data -> StockCandleDto.builder()
                                .date((String) data.get("stck_bsop_date"))
                                .open((String) data.get("stck_oprc"))
                                .high((String) data.get("stck_hgpr"))
                                .low((String) data.get("stck_lwpr"))
                                .close((String) data.get("stck_clpr"))
                                .volume((String) data.get("acml_vol"))
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
                Duration ttl = "1D".equals(period) ? Duration.ofMinutes(1) : Duration.ofMinutes(10);
                redisTemplate.opsForValue().set(cacheKey, jsonData, ttl);
            } catch (Exception e) {
                log.warn("Redis 캔들 캐시 저장 실패: {}", e.getMessage());
            }
        }

        return resultData;
    }

    private List<StockCandleDto> fetchIntradayHistory(String symbol, String intervalCode) {
        try {
            ensureAccessToken();

            String url = apiUrl + "/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice"
                    + "?FID_COND_MRKT_DIV_CODE=J"
                    + "&FID_INPUT_ISCD=" + symbol
                    + "&FID_ETC_CLS_CODE="
                    + "&FID_INPUT_HOUR_1="
                    + "&FID_PW_DATA_INCU_YN=N";

            log.info("KIS 분봉 API 요청 URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("authorization", "Bearer " + cachedAccessToken);
            headers.set("appkey", appKey);
            headers.set("appsecret", appSecret);
            headers.set("tr_id", "FHKST03010200");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            Map<String, Object> body = response.getBody();
            if (body != null) {
                log.info("KIS 분봉 API 상세 응답 (symbol: {}): rt_cd={}, msg_cd={}, msg1={}",
                        symbol, body.get("rt_cd"), body.get("msg_cd"), body.get("msg1"));
                // 전체 바디를 알고 싶으면 아래 주석 해제 (로그가 너무 길어질 수 있음)
                // log.info("전체 응답 바디: {}", objectMapper.writeValueAsString(body));
            } else {
                log.warn("KIS 분봉 API 응답 빈 본문 (symbol: {})", symbol);
            }

            if (body != null && body.containsKey("output2")) {
                List<Map<String, Object>> output2 = (List<Map<String, Object>>) body.get("output2");
                if (output2 != null) {
                    log.info("수신된 분봉 데이터 개수: {} (symbol: {})", output2.size(), symbol);
                    return output2.stream().map(data -> StockCandleDto.builder()
                            .date(String.valueOf(data.get("stck_bsop_date")))
                            .time(String.valueOf(data.get("stck_cntg_hour")))
                            .open(String.valueOf(data.get("stck_oprc")))
                            .high(String.valueOf(data.get("stck_hgpr")))
                            .low(String.valueOf(data.get("stck_lwpr")))
                            .close(String.valueOf(data.get("stck_prpr")))
                            .volume(String.valueOf(data.get("cntg_vol")))
                            .build())
                            .collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            log.error("분봉 데이터 조회 에러: {}", e.getMessage());
        }

        return new ArrayList<>();
    }

    // 하위 호환용
    public List<StockCandleDto> getStockHistory(String symbol) {
        return getStockHistory(symbol, "1M");
    }

    /**
     * 1분마다 전 종목 시세를 Redis에 동기화
     */
    @Scheduled(fixedRate = 60000)
    public void updateAllStockPricesToRedis() {
        log.info(">>> [Scheduled] 전 종목 시세 Redis 동기화 시작...");
        List<Stock> allStocks = stockRepository.findAll();

        for (Stock stock : allStocks) {
            try {
                String symbol = stock.getStockCode();
                StockResponseDto latest = fetchPriceFromKisApi(symbol);

                if (latest != null) {
                    String cacheKey = "stock:price:" + symbol;
                    String value = latest.getCurrentPrice() + ":"
                            + latest.getChangeAmount() + ":"
                            + latest.getChangeRate() + ":"
                            + latest.getVolume();

                    redisTemplate.opsForValue().set(cacheKey, value, Duration.ofMinutes(10));
                }

                Thread.sleep(100);
            } catch (Exception e) {
                log.warn("주식({}) 시세 동기화 중 오류: {}", stock.getStockCode(), e.getMessage());
            }
        }

        log.info(">>> [Scheduled] 전 종목 시세 Redis 동기화 완료 (총 {}건)", allStocks.size());
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

    /**
     * KIS API 호출
     */
    private StockResponseDto fetchPriceFromKisApi(String stockCode) {
        try {
            ensureAccessToken();

            String url = apiUrl
                    + "/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD="
                    + stockCode;
            log.info("KIS API 요청 URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization", "Bearer " + cachedAccessToken);
            headers.set("appkey", appKey);
            headers.set("appsecret", appSecret);
            headers.set("tr_id", "FHKST01010100");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            log.info("KIS API 응답: {}", response.getBody());

            if (response.getBody() != null && response.getBody().containsKey("output")) {
                Map<String, Object> output = (Map<String, Object>) response.getBody().get("output");

                String stockName = stockRepository.findByStockCode(stockCode)
                        .map(Stock::getStockName)
                        .orElse("이름없음");

                return StockResponseDto.builder()
                        .symbol(stockCode)
                        .name(stockName)
                        .currentPrice((String) output.get("stck_prpr"))
                        .changeAmount((String) output.get("prdy_vrss"))
                        .changeRate((String) output.get("prdy_ctrt"))
                        .volume((String) output.get("acml_vol"))
                        .build();
            } else {
                log.warn("KIS API 응답에 output 데이터가 없습니다: {}", response.getBody());
            }

            return null;

        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            log.warn("KIS 토큰 만료 또는 무효(401). 토큰 재발급 후 재시도합니다.");
            clearTokenCache();
            return fetchPriceFromKisApi(stockCode);
        } catch (Exception e) {
            log.error("API 호출 에러: {}, 상세: {}", e.getMessage(), e.toString());
            return null;
        }
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
}
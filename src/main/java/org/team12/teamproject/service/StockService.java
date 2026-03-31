package org.team12.teamproject.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final RedisTemplate<String, String> redisTemplate;
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
        if (stockRepository.count() == 0) {
            String[] codes = {
                    "005930", "삼성전자", "KOSPI",
                    "000660", "SK하이닉스", "KOSPI",
                    "373220", "LG에너지솔루션", "KOSPI",
                    "207940", "삼성바이오로직스", "KOSPI",
                    "005380", "현대차", "KOSPI",
                    "000270", "기아", "KOSPI",
                    "068270", "셀트리온", "KOSPI",
                    "005490", "POSCO홀딩스", "KOSPI",
                    "035420", "NAVER", "KOSPI",
                    "051910", "LG화학", "KOSPI",
                    "028260", "삼성물산", "KOSPI",
                    "035720", "카카오", "KOSPI",
                    "006400", "삼성SDI", "KOSPI",
                    "105560", "KB금융", "KOSPI",
                    "055550", "신한지주", "KOSPI",
                    "012330", "현대모비스", "KOSPI",
                    "003670", "포스코퓨처엠", "KOSPI",
                    "086790", "하나금융지주", "KOSPI",
                    "323410", "카카오뱅크", "KOSPI",
                    "259960", "크래프톤", "KOSPI",
                    "377300", "카카오페이", "KOSPI",
                    "011200", "HMM", "KOSPI",
                    "015760", "한국전력", "KOSPI",
                    "018260", "삼성SDS", "KOSPI",
                    "003490", "대한항공", "KOSPI",
                    "034020", "두산에너빌리티", "KOSPI",
                    "010130", "고려아연", "KOSPI",
                    "009150", "삼성전기", "KOSPI",
                    "032830", "삼성생명", "KOSPI",
                    "316140", "우리금융지주", "KOSPI",
                    "010950", "S-Oil", "KOSPI",
                    "000810", "삼성화재", "KOSPI",
                    "033920", "무학", "KOSPI",
                    "011170", "롯데케미칼", "KOSPI",
                    "024110", "기업은행", "KOSPI",
                    "051900", "LG생활건강", "KOSPI",
                    "138040", "메리츠금융지주", "KOSPI",
                    "036570", "엔씨소프트", "KOSPI",
                    "090430", "아모레퍼시픽", "KOSPI",
                    "010140", "삼성중공업", "KOSPI",
                    "028050", "삼성엔지니어링", "KOSPI",
                    "086280", "현대글로비스", "KOSPI",
                    "021240", "코웨이", "KOSPI",
                    "022100", "포스코DX", "KOSDAQ",
                    "247540", "에코프로비엠", "KOSDAQ",
                    "086520", "에코프로", "KOSDAQ",
                    "066970", "엘앤에프", "KOSDAQ",
                    "028300", "HLB", "KOSDAQ",
                    "196170", "알테오젠", "KOSDAQ",
                    "293490", "카카오게임즈", "KOSDAQ",
                    "058470", "리노공업", "KOSDAQ",
                    "278280", "천보", "KOSDAQ",
                    "039030", "이오테크닉스", "KOSDAQ",
                    "041510", "에스엠", "KOSDAQ",
                    "035900", "JYP Ent.", "KOSDAQ",
                    "122870", "와이지엔터테인먼트", "KOSDAQ",
                    "036490", "SK머티리얼즈", "KOSDAQ",
                    "214150", "클래시스", "KOSDAQ",
                    "000250", "삼천당제약", "KOSDAQ",
                    "034230", "파라다이스", "KOSDAQ"
            };

            for (int i = 0; i < codes.length; i += 3) {
                stockRepository.save(
                        Stock.builder()
                                .stockCode(codes[i])
                                .stockName(codes[i + 1])
                                .marketType(codes[i + 2])
                                .isActive(true)
                                .createdAt(LocalDateTime.now())
                                .build()
                );
            }

            log.info("주식 더미 데이터 삽입 완료");
        }
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
                TypeReference<List<StockCandleDto>> typeRef = new TypeReference<>() {};
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
                    + "&FID_PW_DATA_IN_YN=N"
                    + "&FID_INPUT_HOUR_1=";

            log.info("KIS 분봉 API 요청 URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("authorization", "Bearer " + cachedAccessToken);
            headers.set("appkey", appKey);
            headers.set("appsecret", appSecret);
            headers.set("tr_id", "FHKST03010200");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            Map<String, Object> body = response.getBody();
            String bodyStr = objectMapper.writeValueAsString(body);
            log.info("KIS 분봉 API 전체 응답: {}", bodyStr);

            if (body != null && body.containsKey("output2")) {
                List<Map<String, Object>> output2 = (List<Map<String, Object>>) body.get("output2");
                if (output2 != null) {
                    return output2.stream().map(data -> StockCandleDto.builder()
                            .date((String) data.get("stck_bsop_date"))
                            .open((String) data.get("stck_oprc"))
                            .high((String) data.get("stck_hgpr"))
                            .low((String) data.get("stck_lwpr"))
                            .close((String) data.get("stck_prpr"))
                            .volume((String) data.get("cntg_vol"))
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
                    log.info("Redis에서 기존 KIS 토큰 로드 완료 (AppKey: {}...)", appKey.substring(0, Math.min(5, appKey.length())));
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

            String url = apiUrl + "/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=" + stockCode;
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
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
        );

        Map<String, Object> resBody = response.getBody();
        if (resBody != null) {
            String token = (String) resBody.get("access_token");
            this.cachedAccessToken = token;

            String redisKey = "kis:access_token:" + appKey;
            try {
                redisTemplate.opsForValue().set(redisKey, token, Duration.ofHours(23));
                log.info("새 KIS 액세스 토큰 발급 및 Redis 저장 완료 (AppKey: {}...)", appKey.substring(0, Math.min(5, appKey.length())));
            } catch (Exception e) {
                log.warn("Redis에 토큰 저장 실패: {}", e.getMessage());
            }
        }
    }
}
package org.team12.teamproject.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.team12.teamproject.dto.PageResponseDto;
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
import org.team12.teamproject.dto.StockCandleDto;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final StockRepository stockRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${kis.api.url}")
    private String apiUrl;
    @Value("${kis.api.app-key}")
    private String appKey;
    @Value("${kis.api.app-secret}")
    private String appSecret;

    private String cachedAccessToken = null;

    @PostConstruct
    public void initDummyData() {
        if (stockRepository.count() == 0) {
            String[] codes = {
                "005930", "삼성전자", "KOSPI", "000660", "SK하이닉스", "KOSPI", "373220", "LG에너지솔루션", "KOSPI",
                "207940", "삼성바이오로직스", "KOSPI", "005380", "현대차", "KOSPI", "000270", "기아", "KOSPI",
                "068270", "셀트리온", "KOSPI", "005490", "POSCO홀딩스", "KOSPI", "035420", "NAVER", "KOSPI",
                "051910", "LG화학", "KOSPI", "028260", "삼성물산", "KOSPI", "035720", "카카오", "KOSPI",
                "006400", "삼성SDI", "KOSPI", "105560", "KB금융", "KOSPI", "055550", "신한지주", "KOSPI",
                "012330", "현대모비스", "KOSPI", "003670", "포스코퓨처엠", "KOSPI", "086790", "하나금융지주", "KOSPI",
                "323410", "카카오뱅크", "KOSPI", "259960", "크래프톤", "KOSPI", "377300", "카카오페이", "KOSPI",
                "011200", "HMM", "KOSPI", "015760", "한국전력", "KOSPI", "018260", "삼성SDS", "KOSPI",
                "003490", "대한항공", "KOSPI", "034020", "두산에너빌리티", "KOSPI", "010130", "고려아연", "KOSPI",
                "009150", "삼성전기", "KOSPI", "032830", "삼성생명", "KOSPI", "316140", "우리금융지주", "KOSPI",
                "010950", "S-Oil", "KOSPI", "000810", "삼성화재", "KOSPI", "033920", "무학", "KOSPI",
                "011170", "롯데케미칼", "KOSPI", "024110", "기업은행", "KOSPI", "051900", "LG생활건강", "KOSPI",
                "138040", "메리츠금융지주", "KOSPI", "036570", "엔씨소프트", "KOSPI", "090430", "아모레퍼시픽", "KOSPI",
                "010140", "삼성중공업", "KOSPI", "028050", "삼성엔지니어링", "KOSPI", "086280", "현대글로비스", "KOSPI",
                "021240", "코웨이", "KOSPI", "022100", "포스코DX", "KOSDAQ", "247540", "에코프로비엠", "KOSDAQ",
                "086520", "에코프로", "KOSDAQ", "066970", "엘앤에프", "KOSDAQ", "028300", "HLB", "KOSDAQ",
                "196170", "알테오젠", "KOSDAQ", "293490", "카카오게임즈", "KOSDAQ", "058470", "리노공업", "KOSDAQ",
                "278280", "천보", "KOSDAQ", "039030", "이오테크닉스", "KOSDAQ", "041510", "에스엠", "KOSDAQ",
                "035900", "JYP Ent.", "KOSDAQ", "122870", "와이지엔터테인먼트", "KOSDAQ", "036490", "SK머티리얼즈", "KOSDAQ",
                "214150", "클래시스", "KOSDAQ", "000250", "삼천당제약", "KOSDAQ", "034230", "파라다이스", "KOSDAQ"
            };
            
            for (int i=0; i < codes.length; i+=3) {
                stockRepository.save(Stock.builder()
                        .stockCode(codes[i])
                        .stockName(codes[i+1])
                        .marketType(codes[i+2])
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
            log.info("오라클 DB에 한국 주요 주식 60개가 자동 삽입되었습니다!");
        }
    }

    public PageResponseDto<StockResponseDto> getStockList(int page, int size) {
        int lower = (page - 1) * size;
        int upper = page * size;
        List<Stock> stockList = stockRepository.findStocksNative(lower, upper);
        long totalElements = stockRepository.count();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        List<StockResponseDto> content = stockList.stream().map(stock -> {
            return getStockDetail(stock.getStockCode());
        }).toList();

        return PageResponseDto.<StockResponseDto>builder()
                .content(content)
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements((int) totalElements)
                .build();
    }

    public StockResponseDto getStockDetail(String symbol) {
        String cacheKey = "stock:price:" + symbol;
        try {
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                String[] parts = cachedData.split(":");
                return StockResponseDto.builder()
                        .symbol(symbol)
                        .name(stockRepository.findByStockCode(symbol).map(Stock::getStockName).orElse("이름없음"))
                        .currentPrice(parts[0])
                        .changeAmount(parts[1])
                        .changeRate(parts[2])
                        .volume(parts[3])
                        .build();
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 확인 실패 (서버 미구동 등): {}", e.getMessage());
        }

        StockResponseDto dto = fetchPriceFromKisApi(symbol);

        if (dto == null) {
            throw new RuntimeException("종목 데이터를 불러올 수 없습니다.");
        }

        try {
            String valueToCache = String.format("%s:%s:%s:%s", 
                    dto.getCurrentPrice(), dto.getChangeAmount(), dto.getChangeRate(), dto.getVolume());
            redisTemplate.opsForValue().set(cacheKey, valueToCache, Duration.ofSeconds(60));
        } catch (Exception e) {
            log.warn("Redis 캐시 저장 실패 (서버 미구동 등): {}", e.getMessage());
        }

        return dto;
    }

    /**
     * 주식 캔들 데이터(일봉) 조회
     */
    public List<StockCandleDto> getStockHistory(String symbol) {
        try {
            ensureAccessToken();

            LocalDateTime now = LocalDateTime.now();
            String endDate = now.format(DateTimeFormatter.ofPattern("YYYYMMDD"));
            String startDate = now.minusDays(30).format(DateTimeFormatter.ofPattern("YYYYMMDD"));

            String url = apiUrl + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
                    + "?FID_COND_MRKT_DIV_CODE=J"
                    + "&FID_INPUT_ISCD=" + symbol
                    + "&FID_INPUT_DATE_1=" + startDate
                    + "&FID_INPUT_DATE_2=" + endDate
                    + "&FID_PERIOD_DIV_CODE=D"
                    + "&FID_ORG_ADJ_PRC=0";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization", "Bearer " + cachedAccessToken);
            headers.set("appkey", appKey);
            headers.set("appsecret", appSecret);
            headers.set("tr_id", "FHKST03010100"); // 국내주식 기간별 시세(일/주/월)

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("output2")) {
                List<Map<String, Object>> output2 = (List<Map<String, Object>>) response.getBody().get("output2");
                return output2.stream().map(data -> StockCandleDto.builder()
                        .date((String) data.get("stck_bsop_date"))
                        .open((String) data.get("stck_oprc"))
                        .high((String) data.get("stck_hgpr"))
                        .low((String) data.get("stck_lwpr"))
                        .close((String) data.get("stck_clpr"))
                        .volume((String) data.get("acml_vol"))
                        .build())
                        .collect(Collectors.toList());
            }
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            log.warn("KIS 토큰 만료 또는 무효(401). 캔들 데이터 조회 재시도합니다.");
            clearTokenCache();
            return getStockHistory(symbol); // 1회 재시도
        } catch (Exception e) {
            log.error("캔들 데이터 조회 에러: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    private void ensureAccessToken() {
        if (cachedAccessToken == null) {
            // 1. Redis에서 토큰 확인 (AppKey별로 별도 관리하여 개발자간 간섭 방지)
            String redisKey = "kis:access_token:" + appKey;
            try {
                String token = redisTemplate.opsForValue().get(redisKey);
                if (token != null) {
                    this.cachedAccessToken = token;
                    log.info("Redis에서 기존 KIS 토큰 로드 완료 (AppKey: {}...)", appKey.substring(0, 5));
                    return;
                }
            } catch (Exception e) {
                log.warn("Redis 토큰 조회 실패: {}", e.getMessage());
            }

            // 2. 없으면 새로 발급
            issueAccessToken();
        }
    }

    private StockResponseDto fetchPriceFromKisApi(String symbol) {
        try {
            ensureAccessToken();

            String url = apiUrl + "/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=" + symbol;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization", "Bearer " + cachedAccessToken);
            headers.set("appkey", appKey);
            headers.set("appsecret", appSecret);
            headers.set("tr_id", "FHKST01010100");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("output")) {
                Map<String, Object> output = (Map<String, Object>) response.getBody().get("output");
                
                String stockName = stockRepository.findByStockCode(symbol)
                        .map(Stock::getStockName)
                        .orElse("이름없음");

                return StockResponseDto.builder()
                        .symbol(symbol)
                        .name(stockName)
                        .currentPrice((String) output.get("stck_prpr"))
                        .changeAmount((String) output.get("prdy_vrss"))
                        .changeRate((String) output.get("prdy_ctrt"))
                        .volume((String) output.get("acml_vol"))
                        .build();
            }
            return null;
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            log.warn("KIS 토큰 만료 또는 무효(401). 토큰 재발급 후 재시도합니다.");
            clearTokenCache();
            return fetchPriceFromKisApi(symbol); // 1회 재시도
        } catch (Exception e) {
            log.error("API 호출 에러: {}", e.getMessage());
            return null;
        }
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

        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", appKey);
        body.put("appsecret", appSecret);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        if (response.getBody() != null) {
            String token = (String) response.getBody().get("access_token");
            this.cachedAccessToken = token;
            
            String redisKey = "kis:access_token:" + appKey;
            try {
                redisTemplate.opsForValue().set(redisKey, token, Duration.ofHours(23));
                log.info("새 KIS 액세스 토큰 발급 및 Redis 저장 완료 (AppKey: {}...)", appKey.substring(0, 5));
            } catch (Exception e) {
                log.warn("Redis에 토큰 저장 실패: {}", e.getMessage());
            }
        }
    }
}
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 더미 데이터 초기 삽입
     */
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

            for (int i = 0; i < codes.length; i += 3) {
                stockRepository.save(Stock.builder()
                        .stockCode(codes[i])
                        .stockName(codes[i + 1])
                        .marketType(codes[i + 2])
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .build());
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

        List<StockResponseDto> content = stockList.stream()
                .map(stock -> getStockDetail(stock.getStockCode()))
                .toList();

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

                return StockResponseDto.builder()
                        .symbol(stockCode) // DTO는 symbol 유지
                        .name(stockRepository.findByStockCode(stockCode)
                                .map(Stock::getStockName)
                                .orElse("이름없음"))
                        .currentPrice(parts[0])
                        .changeAmount(parts[1])
                        .changeRate(parts[2])
                        .volume(parts[3])
                        .build();
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 실패: {}", e.getMessage());
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
     * KIS API 호출
     */
    private StockResponseDto fetchPriceFromKisApi(String stockCode) {
        try {
            if (cachedAccessToken == null) {
                issueAccessToken();
            }

            String url = apiUrl +
                    "/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD="
                    + stockCode;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization", "Bearer " + cachedAccessToken);
            headers.set("appkey", appKey);
            headers.set("appsecret", appSecret);
            headers.set("tr_id", "FHKST01010100");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("output")) {
                Map<String, Object> output =
                        (Map<String, Object>) response.getBody().get("output");

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
            }

            return null;

        } catch (Exception e) {
            log.error("API 호출 에러: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Access Token 발급
     */
    private void issueAccessToken() {
        String tokenUrl = apiUrl + "/oauth2/tokenP";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", appKey);
        body.put("appsecret", appSecret);

        HttpEntity<Map<String, String>> request =
                new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(tokenUrl, request, Map.class);

        if (response.getBody() != null) {
            this.cachedAccessToken =
                    (String) response.getBody().get("access_token");
        }
    }
}
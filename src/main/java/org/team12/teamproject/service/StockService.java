package org.team12.teamproject.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.team12.teamproject.dto.PageResponseDto;
import org.team12.teamproject.dto.StockResponseDto;
import org.team12.teamproject.entity.Stock;
import org.team12.teamproject.repository.StockRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final StockRepository stockRepository; // DB와 통신할 레포지토리

    @Value("${kis.api.url}")
    private String apiUrl;
    @Value("${kis.api.app-key}")
    private String appKey;
    @Value("${kis.api.app-secret}")
    private String appSecret;

    private String cachedAccessToken = null;

    // [테스트 편의용] 서버가 켜질 때 DB가 비어있으면 주식 3개를 자동으로 넣어줍니다.
    @PostConstruct
    public void initDummyData() {
        if (stockRepository.count() == 0) {
            stockRepository.save(Stock.builder().symbol("005930").name("삼성전자").build());
            stockRepository.save(Stock.builder().symbol("000660").name("SK하이닉스").build());
            stockRepository.save(Stock.builder().symbol("035420").name("NAVER").build());
            log.info("오라클 DB에 테스트용 주식 3개가 자동 삽입되었습니다!");
        }
    }

    // =====================================================================
    // [1] 리스트 조회 (페이징 적용)
    // =====================================================================
    public PageResponseDto<StockResponseDto> getStockList(int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Stock> stockPage = stockRepository.findAll(pageable);

        List<StockResponseDto> content = stockPage.getContent().stream().map(stock -> {
            String price = fetchPriceFromKisApi(stock.getSymbol());
            
            return StockResponseDto.builder()
                    .symbol(stock.getSymbol())
                    .name(stock.getName())
                    .currentPrice(price != null ? price : "조회실패")
                    .build();
        }).toList();

        return PageResponseDto.<StockResponseDto>builder()
                .content(content)
                .currentPage(page)
                .totalPages(stockPage.getTotalPages())
                .totalElements((int) stockPage.getTotalElements())
                .build();
    }

    // =====================================================================
    // [2] 단일 종목 상세 조회
    // =====================================================================
    public StockResponseDto getStockDetail(String symbol) {
        // DB에서 종목 이름 꺼내오기 (없으면 "이름없음" 처리)
        String stockName = stockRepository.findById(symbol)
                .map(Stock::getName)
                .orElse("이름없음");

        String fetchedPrice = fetchPriceFromKisApi(symbol);

        if (fetchedPrice == null) {
            throw new RuntimeException("종목 데이터를 불러올 수 없습니다.");
        }

        return StockResponseDto.builder()
                .symbol(symbol)
                .name(stockName)
                .currentPrice(fetchedPrice)
                .build();
    }

    // =====================================================================
    // [3] KIS API 현재가 조회 로직
    // =====================================================================
    private String fetchPriceFromKisApi(String symbol) {
        try {
            if (cachedAccessToken == null) {
                issueAccessToken();
            }

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
                return (String) output.get("stck_prpr");
            }
            return null;
        } catch (Exception e) {
            log.error("API 호출 에러: {}", e.getMessage());
            return null;
        }
    }

    // =====================================================================
    // [4] KIS API 토큰 발급 로직
    // =====================================================================
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
            this.cachedAccessToken = (String) response.getBody().get("access_token");
        }
    }
}
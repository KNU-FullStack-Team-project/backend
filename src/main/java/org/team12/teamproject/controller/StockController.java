package org.team12.teamproject.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.PageResponseDto;
import org.team12.teamproject.dto.StockResponseDto;
import org.team12.teamproject.service.StockService;
import lombok.RequiredArgsConstructor;
import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174",
        "http://localhost:3000"
})
public class StockController {

    private final StockService stockService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final org.team12.teamproject.service.TechnicalIndicatorService indicatorService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    // =========================================================
    // 1. 리스트 페이징 조회 (이 부분이 추가되었습니다!)
    // =========================================================
    @GetMapping
    public ResponseEntity<PageResponseDto<StockResponseDto>> getStockList(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "industry", required = false) String industry,
            @RequestParam(name = "stockType", required = false) String stockType
    ) {
        PageResponseDto<StockResponseDto> response = stockService.getStockList(page, size, industry, stockType);
        return ResponseEntity.ok(response);
    }

    // =========================================================
    // 2. 단일 종목 상세 조회 (기존에 있던 부분)
    // =========================================================
    @GetMapping("/{symbol}")
    public ResponseEntity<StockResponseDto> getStockDetail(@PathVariable(name = "symbol") String symbol) {
        StockResponseDto response = stockService.getStockDetail(symbol);
        return ResponseEntity.ok(response);
    }

    // =========================================================
    // 3. 종목별 캔들 데이터 조회 (추가됨)
    // =========================================================
    @GetMapping("/{symbol}/history")
    public ResponseEntity<?> getStockHistory(
            @PathVariable(name = "symbol") String symbol,
            @RequestParam(name = "period", defaultValue = "1M") String period
    ) {
        // [최적화] 브라우저 수준에서 1분간 해당 요청을 캐시하도록 설정
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "max-age=60")
                .body(stockService.getStockHistory(symbol, period));
    }

    // =========================================================
    // 4. 종목 진단 조회 (추가됨)
    // =========================================================
    @GetMapping("/{symbol}/diagnosis")
    public ResponseEntity<?> getStockDiagnosis(@PathVariable(name = "symbol") String symbol) {
        try {
            String cacheKey = "stock:diagnosis:" + symbol;
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedData != null) {
                return ResponseEntity.ok(objectMapper.readValue(cachedData, org.team12.teamproject.dto.StockDiagnosisDto.class));
            }
            
            // 캐시 없으면 실시간 계산
            java.util.List<org.team12.teamproject.dto.StockCandleDto> candles = stockService.getStockHistory(symbol, "D");
            if (candles == null || candles.isEmpty()) {
                return ResponseEntity.ok(org.team12.teamproject.dto.StockDiagnosisDto.builder().symbol(symbol).signal("INSUFFICIENT_DATA").build());
            }
            
            org.team12.teamproject.dto.StockDiagnosisDto diagnosis = indicatorService.diagnose(symbol, candles);
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(diagnosis), java.time.Duration.ofMinutes(10));
            return ResponseEntity.ok(diagnosis);
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "진단 중 오류 발생: " + e.getMessage()));
        }
    }

    // =========================================================
    // 5. 종목 검색 (추가됨)
    // =========================================================
    @GetMapping("/search")
    public ResponseEntity<?> searchStocks(@RequestParam(name = "keyword") String keyword) {
        return ResponseEntity.ok(stockService.searchStocks(keyword));
    }

    @GetMapping("/industries")
    public ResponseEntity<List<String>> getAllIndustries() {
        return ResponseEntity.ok(stockService.getAllIndustries());
    }

    // =========================================================
    // 5. KIS 토큰 강제 갱신 (임시)
    // =========================================================
    @GetMapping("/refresh-token")
    public ResponseEntity<String> refreshToken() {
        stockService.refreshToken();
        return ResponseEntity.ok("KIS Access Token refreshed and saved to Redis.");
    }

}

package org.team12.teamproject.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.PageResponseDto;
import org.team12.teamproject.dto.StockResponseDto;
import org.team12.teamproject.service.StockService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class StockController {

    private final StockService stockService;

    // =========================================================
    // 1. 리스트 페이징 조회 (이 부분이 추가되었습니다!)
    // =========================================================
    @GetMapping
    public ResponseEntity<PageResponseDto<StockResponseDto>> getStockList(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        PageResponseDto<StockResponseDto> response = stockService.getStockList(page, size);
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
        return ResponseEntity.ok(stockService.getStockHistory(symbol, period));
    }

    // =========================================================
    // 4. KIS 토큰 강제 갱신 (임시)
    // =========================================================
    @GetMapping("/refresh-token")
    public ResponseEntity<String> refreshToken() {
        stockService.refreshToken();
        return ResponseEntity.ok("KIS Access Token refreshed and saved to Redis.");
    }
}
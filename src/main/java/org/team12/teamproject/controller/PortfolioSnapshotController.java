package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.entity.PortfolioSnapshot;
import org.team12.teamproject.service.PortfolioSnapshotService;

import java.util.List;
import java.util.stream.Collectors;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioSnapshotController {

    private final PortfolioSnapshotService snapshotService;

    /**
     * 특정 계좌의 자산 변동 히스토리 조회
     */
    @GetMapping("/snapshots/{accountId}")
    public ResponseEntity<List<SnapshotResponseDto>> getHistory(@PathVariable Long accountId) {
        List<PortfolioSnapshot> history = snapshotService.getSnapshotHistory(accountId);
        
        List<SnapshotResponseDto> response = history.stream()
                .map(s -> SnapshotResponseDto.builder()
                        .date(s.getSnapshotDate().toString())
                        .totalAsset(s.getTotalAsset())
                        .cashBalance(s.getCashBalance())
                        .stockValue(s.getStockValue())
                        .build())
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    @lombok.Getter
    @lombok.Builder
    public static class SnapshotResponseDto {
        private String date;
        private BigDecimal totalAsset;
        private BigDecimal cashBalance;
        private BigDecimal stockValue;
    }
}

package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.entity.PriceAlert;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.entity.Stock;
import org.team12.teamproject.repository.UserRepository;
import org.team12.teamproject.repository.StockRepository;
import org.team12.teamproject.service.PriceAlertService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/price-alerts")
@RequiredArgsConstructor
public class PriceAlertController {

    private final PriceAlertService priceAlertService;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;

    /**
     * 목표가 알림 설정
     */
    @PostMapping("/{userId}")
    public ResponseEntity<?> createAlert(@PathVariable Long userId, @RequestBody Map<String, Object> request) {
        Long stockId = Long.parseLong(request.get("stockId").toString());
        BigDecimal targetPrice = new BigDecimal(request.get("targetPrice").toString());
        PriceAlert.AlertDirection direction = PriceAlert.AlertDirection.valueOf(request.get("direction").toString());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found"));

        PriceAlert alert = PriceAlert.builder()
                .user(user)
                .stock(stock)
                .targetPrice(targetPrice)
                .direction(direction)
                .build();

        return ResponseEntity.ok(priceAlertService.createPriceAlert(alert));
    }

    /**
     * 내 활성 알림 목록 조회
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<PriceAlert>> getActiveAlerts(@PathVariable Long userId) {
        return ResponseEntity.ok(priceAlertService.getActiveAlerts(userId));
    }

    /**
     * 알림 삭제
     */
    @DeleteMapping("/{alertId}/{userId}")
    public ResponseEntity<Void> deleteAlert(@PathVariable Long alertId, @PathVariable Long userId) {
        priceAlertService.deleteAlert(alertId, userId);
        return ResponseEntity.ok().build();
    }
}

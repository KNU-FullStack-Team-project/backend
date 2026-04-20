package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.OrderRequestDto;
import org.team12.teamproject.dto.OrderResponseDto;
import org.team12.teamproject.dto.StockResponseDto;
import org.team12.teamproject.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174",
        "http://localhost:3000"
})
public class OrderController {

    private final OrderService orderService;
    
    @PostMapping
    public ResponseEntity<String> createOrder(@RequestBody OrderRequestDto req) {
        try {
            if ("BUY".equalsIgnoreCase(req.getOrderSide())) {
                if ("MARKET".equalsIgnoreCase(req.getOrderType())) {
                    orderService.placeMarketBuyOrder(req.getAccountId(), req.getStockCode(), req.getQuantity(), req.getRequestId());
                } else {
                    orderService.placeLimitBuyOrder(req.getAccountId(), req.getStockCode(), req.getQuantity(),
                            req.getPrice(), req.getRequestId());
                }
            } else {
                // SELL인 경우
                if ("MARKET".equalsIgnoreCase(req.getOrderType())) {
                    orderService.placeMarketSellOrder(req.getAccountId(), req.getStockCode(), req.getQuantity(), req.getRequestId());
                } else {
                    orderService.placeLimitSellOrder(req.getAccountId(), req.getStockCode(), req.getQuantity(),
                            req.getPrice(), req.getRequestId());
                }
            }
            return ResponseEntity.ok("주문이 성공적으로 접수되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage() != null ? e.getMessage() : "시스템 오류가 발생했습니다.");
        }
    }

    @GetMapping
    public ResponseEntity<List<OrderResponseDto>> getOrders(@RequestParam(name = "accountId") Long accountId) {
        return ResponseEntity.ok(orderService.getOrdersByAccountId(accountId));
    }

    @GetMapping("/holdings")
    public ResponseEntity<List<StockResponseDto>> getHeldStocks(@RequestParam(name = "accountId") Long accountId) {
        return ResponseEntity.ok(orderService.getHeldStocksByAccountId(accountId));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<String> cancelOrder(@PathVariable(name = "orderId") Long orderId,
            @RequestParam(name = "accountId") Long accountId) {
        orderService.cancelOrder(orderId, accountId);
        return ResponseEntity.ok("주문이 취소되었습니다.");
    }
}
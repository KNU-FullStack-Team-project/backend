package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.dto.OrderRequestDto;
import org.team12.teamproject.entity.Order;
import org.team12.teamproject.service.OrderService;


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
    public ResponseEntity<?> createOrder(@RequestBody OrderRequestDto req) {
        try {
            Order order;
            if ("BUY".equalsIgnoreCase(req.getOrderSide())) {
                if ("MARKET".equalsIgnoreCase(req.getOrderType())) {
                    order = orderService.placeMarketBuyOrder(req.getAccountId(), req.getStockCode(), req.getQuantity(), req.getRequestId());
                } else {
                    order = orderService.placeLimitBuyOrder(req.getAccountId(), req.getStockCode(), req.getQuantity(),
                            req.getPrice(), req.getRequestId());
                }
            } else {
                // SELL인 경우
                if ("MARKET".equalsIgnoreCase(req.getOrderType())) {
                    order = orderService.placeMarketSellOrder(req.getAccountId(), req.getStockCode(), req.getQuantity(), req.getRequestId());
                } else {
                    order = orderService.placeLimitSellOrder(req.getAccountId(), req.getStockCode(), req.getQuantity(),
                            req.getPrice(), req.getRequestId());
                }
            }
            return ResponseEntity.ok().body("주문이 성공적으로 접수되었습니다.");
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 사용자의 주문 내역 조회
     */
    @GetMapping
    public ResponseEntity<?> getOrders(@RequestParam(name = "accountId") Long accountId) {
        try {
            return ResponseEntity.ok().body(orderService.getOrdersByAccountId(accountId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to fetch orders: " + e.getMessage());
        }
    }

    /**
     * 주문 취소 처리
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable(name = "orderId") Long orderId,
            @RequestParam(name = "accountId") Long accountId) {
        try {
            orderService.cancelOrder(orderId, accountId);
            return ResponseEntity.ok("Order canceled successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Cancel failed: " + e.getMessage());
        }
    }
}

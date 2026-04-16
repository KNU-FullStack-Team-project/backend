package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
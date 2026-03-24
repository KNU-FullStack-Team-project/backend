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
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody OrderRequestDto req) {
        try {
            Order order;
            if ("BUY".equalsIgnoreCase(req.getOrderSide())) {
                if ("MARKET".equalsIgnoreCase(req.getOrderType())) {
                    order = orderService.placeMarketBuyOrder(req.getAccountId(), req.getStockCode(), req.getQuantity());
                } else {
                    order = orderService.placeLimitBuyOrder(req.getAccountId(), req.getStockCode(), req.getQuantity(), req.getPrice());
                }
            } else {
                // SELL인 경우 (현재 MARKET SELL만 구현됨)
                order = orderService.placeMarketSellOrder(req.getAccountId(), req.getStockCode(), req.getQuantity());
            }
            return ResponseEntity.ok().body("Order placed successfully. Status: " + order.getOrderStatus());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Order failed: " + e.getMessage());
        }
    }
}

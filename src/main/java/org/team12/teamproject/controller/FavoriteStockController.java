package org.team12.teamproject.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.team12.teamproject.service.FavoriteStockService;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
public class FavoriteStockController {

    private final FavoriteStockService favoriteStockService;

    @GetMapping
    public ResponseEntity<List<String>> getFavorites(@RequestParam(name = "userId") Long userId) {
        return ResponseEntity.ok(favoriteStockService.getFavoriteSymbols(userId));
    }

    @GetMapping("/details")
    public ResponseEntity<?> getFavoriteDetails(@RequestParam(name = "userId") Long userId) {
        try {
            return ResponseEntity.ok(favoriteStockService.getFavoriteDetails(userId));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/{symbol}")
    public ResponseEntity<?> addFavorite(
            @RequestParam(name = "userId") Long userId,
            @PathVariable(name = "symbol") String symbol,
            @RequestParam(name = "buyAlertLevel", required = false, defaultValue = "NONE") String buyAlertLevel,
            @RequestParam(name = "sellAlertLevel", required = false, defaultValue = "NONE") String sellAlertLevel) {
        try {
            favoriteStockService.addFavorite(userId, symbol, buyAlertLevel, sellAlertLevel);
            return ResponseEntity.ok("Added to favorites");
        } catch (Exception e) {
            System.err.println("Error adding favorite: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @PutMapping("/{symbol}/alert")
    public ResponseEntity<?> updateAlertLevel(
            @RequestParam(name = "userId") Long userId,
            @PathVariable(name = "symbol") String symbol,
            @RequestParam(name = "buyAlertLevel", required = false, defaultValue = "NONE") String buyAlertLevel,
            @RequestParam(name = "sellAlertLevel", required = false, defaultValue = "NONE") String sellAlertLevel) {
        try {
            favoriteStockService.updateFavoriteAlertLevel(userId, symbol, buyAlertLevel, sellAlertLevel);
            return ResponseEntity.ok("Updated alert level");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<?> removeFavorite(
            @RequestParam(name = "userId") Long userId,
            @PathVariable(name = "symbol") String symbol
    ) {
        try {
            favoriteStockService.removeFavorite(userId, symbol);
            return ResponseEntity.ok("Removed from favorites");
        } catch (Exception e) {
            System.err.println("Error removing favorite: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }

    }
}

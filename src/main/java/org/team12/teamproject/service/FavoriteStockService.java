package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.repository.FavoriteStockRepository;

import java.util.List;

import org.team12.teamproject.dto.StockResponseDto;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteStockService {

    private final FavoriteStockRepository favoriteStockRepository;
    private final StockService stockService;

    public List<String> getFavoriteSymbols(Long userId) {
        return favoriteStockRepository.findSymbolsByUserId(userId);
    }

    public List<StockResponseDto> getFavoriteDetails(Long userId) {
        List<String> symbols = getFavoriteSymbols(userId);
        return symbols.stream()
                .map(symbol -> {
                    try {
                        return stockService.getStockDetail(symbol);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Transactional
    public void addFavorite(Long userId, String symbol, String buyAlertLevel, String sellAlertLevel) {
        if (favoriteStockRepository.countByUserAndSymbol(userId, symbol) == 0) {
            String finalBuy = (buyAlertLevel != null && !buyAlertLevel.isEmpty()) ? buyAlertLevel : "NONE";
            String finalSell = (sellAlertLevel != null && !sellAlertLevel.isEmpty()) ? sellAlertLevel : "NONE";
            favoriteStockRepository.addFavoriteNative(userId, symbol, finalBuy, finalSell);
        }
    }

    @Transactional
    public void updateFavoriteAlertLevel(Long userId, String symbol, String buyAlertLevel, String sellAlertLevel) {
        favoriteStockRepository.updateFavoriteAlertLevelNative(userId, symbol, buyAlertLevel, sellAlertLevel);
    }

    @Transactional
    public void removeFavorite(Long userId, String symbol) {
        favoriteStockRepository.removeFavoriteNative(userId, symbol);
    }
}

package org.team12.teamproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.team12.teamproject.entity.FavoriteStock;
import org.team12.teamproject.entity.User;
import org.team12.teamproject.repository.FavoriteStockRepository;
import org.team12.teamproject.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteStockService {

    private final FavoriteStockRepository favoriteStockRepository;
    private final UserRepository userRepository;

    public List<String> getFavoriteSymbols(Long userId) {
        return favoriteStockRepository.findSymbolsByUserId(userId);
    }

    @Transactional
    public void addFavorite(Long userId, String symbol) {
        if (favoriteStockRepository.countByUserAndSymbol(userId, symbol) == 0) {
            favoriteStockRepository.addFavoriteNative(userId, symbol);
        }
    }

    @Transactional
    public void removeFavorite(Long userId, String symbol) {
        favoriteStockRepository.removeFavoriteNative(userId, symbol);
    }
}

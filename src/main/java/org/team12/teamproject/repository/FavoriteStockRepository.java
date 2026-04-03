package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.FavoriteStock;
import org.team12.teamproject.entity.User;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.team12.teamproject.entity.FavoriteStock;
import java.util.List;

public interface FavoriteStockRepository extends JpaRepository<FavoriteStock, Long> {
    
    @Query(value = "SELECT stock_symbol FROM favorite_stocks WHERE user_id = :userId", nativeQuery = true)
    List<String> findSymbolsByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT count(*) FROM favorite_stocks WHERE user_id = :userId AND stock_symbol = :symbol", nativeQuery = true)
    int countByUserAndSymbol(@Param("userId") Long userId, @Param("symbol") String symbol);

    @Modifying
    @Query(value = "INSERT INTO favorite_stocks (id, user_id, stock_symbol, created_at) VALUES (favorite_stock_seq.NEXTVAL, :userId, :symbol, CURRENT_TIMESTAMP)", nativeQuery = true)
    void addFavoriteNative(@Param("userId") Long userId, @Param("symbol") String symbol);

    @Modifying
    @Query(value = "DELETE FROM favorite_stocks WHERE user_id = :userId AND stock_symbol = :symbol", nativeQuery = true)
    void removeFavoriteNative(@Param("userId") Long userId, @Param("symbol") String symbol);
}

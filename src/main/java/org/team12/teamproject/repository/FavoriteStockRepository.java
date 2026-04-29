package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.FavoriteStock;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteStockRepository extends JpaRepository<FavoriteStock, Long> {
    
    @Query(value = "SELECT s.stock_code FROM favorite_stocks fs JOIN stock s ON fs.stock_id = s.stock_id WHERE fs.user_id = :userId", nativeQuery = true)
    List<String> findSymbolsByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT count(*) FROM favorite_stocks fs JOIN stock s ON fs.stock_id = s.stock_id WHERE fs.user_id = :userId AND s.stock_code = :symbol", nativeQuery = true)
    int countByUserAndSymbol(@Param("userId") Long userId, @Param("symbol") String symbol);

    @Modifying
    @Query(value = "INSERT INTO favorite_stocks (id, user_id, stock_id, created_at, alert_level, sell_alert_level) " +
                   "SELECT favorite_stock_seq.NEXTVAL, :userId, s.stock_id, CURRENT_TIMESTAMP, :buyAlertLevel, :sellAlertLevel " +
                   "FROM stock s WHERE s.stock_code = :symbol", nativeQuery = true)
    void addFavoriteNative(@Param("userId") Long userId, @Param("symbol") String symbol, 
                           @Param("buyAlertLevel") String buyAlertLevel, @Param("sellAlertLevel") String sellAlertLevel);

    @Modifying
    @Query(value = "DELETE FROM favorite_stocks WHERE user_id = :userId AND stock_id = (SELECT stock_id FROM stock WHERE stock_code = :symbol)", nativeQuery = true)
    void removeFavoriteNative(@Param("userId") Long userId, @Param("symbol") String symbol);

    @Query("SELECT DISTINCT s.stockCode FROM FavoriteStock f JOIN f.stock s")
    List<String> findAllFavoriteSymbols();

    @Query("SELECT f FROM FavoriteStock f JOIN FETCH f.user WHERE f.stock.stockCode = :symbol AND (f.buyAlertLevel != 'NONE' OR f.sellAlertLevel != 'NONE')")
    List<FavoriteStock> findAlertCandidatesBySymbol(@Param("symbol") String symbol);

    @Modifying
    @Query(value = "UPDATE favorite_stocks SET alert_level = :buyAlertLevel, sell_alert_level = :sellAlertLevel " +
                   "WHERE user_id = :userId AND stock_id = (SELECT stock_id FROM stock WHERE stock_code = :symbol)", nativeQuery = true)
    void updateFavoriteAlertLevelNative(@Param("userId") Long userId, @Param("symbol") String symbol, 
                                        @Param("buyAlertLevel") String buyAlertLevel, @Param("sellAlertLevel") String sellAlertLevel);
}

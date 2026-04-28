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
    @Query(value = "INSERT INTO favorite_stocks (id, user_id, stock_id, created_at) " +
                   "SELECT favorite_stock_seq.NEXTVAL, :userId, s.stock_id, CURRENT_TIMESTAMP " +
                   "FROM stock s WHERE s.stock_code = :symbol", nativeQuery = true)
    void addFavoriteNative(@Param("userId") Long userId, @Param("symbol") String symbol);

    @Modifying
    @Query(value = "DELETE FROM favorite_stocks WHERE user_id = :userId AND stock_id = (SELECT stock_id FROM stock WHERE stock_code = :symbol)", nativeQuery = true)
    void removeFavoriteNative(@Param("userId") Long userId, @Param("symbol") String symbol);
}

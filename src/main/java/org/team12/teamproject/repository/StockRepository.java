package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.Stock;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByStockCode(String stockCode);
}
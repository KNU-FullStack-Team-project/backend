package org.team12.teamproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.team12.teamproject.entity.Stock;


public interface StockRepository extends JpaRepository<Stock, String> {
    
}
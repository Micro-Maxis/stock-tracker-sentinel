package com.portfolio.stocktrackersentinel.repository;

import com.portfolio.stocktrackersentinel.entity.StockHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {
    List<StockHistory> findAllByOrderByRemovedAtDesc();
}
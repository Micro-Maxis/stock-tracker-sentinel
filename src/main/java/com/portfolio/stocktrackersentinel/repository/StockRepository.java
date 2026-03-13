package com.portfolio.stocktrackersentinel.repository;

import com.portfolio.stocktrackersentinel.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    List<Stock> findBySymbol(String symbol);
    Optional<Stock> findBySymbolIgnoreCase(String symbol);
    boolean existsBySymbolIgnoreCase(String symbol);

    // Top performers
    @Query("SELECT s FROM Stock s ORDER BY (s.currentPrice - s.buyPrice) / s.buyPrice DESC")
    List<Stock> findTopPerformers();

    // Underperformers
    @Query("SELECT s FROM Stock s WHERE s.currentPrice < s.buyPrice ORDER BY (s.currentPrice - s.buyPrice) / s.buyPrice ASC")
    List<Stock> findUnderperformers();

    // Total value grouped by currency
    @Query(value = "SELECT currency, SUM(current_price * quantity) as total, SUM(buy_price * quantity) as cost FROM stocks GROUP BY currency", nativeQuery = true)
    List<Object[]> getTotalValueByCurrency();

    // Win/loss count
    @Query("SELECT COUNT(s) FROM Stock s WHERE s.currentPrice >= s.buyPrice")
    long countProfitableStocks();

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.currentPrice < s.buyPrice")
    long countLosingStocks();

    // Call stored procedure
    @Query(value = "SELECT * FROM get_portfolio_summary_by_currency(:currency)", nativeQuery = true)
    List<Object[]> getPortfolioSummaryByCurrency(@Param("currency") String currency);
}
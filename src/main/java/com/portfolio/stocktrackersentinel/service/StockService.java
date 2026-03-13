package com.portfolio.stocktrackersentinel.service;

import com.portfolio.stocktrackersentinel.dto.PortfolioSummaryDTO;
import com.portfolio.stocktrackersentinel.entity.Stock;
import com.portfolio.stocktrackersentinel.entity.StockHistory;
import com.portfolio.stocktrackersentinel.repository.StockHistoryRepository;
import com.portfolio.stocktrackersentinel.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final CurrencyService currencyService;

    // ── CREATE (with average cost merge) ─────────────────
    public Stock addStock(@NonNull Stock stock) {
        if (stock.getSymbol() == null || stock.getSymbol().isBlank())
            throw new IllegalArgumentException("Symbol is required");
        if (stock.getBuyPrice() == null || stock.getBuyPrice().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Buy price must be greater than 0");
        if (stock.getQuantity() == null || stock.getQuantity() <= 0)
            throw new IllegalArgumentException("Quantity must be greater than 0");
        String symbol = stock.getSymbol().toUpperCase().trim();
        stock.setSymbol(symbol);
        stock.setCurrency(currencyService.detectCurrency(symbol));
        stock.setExchange(currencyService.detectExchange(symbol));

        Optional<Stock> existing = stockRepository.findBySymbolIgnoreCase(symbol);

        if (existing.isPresent()) {
            Stock current = existing.get();
            log.debug("Symbol {} already exists — merging with average cost basis", symbol);

            BigDecimal oldQty   = BigDecimal.valueOf(current.getQuantity());
            BigDecimal newQty   = BigDecimal.valueOf(stock.getQuantity());
            BigDecimal totalQty = oldQty.add(newQty);

            // Average cost = ((old_qty x old_price) + (new_qty x new_price)) / total_qty
            BigDecimal avgPrice = (oldQty.multiply(current.getBuyPrice())
                    .add(newQty.multiply(stock.getBuyPrice())))
                    .divide(totalQty, 4, RoundingMode.HALF_UP);

            current.setQuantity(totalQty.doubleValue());
            current.setBuyPrice(avgPrice);
            current.setCurrentPrice(stock.getCurrentPrice());

            log.debug("Merged {} | New avg: {} | Total qty: {}", symbol, avgPrice, totalQty);
            return stockRepository.save(current);
        }

        log.debug("Adding new position {} | Exchange: {} | Currency: {}",
                symbol, stock.getExchange(), stock.getCurrency());
        return stockRepository.save(stock);
    }

    // ── READ ALL ─────────────────────────────────────────
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    // ── READ ONE ─────────────────────────────────────────
    public Stock getStockById(Long id) {
        return stockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock not found: " + id));
    }

    // ── UPDATE ───────────────────────────────────────────
    public Stock updateStock(Long id, Stock updated) {
        Stock existing = getStockById(id);
        log.debug("Updating stock: {}", existing.getSymbol());
        existing.setBuyPrice(updated.getBuyPrice());
        existing.setCurrentPrice(updated.getCurrentPrice());
        existing.setQuantity(updated.getQuantity());
        existing.setCompanyName(updated.getCompanyName());
        existing.setCurrency(currencyService.detectCurrency(existing.getSymbol()));
        existing.setExchange(currencyService.detectExchange(existing.getSymbol()));
        return stockRepository.save(existing);
    }

    // ── DELETE -> moves to history ────────────────────────
    public void deleteStock(Long id) {
        Stock stock = getStockById(id);
        log.debug("Archiving stock: {}", stock.getSymbol());

        StockHistory history = new StockHistory();
        history.setSymbol(stock.getSymbol());
        history.setCompanyName(stock.getCompanyName());
        history.setBuyPrice(stock.getBuyPrice());
        history.setQuantity(stock.getQuantity());
        history.setCurrentPrice(stock.getCurrentPrice());
        history.setCurrency(stock.getCurrency());
        history.setExchange(stock.getExchange());
        history.setCreatedAt(stock.getCreatedAt());
        history.setRemovedAt(LocalDateTime.now());
        history.setFinalGainLoss(calculateGainLoss(stock));

        stockHistoryRepository.save(history);
        stockRepository.deleteById(id);
    }

    // ── HISTORY ──────────────────────────────────────────
    public List<StockHistory> getHistory() {
        return stockHistoryRepository.findAllByOrderByRemovedAtDesc();
    }

    // ── BUSINESS LOGIC ───────────────────────────────────
    public BigDecimal calculateGainLoss(Stock stock) {
        return stock.getCurrentPrice()
                .subtract(stock.getBuyPrice())
                .multiply(BigDecimal.valueOf(stock.getQuantity()));
    }

    // ── PORTFOLIO SUMMARY ─────────────────────────────────
    public PortfolioSummaryDTO getPortfolioSummary() {
        List<Stock> stocks = stockRepository.findAll();
        PortfolioSummaryDTO summary = new PortfolioSummaryDTO();
        summary.setUsdToMyrRate(currencyService.getUsdToMyrRate());

        for (Stock s : stocks) {
            BigDecimal value    = s.getCurrentPrice()
                    .multiply(BigDecimal.valueOf(s.getQuantity()));
            BigDecimal gainLoss = calculateGainLoss(s);
            String currency     = s.getCurrency() != null ? s.getCurrency() : "USD";

            if ("USD".equals(currency)) {
                summary.setUsdAssets(summary.getUsdAssets().add(value));
                summary.setUsdGainLoss(summary.getUsdGainLoss().add(gainLoss));
                summary.setUsdHoldings(summary.getUsdHoldings() + 1);
            } else {
                summary.setMyrAssets(summary.getMyrAssets().add(value));
                summary.setMyrGainLoss(summary.getMyrGainLoss().add(gainLoss));
                summary.setMyrHoldings(summary.getMyrHoldings() + 1);
            }

            summary.setTotalValueMyr(summary.getTotalValueMyr()
                    .add(currencyService.toMyr(value, currency)));
            summary.setTotalGainLoss(summary.getTotalGainLoss()
                    .add(currencyService.toMyr(gainLoss, currency)));
        }
        return summary;
    }

    // ── ANALYTICS ────────────────────────────────────────
    public Map<String, Object> getAnalytics() {
        Map<String, Object> result = new HashMap<>();

        List<Stock> top    = stockRepository.findTopPerformers();
        List<Stock> losers = stockRepository.findUnderperformers();

        long profitable = stockRepository.countProfitableStocks();
        long losing     = stockRepository.countLosingStocks();

        List<Object[]> usdSummary = stockRepository.getPortfolioSummaryByCurrency("USD");
        List<Object[]> myrSummary = stockRepository.getPortfolioSummaryByCurrency("MYR");

        result.put("topPerformers",   top.stream().limit(3).map(this::toAnalyticsDto).toList());
        result.put("underperformers", losers.stream().limit(3).map(this::toAnalyticsDto).toList());
        result.put("profitableCount", profitable);
        result.put("losingCount",     losing);
        result.put("totalStocks",     profitable + losing);
        result.put("usdSummary",      usdSummary.isEmpty() ? null : mapSummary(usdSummary.get(0)));
        result.put("myrSummary",      myrSummary.isEmpty() ? null : mapSummary(myrSummary.get(0)));

        return result;
    }

    private Map<String, Object> toAnalyticsDto(Stock s) {
        Map<String, Object> m = new HashMap<>();
        double glPct = s.getBuyPrice().compareTo(BigDecimal.ZERO) != 0
                ? s.getCurrentPrice().subtract(s.getBuyPrice())
                .divide(s.getBuyPrice(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0;
        m.put("symbol",      s.getSymbol());
        m.put("companyName", s.getCompanyName());
        m.put("currency",    s.getCurrency());
        m.put("gainLossPct", Math.round(glPct * 100.0) / 100.0);
        m.put("gainLoss",    s.getCurrentPrice().subtract(s.getBuyPrice())
                .multiply(BigDecimal.valueOf(s.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP));
        return m;
    }

    private Map<String, Object> mapSummary(Object[] row) {
        Map<String, Object> m = new HashMap<>();
        m.put("totalValue",    row[0]);
        m.put("totalCost",     row[1]);
        m.put("totalGainLoss", row[2]);
        m.put("holdingCount",  row[3]);
        return m;
    }
}
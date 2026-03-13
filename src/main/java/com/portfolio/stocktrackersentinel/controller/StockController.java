package com.portfolio.stocktrackersentinel.controller;

import com.portfolio.stocktrackersentinel.dto.PortfolioSummaryDTO;
import com.portfolio.stocktrackersentinel.entity.Stock;
import com.portfolio.stocktrackersentinel.entity.StockHistory;
import com.portfolio.stocktrackersentinel.service.FinnhubService;
import com.portfolio.stocktrackersentinel.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final FinnhubService finnhubService;

    @GetMapping
    public ResponseEntity<List<Stock>> getAllStocks() {
        return ResponseEntity.ok(stockService.getAllStocks());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Stock> getStockById(@PathVariable Long id) {
        return ResponseEntity.ok(stockService.getStockById(id));
    }

    @PostMapping
    public ResponseEntity<Stock> addStock(@RequestBody Stock stock) {
        return ResponseEntity.ok(stockService.addStock(stock));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Stock> updateStock(@PathVariable Long id,
                                             @RequestBody Stock stock) {
        return ResponseEntity.ok(stockService.updateStock(id, stock));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStock(@PathVariable Long id) {
        stockService.deleteStock(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/history")
    public ResponseEntity<List<StockHistory>> getHistory() {
        return ResponseEntity.ok(stockService.getHistory());
    }

    @GetMapping("/search")
    public ResponseEntity<Map> searchStocks(@RequestParam String query) {
        return ResponseEntity.ok(finnhubService.searchStocks(query));
    }

    // ── Portfolio Summary (currency breakdown) ────────────
    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummaryDTO> getPortfolioSummary() {
        return ResponseEntity.ok(stockService.getPortfolioSummary());
    }

    // ── Multithreading ────────────────────────────────────
    @PostMapping("/refresh-prices")
    public ResponseEntity<String> refreshAllPrices() {
        List<Stock> stocks = stockService.getAllStocks();

        List<CompletableFuture<Stock>> futures = stocks.stream()
                .map(s -> finnhubService.getLivePrice(s.getSymbol())
                        .thenApply(price -> {
                            if (price != null) {
                                s.setCurrentPrice(price);
                                return stockService.updateStock(s.getId(), s);
                            }
                            return s;
                        }))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("All {} threads completed", stocks.size());
        return ResponseEntity.ok("Refreshed " + stocks.size() + " stocks");
    }

    // ── Single price lookup ───────────────────────────────
    @GetMapping("/price-lookup")
    public CompletableFuture<ResponseEntity<BigDecimal>> priceLookup(
            @RequestParam String symbol) {
        return finnhubService.getLivePrice(symbol)
                .thenApply(price -> price != null
                        ? ResponseEntity.ok(price)
                        : ResponseEntity.ok(BigDecimal.ZERO));
    }
    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        Map<String, Object> analytics = stockService.getAnalytics();
        return ResponseEntity.ok(analytics);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
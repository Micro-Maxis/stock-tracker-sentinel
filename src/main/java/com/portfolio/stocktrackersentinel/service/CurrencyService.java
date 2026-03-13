package com.portfolio.stocktrackersentinel.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class CurrencyService {

    private final RestTemplate restTemplate = new RestTemplate();

    // volatile ensures writes from one thread are immediately visible to all others
    private volatile BigDecimal cachedUsdToMyr = BigDecimal.valueOf(4.70);
    private volatile LocalDateTime lastFetched = null;

    // prevents multiple threads from calling the API simultaneously
    private final ReentrantLock refreshLock = new ReentrantLock();

    // ── Get USD→MYR rate ─────────────────────────────────
    public BigDecimal getUsdToMyrRate() {
        if (lastFetched == null ||
                lastFetched.isBefore(LocalDateTime.now().minusHours(1))) {
            refreshRate();
        }
        return cachedUsdToMyr;
    }

    @Async
    public CompletableFuture<Void> refreshRate() {
        // tryLock() returns false immediately if another thread already holds the lock
        // preventing duplicate API calls when multiple threads trigger a refresh
        if (!refreshLock.tryLock()) {
            log.debug("Rate refresh already in progress on another thread — skipping");
            return CompletableFuture.completedFuture(null);
        }
        try {
            String url = "https://open.er-api.com/v6/latest/USD";
            Map response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.get("rates") != null) {
                Map rates = (Map) response.get("rates");
                double myr = ((Number) rates.get("MYR")).doubleValue();
                cachedUsdToMyr = BigDecimal.valueOf(myr)
                        .setScale(4, RoundingMode.HALF_UP);
                lastFetched = LocalDateTime.now();
                log.debug("USD/MYR rate updated: {}", cachedUsdToMyr);
            }
        } catch (Exception e) {
            log.error("Failed to fetch exchange rate: {}", e.getMessage());
        } finally {
            // always release the lock even if an exception occurs
            refreshLock.unlock();
        }
        return CompletableFuture.completedFuture(null);
    }

    // ── Convert amount to MYR ─────────────────────────────
    public BigDecimal toMyr(BigDecimal amount, String currency) {
        if ("MYR".equals(currency)) return amount;
        if ("USD".equals(currency)) {
            return amount.multiply(cachedUsdToMyr)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return amount;
    }

    // ── Auto-detect from symbol ───────────────────────────
    public String detectCurrency(String symbol) {
        if (symbol == null) return "USD";
        return symbol.toUpperCase().trim().endsWith(".KL") ? "MYR" : "USD";
    }

    public String detectExchange(String symbol) {
        if (symbol == null) return "US";
        return symbol.toUpperCase().trim().endsWith(".KL") ? "MYR" : "US";
    }

    public BigDecimal getCachedRate() {
        return cachedUsdToMyr;
    }
}
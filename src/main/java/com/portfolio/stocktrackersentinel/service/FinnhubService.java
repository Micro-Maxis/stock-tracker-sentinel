package com.portfolio.stocktrackersentinel.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@SuppressWarnings("unchecked")
public class FinnhubService {

    @Value("${finnhub.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Get current price for a symbol ───────────────────
    @Async
    public CompletableFuture<BigDecimal> getFinnhubPrice(String symbol) {
        log.debug("Thread [{}] fetching price for {}",
                Thread.currentThread().getName(), symbol);
        try {
            String url = "https://finnhub.io/api/v1/quote?symbol="
                    + symbol.toUpperCase() + "&token=" + apiKey;

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.get("c") != null) {
                double price = ((Number) response.get("c")).doubleValue();
                if (price > 0) {
                    return CompletableFuture.completedFuture(
                            BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP)
                    );
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch price for {}: {}", symbol, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    // ── Search for stocks by name/symbol ─────────────────
    public Map<String, Object> searchStocks(String query) {
        log.debug("Searching stocks for query: {}", query);
        try {
            String url = "https://finnhub.io/api/v1/search?q="
                    + query + "&token=" + apiKey;
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            log.error("Search failed for {}: {}", query, e.getMessage());
            return Map.of();
        }
    }

    // ── Yahoo Finance for .KL stocks ─────────────────────
    @Async
    public CompletableFuture<BigDecimal> getYahooPrice(String symbol) {
        log.debug("Thread [{}] fetching Yahoo price for {}",
                Thread.currentThread().getName(), symbol);
        try {
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/"
                    + symbol.toUpperCase()
                    + "?interval=1d&range=1d";

            // Yahoo needs a browser-like User-Agent or it blocks the request
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, Map.class);

            Map<String, Object> body = response.getBody();
            if (body != null) {
                Map<String, Object> chart = (Map<String, Object>) body.get("chart");
                if (chart != null) {
                    List<Map<String, Object>> resultList =
                            (List<Map<String, Object>>) chart.get("result");
                    if (resultList != null && !resultList.isEmpty()) {
                        Map<String, Object> result = resultList.get(0);
                        Map<String, Object> meta = (Map<String, Object>) result.get("meta");
                        if (meta != null && meta.get("regularMarketPrice") != null) {
                            double price = ((Number) meta.get("regularMarketPrice"))
                                    .doubleValue();
                            log.debug("Yahoo price for {}: {}", symbol, price);
                            return CompletableFuture.completedFuture(
                                    BigDecimal.valueOf(price)
                                            .setScale(2, RoundingMode.HALF_UP));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Yahoo Finance failed for {}: {}", symbol, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    // ── Smart router — picks API based on symbol ──────────
    @Async
    public CompletableFuture<BigDecimal> getLivePrice(String symbol) {
        String s = symbol.toUpperCase().trim();
        if (s.endsWith(".KL")) {
            log.debug("Routing {} → Yahoo Finance (Bursa MYR)", s);
            return getYahooPrice(s);
        }
        log.debug("Routing {} → Finnhub (US/Global)", s);
        return getFinnhubPrice(s);
    }
}
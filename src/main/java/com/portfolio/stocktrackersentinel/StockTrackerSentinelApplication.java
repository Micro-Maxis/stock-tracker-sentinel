package com.portfolio.stocktrackersentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class StockTrackerSentinelApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockTrackerSentinelApplication.class, args);
    }
}
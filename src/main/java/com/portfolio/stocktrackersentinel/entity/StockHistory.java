package com.portfolio.stocktrackersentinel.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "stock_history")
public class StockHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private String companyName;
    private BigDecimal buyPrice;
    private Double quantity;
    private BigDecimal currentPrice;

    private String currency;
    private String exchange;

    private LocalDateTime createdAt;
    private LocalDateTime removedAt;

    private BigDecimal finalGainLoss;
}
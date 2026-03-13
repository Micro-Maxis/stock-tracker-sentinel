package com.portfolio.stocktrackersentinel.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false)
    private BigDecimal buyPrice;

    @Column(nullable = false)
    private Double quantity;

    @Column(nullable = false)
    private BigDecimal currentPrice;

    @Column(nullable = false)
    private String currency;     // "USD" or "MYR"

    @Column(nullable = false)
    private String exchange;     // "US" or "MYR" (Bursa)

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.currency == null) this.currency = "USD";
        if (this.exchange == null) this.exchange = "US";
    }
}
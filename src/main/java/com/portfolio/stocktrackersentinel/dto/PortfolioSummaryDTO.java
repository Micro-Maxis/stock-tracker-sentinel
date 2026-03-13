package com.portfolio.stocktrackersentinel.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PortfolioSummaryDTO {
    private BigDecimal usdAssets   = BigDecimal.ZERO;
    private BigDecimal myrAssets   = BigDecimal.ZERO;
    private BigDecimal usdGainLoss = BigDecimal.ZERO;
    private BigDecimal myrGainLoss = BigDecimal.ZERO;
    private BigDecimal totalValueMyr  = BigDecimal.ZERO;
    private BigDecimal totalGainLoss  = BigDecimal.ZERO;
    private BigDecimal usdToMyrRate   = BigDecimal.ZERO;
    private int usdHoldings = 0;
    private int myrHoldings = 0;
}
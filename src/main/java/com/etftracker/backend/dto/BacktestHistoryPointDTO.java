package com.etftracker.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BacktestHistoryPointDTO {
    private LocalDate date;
    private BigDecimal cumulativeInvestment;
    private BigDecimal portfolioValue;
    private BigDecimal sharesHeld;
}

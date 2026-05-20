package com.etftracker.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResultDTO {
    private String ticker;
    private String assetName;
    private BigDecimal totalInvested;
    private BigDecimal totalShares;
    private BigDecimal currentValue;
    private BigDecimal totalReturn;
    private BigDecimal returnRate; // 例如 0.2530 代表 25.30%
    private BigDecimal dividendEarned;
    private List<BacktestHistoryPointDTO> history;
}

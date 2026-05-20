package com.etftracker.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 投資組合總覽 DTO
 * 彙總使用者所有持倉的統計摘要與各資產分項明細
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSummaryDTO {

    /** 累計投入總本金 */
    private BigDecimal totalInvested;

    /** 目前持有部位的總市值 */
    private BigDecimal totalMarketValue;

    /** 未實現損益（市值 - 本金） */
    private BigDecimal unrealizedPnL;

    /** 未實現報酬率（例如 0.1530 代表 15.30%） */
    private BigDecimal unrealizedReturnRate;

    /** 各資產持倉明細列表 */
    private List<PortfolioHoldingDTO> holdings;

    /**
     * 單一資產的持倉明細 DTO（巢狀類別）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortfolioHoldingDTO {
        /** 資產代號（例如 "0050"） */
        private String ticker;

        /** 資產中文名稱 */
        private String assetName;

        /** 持有總股數 */
        private BigDecimal totalShares;

        /** 平均買入成本（元/股） */
        private BigDecimal averageCost;

        /** 最新收盤價（元/股） */
        private BigDecimal latestPrice;

        /** 當前市值（totalShares * latestPrice） */
        private BigDecimal marketValue;

        /** 投入本金（totalShares * averageCost） */
        private BigDecimal costBasis;

        /** 未實現損益 */
        private BigDecimal unrealizedPnL;

        /** 未實現報酬率 */
        private BigDecimal unrealizedReturnRate;

        /** 持倉佔總市值比例（例如 0.6530 代表 65.30%） */
        private BigDecimal portfolioWeight;
    }
}

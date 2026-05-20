package com.etftracker.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 未來資產增值模擬結果 DTO
 * 使用歷史 CAGR 推算在持續定期定額下，未來各年度可能的資產規模
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionResultDTO {

    /** 標的代號 */
    private String ticker;

    /** 標的名稱 */
    private String assetName;

    /** 作為推算基礎的歷史年化複合成長率（CAGR）來源區間 */
    private String cagrBasePeriod;

    /** 計算出的歷史 CAGR（例如 0.1230 代表 12.30%/年） */
    private BigDecimal historicalCagr;

    /** 初始已投入本金（作為模擬起點） */
    private BigDecimal initialInvestment;

    /** 每次扣款金額 */
    private BigDecimal monthlyInvestmentAmount;

    /** 每月扣款頻率（即每月扣款天數） */
    private int deductionFrequencyPerMonth;

    /** 模擬總年限 */
    private int projectionYears;

    /** 各年度的資產增值節點，從第 1 年到第 projectionYears 年 */
    private List<ProjectionYearPointDTO> yearlyProjection;

    /** 最終模擬年限末的累計投入本金 */
    private BigDecimal projectedTotalInvested;

    /** 最終模擬年限末的預估總資產（含複利增長） */
    private BigDecimal projectedFinalValue;

    /** 最終預估未實現損益 */
    private BigDecimal projectedTotalReturn;

    /**
     * 每年模擬節點 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectionYearPointDTO {
        /** 第幾年（1-based） */
        private int year;

        /** 截至本年底的累計投入本金 */
        private BigDecimal cumulativeInvested;

        /** 截至本年底的預估資產市值（含 CAGR 複利效果） */
        private BigDecimal projectedValue;
    }
}

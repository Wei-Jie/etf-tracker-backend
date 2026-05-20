package com.etftracker.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 單筆持倉交易明細 DTO
 * 供 GET /api/v1/portfolio/holdings 回傳每筆買入紀錄使用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HoldingRecordDTO {

    /** 持倉紀錄 ID（用於刪除操作） */
    private Long portfolioId;

    /** 資產代號（例如 "0050"） */
    private String ticker;

    /** 資產中文名稱 */
    private String assetName;

    /** 買入日期 */
    private LocalDate buyDate;

    /** 買入數量（支援零股小數） */
    private BigDecimal quantity;

    /** 買入單價（元/股） */
    private BigDecimal unitPrice;

    /** 小計成本（quantity * unitPrice） */
    private BigDecimal totalCost;
}

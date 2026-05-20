package com.etftracker.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新增持倉交易紀錄的請求 DTO
 * 對應 POST /api/v1/portfolio/holdings 的請求體
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddHoldingRequestDTO {

    /** 資產代號（例如 "0050"），不可為空 */
    @NotBlank(message = "標的代號 ticker 不能為空")
    private String ticker;

    /** 買入日期（格式 yyyy-MM-dd），不可為空 */
    @NotNull(message = "買入日期不能為空")
    private LocalDate buyDate;

    /** 買入數量（股數，支援零股小數），必須大於 0 */
    @NotNull(message = "買入數量不能為空")
    @DecimalMin(value = "0.0001", message = "買入數量必須大於 0")
    private BigDecimal quantity;

    /** 買入單價（元/股），必須大於 0 */
    @NotNull(message = "買入單價不能為空")
    @DecimalMin(value = "0.01", message = "買入單價必須大於 0")
    private BigDecimal unitPrice;
}

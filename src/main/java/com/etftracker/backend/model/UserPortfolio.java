package com.etftracker.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 使用者投資組合實體，記錄每筆交易明細
 */
@Entity
@Table(
    name = "USER_PORTFOLIO",
    indexes = {
        @Index(name = "idx_portfolio_asset", columnList = "asset_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPortfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_portfolio_seq")
    @SequenceGenerator(name = "user_portfolio_seq", sequenceName = "user_portfolio_seq", allocationSize = 50)
    private Long portfolioId;

    @Column(nullable = false)
    private String owner = "自己";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private AssetInfo asset;

    @Column(nullable = false)
    @NotNull(message = "交易日期不能為空")
    private LocalDate buyDate;

    @Column(nullable = false, precision = 15, scale = 4)
    @NotNull(message = "交易數量不能為空")
    @DecimalMin(value = "0.0001", message = "交易數量必須大於 0")
    private BigDecimal quantity;

    @Column(nullable = false, precision = 10, scale = 4)
    @NotNull(message = "交易單價不能為空")
    @DecimalMin(value = "0.01", message = "交易單價必須大於 0")
    private BigDecimal unitPrice;
}


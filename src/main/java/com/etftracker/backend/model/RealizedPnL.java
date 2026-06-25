package com.etftracker.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 已實現損益實體，記錄每次賣出交易的利潤或虧損
 */
@Entity
@Table(
    name = "REALIZED_PNL",
    indexes = {
        @Index(name = "idx_realized_owner", columnList = "owner"),
        @Index(name = "idx_realized_ticker", columnList = "ticker")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RealizedPnL {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "realized_pnl_seq")
    @SequenceGenerator(name = "realized_pnl_seq", sequenceName = "realized_pnl_seq", allocationSize = 50)
    private Long realizedId;

    @Column(nullable = false)
    private String ticker;

    @Column(nullable = false)
    private String assetName;

    @Column(nullable = false)
    private LocalDate sellDate;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal sellPrice;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal averageBuyPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal realizedPnL; // (sellPrice - averageBuyPrice) * quantity

    @Column(nullable = false)
    private String owner = "自己";
}

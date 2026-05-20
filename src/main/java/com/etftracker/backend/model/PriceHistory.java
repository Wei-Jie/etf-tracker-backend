package com.etftracker.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "PRICE_HISTORY")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private AssetInfo asset;

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal closingPrice;
}

package com.etftracker.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "DIVIDEND_HISTORY")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DividendHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dividendId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private AssetInfo asset;

    @Column(nullable = false)
    private LocalDate exDividendDate;

    @Column(precision = 10, scale = 4)
    private BigDecimal cashDividend;

    @Column(precision = 10, scale = 4)
    private BigDecimal stockDividend;
}

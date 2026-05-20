package com.etftracker.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "USER_PORTFOLIO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPortfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long portfolioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private AssetInfo asset;

    @Column(nullable = false)
    private LocalDate buyDate;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal unitPrice;
}

package com.etftracker.backend.service;

import com.etftracker.backend.dto.PortfolioSummaryDTO;
import com.etftracker.backend.dto.PortfolioSummaryDTO.PortfolioHoldingDTO;
import com.etftracker.backend.model.UserPortfolio;
import com.etftracker.backend.repository.PriceHistoryRepository;
import com.etftracker.backend.repository.UserPortfolioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 投資組合業務邏輯服務
 * 計算使用者的持倉市值、平均成本、未實現損益與各資產佔比
 */
@Service
public class PortfolioService {

    @Autowired
    private UserPortfolioRepository portfolioRepository;

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    /**
     * 取得使用者的投資組合總覽
     * 依資產分組計算持股數、平均成本，並查詢最新收盤價計算市值與損益
     *
     * @return 投資組合總覽 PortfolioSummaryDTO
     */
    public PortfolioSummaryDTO getPortfolioSummary() {
        // 1. 取得所有持倉記錄
        List<UserPortfolio> allHoldings = portfolioRepository.findAllByOrderByAsset_TickerAscBuyDateAsc();

        if (allHoldings.isEmpty()) {
            return new PortfolioSummaryDTO(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    Collections.emptyList()
            );
        }

        // 2. 依 ticker 分組統計
        Map<String, List<UserPortfolio>> grouped = allHoldings.stream()
                .collect(Collectors.groupingBy(p -> p.getAsset().getTicker()));

        List<PortfolioHoldingDTO> holdingDTOs = new ArrayList<>();
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;

        for (Map.Entry<String, List<UserPortfolio>> entry : grouped.entrySet()) {
            String ticker = entry.getKey();
            List<UserPortfolio> positions = entry.getValue();
            String assetName = positions.get(0).getAsset().getName();

            // 計算該資產的總持股數與加權平均成本
            BigDecimal totalShares = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;

            for (UserPortfolio pos : positions) {
                BigDecimal qty = pos.getQuantity();
                BigDecimal price = pos.getUnitPrice();
                totalShares = totalShares.add(qty);
                totalCost = totalCost.add(qty.multiply(price));
            }

            BigDecimal averageCost = BigDecimal.ZERO;
            if (totalShares.compareTo(BigDecimal.ZERO) > 0) {
                averageCost = totalCost.divide(totalShares, 4, RoundingMode.HALF_UP);
            }

            // 查詢最新收盤價（取資料庫中該 ticker 最新一筆）
            BigDecimal latestPrice = priceHistoryRepository
                    .findTopByAsset_TickerOrderByTradeDateDesc(ticker)
                    .map(ph -> ph.getClosingPrice())
                    .orElse(averageCost); // 若無價格資料，以成本代替避免 NPE

            // 計算市值與損益
            BigDecimal marketValue = totalShares.multiply(latestPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal costBasis = totalCost.setScale(2, RoundingMode.HALF_UP);
            BigDecimal unrealizedPnL = marketValue.subtract(costBasis).setScale(2, RoundingMode.HALF_UP);
            BigDecimal unrealizedReturnRate = BigDecimal.ZERO;
            if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
                unrealizedReturnRate = unrealizedPnL.divide(costBasis, 4, RoundingMode.HALF_UP);
            }

            totalMarketValue = totalMarketValue.add(marketValue);
            totalCostBasis = totalCostBasis.add(costBasis);

            holdingDTOs.add(new PortfolioHoldingDTO(
                    ticker,
                    assetName,
                    totalShares.setScale(4, RoundingMode.HALF_UP),
                    averageCost,
                    latestPrice,
                    marketValue,
                    costBasis,
                    unrealizedPnL,
                    unrealizedReturnRate,
                    BigDecimal.ZERO // 佔比先設為 0，等最終市值算出後再計算
            ));
        }

        // 3. 計算各資產佔比
        BigDecimal finalTotalValue = totalMarketValue;
        if (finalTotalValue.compareTo(BigDecimal.ZERO) > 0) {
            holdingDTOs = holdingDTOs.stream().map(h -> {
                BigDecimal weight = h.getMarketValue().divide(finalTotalValue, 4, RoundingMode.HALF_UP);
                h.setPortfolioWeight(weight);
                return h;
            }).collect(Collectors.toList());
        }

        // 4. 計算整體未實現損益與報酬率
        BigDecimal totalUnrealizedPnL = totalMarketValue.subtract(totalCostBasis).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalUnrealizedReturnRate = BigDecimal.ZERO;
        if (totalCostBasis.compareTo(BigDecimal.ZERO) > 0) {
            totalUnrealizedReturnRate = totalUnrealizedPnL.divide(totalCostBasis, 4, RoundingMode.HALF_UP);
        }

        return new PortfolioSummaryDTO(
                totalCostBasis,
                totalMarketValue.setScale(2, RoundingMode.HALF_UP),
                totalUnrealizedPnL,
                totalUnrealizedReturnRate,
                holdingDTOs
        );
    }
}

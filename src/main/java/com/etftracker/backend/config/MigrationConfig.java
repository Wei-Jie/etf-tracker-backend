package com.etftracker.backend.config;

import com.etftracker.backend.model.UserPortfolio;
import com.etftracker.backend.model.RealizedPnL;
import com.etftracker.backend.repository.UserPortfolioRepository;
import com.etftracker.backend.repository.RealizedPnLRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 歷史數據手續費回補 Migration 設定類別
 * 在系統重啟、Hibernate 自動建立欄位後執行，全自動補齊歷史交易手續費與稅金
 */
@Configuration
public class MigrationConfig {

    @Bean
    public CommandLineRunner backfillFees(UserPortfolioRepository portfolioRepository,
                                          RealizedPnLRepository realizedPnLRepository) {
        return args -> {
            // 1. 回補 UserPortfolio 的買入手續費 (fee 為 null 的紀錄)
            List<UserPortfolio> emptyFeePortfolios = portfolioRepository.findAll()
                    .stream()
                    .filter(p -> p.getFee() == null)
                    .toList();

            if (!emptyFeePortfolios.isEmpty()) {
                System.out.println("[Migration] 發現 " + emptyFeePortfolios.size() + " 筆歷史買入紀錄缺少手續費，開始自動估算回補...");
                for (UserPortfolio p : emptyFeePortfolios) {
                    BigDecimal volume = p.getQuantity().multiply(p.getUnitPrice());
                    BigDecimal calculatedFee = volume.multiply(new BigDecimal("0.001425"))
                            .multiply(new BigDecimal("0.6")) // 富邦電子單 6 折
                            .setScale(0, RoundingMode.HALF_UP);

                    // 判斷整股或零股套用低消
                    BigDecimal finalFee;
                    boolean isOddShare = p.getQuantity().remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) != 0;
                    if (isOddShare) {
                        // 零股低消 1 元
                        finalFee = calculatedFee.compareTo(BigDecimal.ONE) < 0 ? BigDecimal.ONE : calculatedFee;
                    } else {
                        // 整股低消 20 元
                        finalFee = calculatedFee.compareTo(new BigDecimal("20")) < 0 ? new BigDecimal("20") : calculatedFee;
                    }

                    p.setFee(finalFee);
                }
                portfolioRepository.saveAll(emptyFeePortfolios);
                System.out.println("[Migration] 歷史買入紀錄手續費回補完成！");
            }

            // 2. 回補 RealizedPnL 的賣出手續費與證交稅
            List<RealizedPnL> emptyPnLRecords = realizedPnLRepository.findAll()
                    .stream()
                    .filter(r -> r.getFee() == null || r.getTax() == null)
                    .toList();

            if (!emptyPnLRecords.isEmpty()) {
                System.out.println("[Migration] 發現 " + emptyPnLRecords.size() + " 筆歷史已實現損益紀錄缺少費用/稅金，開始自動估算回補...");
                for (RealizedPnL r : emptyPnLRecords) {
                    BigDecimal volume = r.getQuantity().multiply(r.getSellPrice());
                    
                    // 估算手續費
                    BigDecimal calculatedFee = volume.multiply(new BigDecimal("0.001425"))
                            .multiply(new BigDecimal("0.6"))
                            .setScale(0, RoundingMode.HALF_UP);
                    BigDecimal finalFee;
                    boolean isOddShare = r.getQuantity().remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) != 0;
                    if (isOddShare) {
                        finalFee = calculatedFee.compareTo(BigDecimal.ONE) < 0 ? BigDecimal.ONE : calculatedFee;
                    } else {
                        finalFee = calculatedFee.compareTo(new BigDecimal("20")) < 0 ? new BigDecimal("20") : calculatedFee;
                    }
                    
                    // 估算證交稅 (ETF: 0.1%, 股票: 0.3%)
                    BigDecimal taxRate = r.getTicker().startsWith("00") ? new BigDecimal("0.001") : new BigDecimal("0.003");
                    BigDecimal finalTax = volume.multiply(taxRate).setScale(0, RoundingMode.HALF_UP);

                    if (r.getFee() == null) {
                        r.setFee(finalFee);
                    }
                    if (r.getTax() == null) {
                        r.setTax(finalTax);
                    }
                }
                realizedPnLRepository.saveAll(emptyPnLRecords);
                System.out.println("[Migration] 歷史已實現損益紀錄費用與稅金回補完成！");
            }
        };
    }
}

package com.etftracker.backend.repository;

import com.etftracker.backend.model.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    Optional<PriceHistory> findByAsset_AssetIdAndTradeDate(Long assetId, LocalDate tradeDate);

    // 批次查詢：一次撈出今天所有已存在的收盤價紀錄，避免逐筆查詢
    List<PriceHistory> findAllByTradeDate(LocalDate tradeDate);

    // 依 Ticker 查詢所有歷史股價，並按日期遞增排序，方便前端畫圖
    List<PriceHistory> findAllByAsset_TickerOrderByTradeDateAsc(String ticker);

    // 依 Ticker 查詢特定時間區間內歷史股價，並按日期遞增排序，供回測使用
    List<PriceHistory> findAllByAsset_TickerAndTradeDateBetweenOrderByTradeDateAsc(
            String ticker, LocalDate startDate, LocalDate endDate);

    // 查詢指定 Ticker 最新一筆收盤價（取交易日期最大值），供投資組合市值計算使用
    Optional<PriceHistory> findTopByAsset_TickerOrderByTradeDateDesc(String ticker);
}

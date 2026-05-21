package com.etftracker.backend.service;

import com.etftracker.backend.model.AssetInfo;
import com.etftracker.backend.model.PriceHistory;
import com.etftracker.backend.repository.AssetInfoRepository;
import com.etftracker.backend.repository.PriceHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EtfService {

    private final AssetInfoRepository assetInfoRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    public EtfService(AssetInfoRepository assetInfoRepository, PriceHistoryRepository priceHistoryRepository) {
        this.assetInfoRepository = assetInfoRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    /**
     * 獲取所有已同步的 ETF 與股票清單
     */
    @Transactional(readOnly = true)
    public List<AssetInfo> getAllAssets() {
        return assetInfoRepository.findAll();
    }

    /**
     * 獲取特定 Ticker (如 0050) 的歷史價格紀錄（依日期排序），可指定時間範圍
     */
    @Transactional(readOnly = true)
    public List<PriceHistory> getPriceHistory(String ticker, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return priceHistoryRepository.findAllByAsset_TickerAndTradeDateBetweenOrderByTradeDateAsc(ticker, startDate, endDate);
        }
        return priceHistoryRepository.findAllByAsset_TickerOrderByTradeDateAsc(ticker);
    }

    @Transactional(readOnly = true)
    public List<PriceHistory> getPriceHistory(String ticker) {
        return getPriceHistory(ticker, null, null);
    }
}

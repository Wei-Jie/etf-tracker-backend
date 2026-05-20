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
     * 獲取特定 Ticker (如 0050) 的完整歷史價格紀錄（依日期排序）
     */
    @Transactional(readOnly = true)
    public List<PriceHistory> getPriceHistory(String ticker) {
        return priceHistoryRepository.findAllByAsset_TickerOrderByTradeDateAsc(ticker);
    }
}

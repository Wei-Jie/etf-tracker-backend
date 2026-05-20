package com.etftracker.backend.service;

import com.etftracker.backend.dto.TwseStockDayDTO;
import com.etftracker.backend.model.AssetInfo;
import com.etftracker.backend.model.PriceHistory;
import com.etftracker.backend.repository.AssetInfoRepository;
import com.etftracker.backend.repository.PriceHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataSyncService {

    private final TwseApiService twseApiService;
    private final AssetInfoRepository assetInfoRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    // 每次批次寫入的資料筆數
    private static final int BATCH_SIZE = 100;

    public DataSyncService(TwseApiService twseApiService,
                           AssetInfoRepository assetInfoRepository,
                           PriceHistoryRepository priceHistoryRepository) {
        this.twseApiService = twseApiService;
        this.assetInfoRepository = assetInfoRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @Transactional
    public void syncDailyClosingPrices() {
        List<TwseStockDayDTO> dtoList = twseApiService.fetchDailyClosingPrices();
        LocalDate today = LocalDate.now();

        // ─── 步驟一：解析 TWSE 原始資料，過濾掉無效資料 ───────────────────
        // 使用 Map<ticker, closingPrice> 儲存所有有效的今日資料
        Map<String, TwseValidData> validDataMap = new LinkedHashMap<>();
        for (TwseStockDayDTO dto : dtoList) {
            String ticker = dto.getCode();
            String closingPriceStr = dto.getClosingPrice();

            if (ticker == null || closingPriceStr == null || closingPriceStr.trim().isEmpty()) {
                continue;
            }
            try {
                BigDecimal closingPrice = new BigDecimal(closingPriceStr.replace(",", ""));
                validDataMap.put(ticker, new TwseValidData(ticker, dto.getName(), closingPrice));
            } catch (Exception e) {
                // 忽略無效格式的收盤價 (例如 "--")
            }
        }

        // ─── 步驟二：一次查詢所有已存在的 AssetInfo，建立快取 Map ──────────
        // 改成 1 次查詢，取代原本每筆都查一次
        Map<String, AssetInfo> existingAssetMap = assetInfoRepository.findAll()
                .stream()
                .collect(Collectors.toMap(AssetInfo::getTicker, a -> a));

        // ─── 步驟三：找出新資產，批次新增 AssetInfo ─────────────────────────
        List<AssetInfo> newAssets = new ArrayList<>();
        for (String ticker : validDataMap.keySet()) {
            if (!existingAssetMap.containsKey(ticker)) {
                TwseValidData data = validDataMap.get(ticker);
                AssetInfo newAsset = new AssetInfo();
                newAsset.setTicker(ticker);
                newAsset.setName(data.name());
                newAsset.setAssetType(ticker.startsWith("00") ? "ETF" : "STOCK");
                newAssets.add(newAsset);
            }
        }
        // 分批寫入新的 AssetInfo
        if (!newAssets.isEmpty()) {
            for (int i = 0; i < newAssets.size(); i += BATCH_SIZE) {
                List<AssetInfo> batch = newAssets.subList(i, Math.min(i + BATCH_SIZE, newAssets.size()));
                List<AssetInfo> saved = assetInfoRepository.saveAll(batch);
                // 把新存好的資產加入快取，供後續步驟使用
                saved.forEach(a -> existingAssetMap.put(a.getTicker(), a));
            }
        }

        // ─── 步驟四：一次查詢今天已存在的 PriceHistory，建立快取 Set ────────
        // 避免重複寫入同一天的收盤價
        Set<Long> existingPriceAssetIds = priceHistoryRepository
                .findAllByTradeDate(today)
                .stream()
                .map(ph -> ph.getAsset().getAssetId())
                .collect(Collectors.toSet());

        // ─── 步驟五：建立今日 PriceHistory 清單，批次寫入 ───────────────────
        List<PriceHistory> newPriceHistories = new ArrayList<>();
        for (TwseValidData data : validDataMap.values()) {
            AssetInfo asset = existingAssetMap.get(data.ticker());
            if (asset == null || existingPriceAssetIds.contains(asset.getAssetId())) {
                continue; // 資產不存在或今天已寫過，跳過
            }
            PriceHistory history = new PriceHistory();
            history.setAsset(asset);
            history.setTradeDate(today);
            history.setClosingPrice(data.closingPrice());
            newPriceHistories.add(history);
        }
        // 分批寫入 PriceHistory
        for (int i = 0; i < newPriceHistories.size(); i += BATCH_SIZE) {
            List<PriceHistory> batch = newPriceHistories.subList(i, Math.min(i + BATCH_SIZE, newPriceHistories.size()));
            priceHistoryRepository.saveAll(batch);
        }
    }

    // 用來暫存有效資料的輕量 Record
    private record TwseValidData(String ticker, String name, BigDecimal closingPrice) {}
}

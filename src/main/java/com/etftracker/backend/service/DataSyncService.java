package com.etftracker.backend.service;

import com.etftracker.backend.dto.TwseHistoryDayDTO;
import com.etftracker.backend.dto.TwseStockDayDTO;
import com.etftracker.backend.model.AssetInfo;
import com.etftracker.backend.model.PriceHistory;
import com.etftracker.backend.repository.AssetInfoRepository;
import com.etftracker.backend.repository.PriceHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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

    /**
     * 批次抓取指定標的的歷史收盤價
     * 從 startYearMonth 起逐月呼叫 TWSE STOCK_DAY API，寫入 price_history 表
     * 每次 API 請求間隔 3 秒，避免被 TWSE 限流
     *
     * @param tickers        要同步的標的代號列表，例如 ["0050", "0056", "00878"]
     * @param startYearMonth 起始年月，格式 "YYYYMM"，例如 "202008"
     * @return 同步結果摘要字串
     */
    public String syncHistoricalPrices(List<String> tickers, String startYearMonth) {
        YearMonth start = YearMonth.parse(startYearMonth, DateTimeFormatter.ofPattern("yyyyMM"));
        YearMonth end = YearMonth.now();

        // 建立 AssetInfo 快取
        Map<String, AssetInfo> assetMap = assetInfoRepository.findAll()
                .stream()
                .collect(Collectors.toMap(AssetInfo::getTicker, a -> a));

        int totalInserted = 0;
        int totalSkipped = 0;
        List<String> errors = new ArrayList<>();

        for (String ticker : tickers) {
            AssetInfo asset = assetMap.get(ticker);
            if (asset == null) {
                errors.add(ticker + "：找不到對應的 AssetInfo，請先執行每日同步");
                continue;
            }

            // 取得該標的已存在的所有日期，避免重複寫入
            Set<LocalDate> existingDates = priceHistoryRepository
                    .findAllByAsset_Ticker(ticker)
                    .stream()
                    .map(PriceHistory::getTradeDate)
                    .collect(Collectors.toSet());

            YearMonth current = start;
            while (!current.isAfter(end)) {
                String yearMonth = current.format(DateTimeFormatter.ofPattern("yyyyMM"));
                List<TwseHistoryDayDTO> monthData = twseApiService.fetchMonthlyHistory(ticker, yearMonth);

                List<PriceHistory> toInsert = new ArrayList<>();
                for (TwseHistoryDayDTO dto : monthData) {
                    try {
                        // 民國年轉換：例如 "113/05/21" → 2024-05-21
                        String[] parts = dto.dateRoc().split("/");
                        if (parts.length != 3) continue;
                        int year = Integer.parseInt(parts[0]) + 1911;
                        int month = Integer.parseInt(parts[1]);
                        int day = Integer.parseInt(parts[2]);
                        LocalDate tradeDate = LocalDate.of(year, month, day);

                        if (existingDates.contains(tradeDate)) {
                            totalSkipped++;
                            continue;
                        }

                        String priceStr = dto.closingPrice().replace(",", "").trim();
                        if (priceStr.isEmpty() || priceStr.equals("--")) continue;
                        BigDecimal price = new BigDecimal(priceStr);

                        PriceHistory ph = new PriceHistory();
                        ph.setAsset(asset);
                        ph.setTradeDate(tradeDate);
                        ph.setClosingPrice(price);
                        toInsert.add(ph);
                        existingDates.add(tradeDate);

                    } catch (Exception e) {
                        // 略過個別解析失敗的列
                    }
                }

                if (!toInsert.isEmpty()) {
                    priceHistoryRepository.saveAll(toInsert);
                    totalInserted += toInsert.size();
                    System.out.printf("[DataSync] %s %s：寫入 %d 筆%n", ticker, yearMonth, toInsert.size());
                }

                current = current.plusMonths(1);

                // 每次請求間隔 1 秒，避免 TWSE 限流（實測 1 秒安全且速度快）
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }

        String summary = String.format(
                "歷史資料同步完成！標的：%s｜新增：%d 筆｜略過重複：%d 筆%s",
                String.join(", ", tickers),
                totalInserted,
                totalSkipped,
                errors.isEmpty() ? "" : "｜錯誤：" + String.join("; ", errors)
        );
        System.out.println("[DataSync] " + summary);
        return summary;
    }
}

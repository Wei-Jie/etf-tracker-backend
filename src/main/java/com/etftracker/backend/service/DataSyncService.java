package com.etftracker.backend.service;

import com.etftracker.backend.dto.TwseHistoryDayDTO;
import com.etftracker.backend.dto.TwseStockDayDTO;
import com.etftracker.backend.model.AssetInfo;
import com.etftracker.backend.model.PriceHistory;
import com.etftracker.backend.repository.AssetInfoRepository;
import com.etftracker.backend.repository.PriceHistoryRepository;
import com.etftracker.backend.repository.UserPortfolioRepository;
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
    private final UserPortfolioRepository userPortfolioRepository;

    // 每次批次寫入的資料筆數
    private static final int BATCH_SIZE = 100;

    public DataSyncService(TwseApiService twseApiService,
                           AssetInfoRepository assetInfoRepository,
                           PriceHistoryRepository priceHistoryRepository,
                           UserPortfolioRepository userPortfolioRepository) {
        this.twseApiService = twseApiService;
        this.assetInfoRepository = assetInfoRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.userPortfolioRepository = userPortfolioRepository;
    }

    @Transactional
    public void syncDailyClosingPrices() {
        // ─── 步驟零：獲取有庫存的標的清單 (防呆預設 0050, 0056, 00878) ───
        List<String> distinctTickers = userPortfolioRepository.findDistinctTickers();
        Set<String> targetTickers = new HashSet<>(distinctTickers);
        if (targetTickers.isEmpty()) {
            targetTickers.addAll(Arrays.asList("0050", "0056", "00878"));
        }
        System.out.println("[DataSync] 今日同步目標標的清單: " + targetTickers);

        // ─── 步驟零點五：精準取得證交所當前數據的真實交易日期 (以 0050 最新成交日為真理之源) ───
        LocalDate todayZone = LocalDate.now(java.time.ZoneId.of("Asia/Taipei"));
        String currentYearMonth = todayZone.format(DateTimeFormatter.ofPattern("yyyyMM"));
        List<TwseHistoryDayDTO> historyList0050 = twseApiService.fetchMonthlyHistory("0050", currentYearMonth);
        
        LocalDate actualTradeDate = todayZone; // 預設 Fallback
        if (historyList0050 != null && !historyList0050.isEmpty()) {
            TwseHistoryDayDTO lastRecord = historyList0050.get(historyList0050.size() - 1);
            try {
                String[] parts = lastRecord.dateRoc().split("/");
                if (parts.length == 3) {
                    int year = Integer.parseInt(parts[0]) + 1911;
                    int month = Integer.parseInt(parts[1]);
                    int day = Integer.parseInt(parts[2]);
                    actualTradeDate = LocalDate.of(year, month, day);
                    System.out.println("[DataSync] 偵測到證交所最新收盤數據之真實交易日期為: " + actualTradeDate);
                }
            } catch (Exception e) {
                System.err.println("[DataSync] 解析 0050 最後交易日失敗，採用系統時間: " + e.getMessage());
            }
        }

        // ─── 步驟一：逐一呼叫官網歷史 API 抓取各標的今日最新收盤價 (每次請求間隔 1 秒防限流) ───────
        Map<String, TwseValidData> validDataMap = new LinkedHashMap<>();
        for (String ticker : targetTickers) {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            List<TwseHistoryDayDTO> historyList = twseApiService.fetchMonthlyHistory(ticker, currentYearMonth);
            if (historyList == null || historyList.isEmpty()) {
                System.err.println("[DataSync] 無法獲取標的 " + ticker + " 當月歷史收盤價，跳過。");
                continue;
            }

            TwseHistoryDayDTO lastRecord = historyList.get(historyList.size() - 1);
            try {
                String[] parts = lastRecord.dateRoc().split("/");
                if (parts.length == 3) {
                    int year = Integer.parseInt(parts[0]) + 1911;
                    int month = Integer.parseInt(parts[1]);
                    int day = Integer.parseInt(parts[2]);
                    LocalDate recordDate = LocalDate.of(year, month, day);

                    // 確保日期對齊今日真實交易日
                    if (recordDate.equals(actualTradeDate)) {
                        String priceStr = lastRecord.closingPrice().replace(",", "").trim();
                        if (!priceStr.isEmpty() && !priceStr.equals("--")) {
                            BigDecimal closingPrice = new BigDecimal(priceStr);
                            validDataMap.put(ticker, new TwseValidData(ticker, "", closingPrice));
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[DataSync] 解析標的 " + ticker + " 最新股價失敗: " + e.getMessage());
            }
        }

        // ─── 步驟二：一次查詢所有已存在的 AssetInfo，建立快取 Map ──────────
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

        // ─── 步驟四：一次查詢今天已存在的 PriceHistory，建立快取 Map ────────
        // 方便比對進行 Upsert (新增或覆蓋更新)
        Map<Long, PriceHistory> existingPriceMap = priceHistoryRepository
                .findAllByTradeDate(actualTradeDate)
                .stream()
                .collect(Collectors.toMap(ph -> ph.getAsset().getAssetId(), ph -> ph, (ph1, ph2) -> ph1));

        // ─── 步驟五：建立今日 PriceHistory 清單，批次寫入或更新 ───────────────
        List<PriceHistory> toSaveList = new ArrayList<>();
        int updatedCount = 0;
        int insertedCount = 0;

        for (TwseValidData data : validDataMap.values()) {
            AssetInfo asset = existingAssetMap.get(data.ticker());
            if (asset == null) {
                continue; // 資產不存在，跳過
            }

            if (existingPriceMap.containsKey(asset.getAssetId())) {
                PriceHistory existingPh = existingPriceMap.get(asset.getAssetId());
                // 若收盤價不同，則覆蓋更新，達到自癒效果
                if (existingPh.getClosingPrice().compareTo(data.closingPrice()) != 0) {
                    existingPh.setClosingPrice(data.closingPrice());
                    toSaveList.add(existingPh);
                    updatedCount++;
                    System.out.printf("[DataSync] 偵測到 %s 今日收盤價變更 (舊: %s, 新: %s)，執行自癒覆蓋。%n", 
                            data.ticker(), existingPh.getClosingPrice(), data.closingPrice());
                }
            } else {
                PriceHistory newPh = new PriceHistory();
                newPh.setAsset(asset);
                newPh.setTradeDate(actualTradeDate);
                newPh.setClosingPrice(data.closingPrice());
                toSaveList.add(newPh);
                insertedCount++;
            }
        }

        // 分批寫入/更新 PriceHistory
        if (!toSaveList.isEmpty()) {
            for (int i = 0; i < toSaveList.size(); i += BATCH_SIZE) {
                List<PriceHistory> batch = toSaveList.subList(i, Math.min(i + BATCH_SIZE, toSaveList.size()));
                priceHistoryRepository.saveAll(batch);
            }
            System.out.printf("[DataSync] 今日收盤價同步完成！新增: %d 筆，更新自癒: %d 筆。%n", insertedCount, updatedCount);
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

            // 取得該標的已存在的所有 PriceHistory，建立 Map 方便直接進行 Upsert 覆蓋更新
            Map<LocalDate, PriceHistory> existingPriceMap = priceHistoryRepository
                    .findAllByAsset_Ticker(ticker)
                    .stream()
                    .collect(Collectors.toMap(PriceHistory::getTradeDate, ph -> ph, (ph1, ph2) -> ph1));

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

                        String priceStr = dto.closingPrice().replace(",", "").trim();
                        if (priceStr.isEmpty() || priceStr.equals("--")) continue;
                        BigDecimal price = new BigDecimal(priceStr);

                        if (existingPriceMap.containsKey(tradeDate)) {
                            PriceHistory existingPh = existingPriceMap.get(tradeDate);
                            // 若偵測到收盤價不同（例如之前寫入了重複或錯誤的股價），則進行覆蓋更新自癒
                            if (existingPh.getClosingPrice().compareTo(price) != 0) {
                                existingPh.setClosingPrice(price);
                                toInsert.add(existingPh);
                                System.out.printf("[DataSync] 偵測到 %s %s 價格變更 (舊: %s, 新: %s)，將進行覆蓋更新。%n", 
                                        ticker, tradeDate, existingPh.getClosingPrice(), price);
                            } else {
                                totalSkipped++;
                            }
                        } else {
                            PriceHistory ph = new PriceHistory();
                            ph.setAsset(asset);
                            ph.setTradeDate(tradeDate);
                            ph.setClosingPrice(price);
                            toInsert.add(ph);
                            existingPriceMap.put(tradeDate, ph);
                        }

                    } catch (Exception e) {
                        // 略過個別解析失敗的列
                    }
                }

                if (!toInsert.isEmpty()) {
                    priceHistoryRepository.saveAll(toInsert);
                    totalInserted += toInsert.size();
                    System.out.printf("[DataSync] %s %s：寫入/更新 %d 筆%n", ticker, yearMonth, toInsert.size());
                }

                current = current.plusMonths(1);

                // 每次請求間隔 1 秒，避免 TWSE 限流（實測 1 秒安全且速度快）
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }

        String summary = String.format(
                "歷史資料同步完成！標的：%s｜新增/更新：%d 筆｜略過重複：%d 筆%s",
                String.join(", ", tickers),
                totalInserted,
                totalSkipped,
                errors.isEmpty() ? "" : "｜錯誤：" + String.join("; ", errors)
        );
        System.out.println("[DataSync] " + summary);
        return summary;
    }
}

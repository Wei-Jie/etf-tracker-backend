package com.etftracker.backend.service;

import com.etftracker.backend.dto.BacktestHistoryPointDTO;
import com.etftracker.backend.dto.BacktestResultDTO;
import com.etftracker.backend.model.AssetInfo;
import com.etftracker.backend.model.DividendHistory;
import com.etftracker.backend.model.PriceHistory;
import com.etftracker.backend.repository.AssetInfoRepository;
import com.etftracker.backend.repository.DividendHistoryRepository;
import com.etftracker.backend.repository.PriceHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BacktestService {

    @Autowired
    private AssetInfoRepository assetInfoRepository;

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    @Autowired
    private DividendHistoryRepository dividendHistoryRepository;

    private enum EventType {
        DEDUCTION, DIVIDEND
    }

    private static class BacktestEvent implements Comparable<BacktestEvent> {
        LocalDate date;
        EventType type;
        BigDecimal amount; // DEDUCTION: 扣款金額, DIVIDEND: 每股股利

        public BacktestEvent(LocalDate date, EventType type, BigDecimal amount) {
            this.date = date;
            this.type = type;
            this.amount = amount;
        }

        @Override
        public int compareTo(BacktestEvent o) {
            int dateCompare = this.date.compareTo(o.date);
            if (dateCompare != 0) {
                return dateCompare;
            }
            // 同一天若有除息與扣款，除息優先（因除息日在開盤即反映除息，當天扣款買入不享有該次除息）
            if (this.type == EventType.DIVIDEND && o.type == EventType.DEDUCTION) {
                return -1;
            }
            if (this.type == EventType.DEDUCTION && o.type == EventType.DIVIDEND) {
                return 1;
            }
            return 0;
        }
    }

    public BacktestResultDTO runDcaBacktest(
            String ticker,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal investmentAmount,
            List<Integer> investmentDays,
            boolean reinvestDividends) {

        // 1. 驗證資產是否存在
        AssetInfo asset = assetInfoRepository.findByTicker(ticker)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的標的代號: " + ticker));

        // 2. 獲取特定區間歷史股價與除息資料
        List<PriceHistory> prices = priceHistoryRepository
                .findAllByAsset_TickerAndTradeDateBetweenOrderByTradeDateAsc(ticker, startDate, endDate);

        if (prices.isEmpty()) {
            throw new IllegalArgumentException("在指定區間內查無 " + ticker + " 的歷史股價資料，無法進行回測");
        }

        List<DividendHistory> dividends = dividendHistoryRepository
                .findAllByAsset_TickerAndExDividendDateBetweenOrderByExDividendDateAsc(ticker, startDate, endDate);

        // 建立股價快速檢索 Map 與有序交易日列表
        Map<LocalDate, BigDecimal> priceMap = prices.stream()
                .collect(Collectors.toMap(PriceHistory::getTradeDate, PriceHistory::getClosingPrice, (v1, v2) -> v1));
        List<LocalDate> tradeDates = prices.stream()
                .map(PriceHistory::getTradeDate)
                .sorted()
                .collect(Collectors.toList());

        // 3. 收集扣款事件
        List<BacktestEvent> events = new ArrayList<>();
        
        // 遍歷回測範圍內的每個月
        LocalDate cursor = startDate.withDayOfMonth(1);
        LocalDate endBound = endDate.withDayOfMonth(1);
        
        while (!cursor.isAfter(endBound)) {
            for (int day : investmentDays) {
                // 處理小月天數防錯（例如 2 月最大 28 或 29 天）
                int maxDay = cursor.lengthOfMonth();
                int actualDay = Math.min(day, maxDay);
                LocalDate targetDate = LocalDate.of(cursor.getYear(), cursor.getMonthValue(), actualDay);
                
                // 必須在回測區間內
                if (targetDate.isBefore(startDate) || targetDate.isAfter(endDate)) {
                    continue;
                }
                
                // 假日順延：尋找大於等於 targetDate 的第一個交易日
                LocalDate actualTradeDate = findFirstTradeDateOnOrAfter(targetDate, tradeDates, endDate);
                if (actualTradeDate != null) {
                    events.add(new BacktestEvent(actualTradeDate, EventType.DEDUCTION, investmentAmount));
                }
            }
            cursor = cursor.plusMonths(1);
        }

        // 4. 收集除息事件
        for (DividendHistory div : dividends) {
            if (div.getCashDividend() != null && div.getCashDividend().compareTo(BigDecimal.ZERO) > 0) {
                events.add(new BacktestEvent(div.getExDividendDate(), EventType.DIVIDEND, div.getCashDividend()));
            }
        }

        // 5. 事件軸排序 (排序規則為日期升序，日期相同則除息優先)
        Collections.sort(events);

        // 6. 流式模擬計算
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalShares = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal cashBalance = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal dividendEarned = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        List<BacktestHistoryPointDTO> history = new ArrayList<>();

        for (BacktestEvent event : events) {
            LocalDate eventDate = event.date;
            
            // 獲取事件當天收盤價（若當天非交易日，則尋找當天後的第一個可用交易日）
            BigDecimal price = getPriceOnOrAfter(eventDate, priceMap, tradeDates);
            if (price == null) {
                continue; // 極端情況無股價
            }

            if (event.type == EventType.DIVIDEND) {
                // 除息事件：只有此時已持有股票才計算
                if (totalShares.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal cashDiv = event.amount;
                    BigDecimal payout = totalShares.multiply(cashDiv).setScale(4, RoundingMode.HALF_UP);
                    dividendEarned = dividendEarned.add(payout);

                    if (reinvestDividends) {
                        // 股息再投資：直接以當天股價買入零股
                        BigDecimal reinvestShares = payout.divide(price, 4, RoundingMode.HALF_UP);
                        totalShares = totalShares.add(reinvestShares);
                    } else {
                        // 未再投資：累計現金餘額
                        cashBalance = cashBalance.add(payout);
                    }

                    // 記錄歷史節點 (除息日)
                    BigDecimal portfolioValue = totalShares.multiply(price).add(cashBalance).setScale(2, RoundingMode.HALF_UP);
                    history.add(new BacktestHistoryPointDTO(eventDate, totalInvested, portfolioValue, totalShares));
                }
            } else if (event.type == EventType.DEDUCTION) {
                // 扣款事件
                totalInvested = totalInvested.add(event.amount);
                BigDecimal boughtShares = event.amount.divide(price, 4, RoundingMode.HALF_UP);
                totalShares = totalShares.add(boughtShares);

                // 記錄歷史節點 (扣款日)
                BigDecimal portfolioValue = totalShares.multiply(price).add(cashBalance).setScale(2, RoundingMode.HALF_UP);
                history.add(new BacktestHistoryPointDTO(eventDate, totalInvested, portfolioValue, totalShares));
            }
        }

        // 7. 計算最終總結數據
        BigDecimal finalPrice = prices.get(prices.size() - 1).getClosingPrice();
        BigDecimal currentValue = totalShares.multiply(finalPrice).add(cashBalance).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalReturn = currentValue.subtract(totalInvested).setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal returnRate = BigDecimal.ZERO;
        if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            returnRate = totalReturn.divide(totalInvested, 4, RoundingMode.HALF_UP);
        }

        return new BacktestResultDTO(
                ticker,
                asset.getName(),
                totalInvested,
                totalShares,
                currentValue,
                totalReturn,
                returnRate,
                dividendEarned,
                history
        );
    }

    // 輔助方法：在有序交易日清單中尋找大於等於指定日期的第一個交易日
    private LocalDate findFirstTradeDateOnOrAfter(LocalDate date, List<LocalDate> tradeDates, LocalDate maxBound) {
        for (LocalDate d : tradeDates) {
            if (!d.isBefore(date)) {
                return d.isAfter(maxBound) ? null : d;
            }
        }
        return null;
    }

    // 輔助方法：尋找特定日期或該日期之後的第一個可用股價
    private BigDecimal getPriceOnOrAfter(LocalDate date, Map<LocalDate, BigDecimal> priceMap, List<LocalDate> tradeDates) {
        if (priceMap.containsKey(date)) {
            return priceMap.get(date);
        }
        for (LocalDate d : tradeDates) {
            if (!d.isBefore(date)) {
                return priceMap.get(d);
            }
        }
        return null;
    }
}

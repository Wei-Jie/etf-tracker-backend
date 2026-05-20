package com.etftracker.backend.service;

import com.etftracker.backend.dto.BacktestHistoryPointDTO;
import com.etftracker.backend.dto.BacktestResultDTO;
import com.etftracker.backend.dto.ProjectionResultDTO;
import com.etftracker.backend.dto.ProjectionResultDTO.ProjectionYearPointDTO;
import com.etftracker.backend.model.AssetInfo;
import com.etftracker.backend.model.DividendHistory;
import com.etftracker.backend.model.PriceHistory;
import com.etftracker.backend.repository.AssetInfoRepository;
import com.etftracker.backend.repository.DividendHistoryRepository;
import com.etftracker.backend.repository.PriceHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
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

    /**
     * 未來資產增值模擬（Projection）
     *
     * 以歷史回測得到的 CAGR（年化複合成長率）作為基準，模擬在持續定期定額投入的情況下，
     * 未來 N 年末的預估資產總值。計算採用逐年複利模型：
     *   - 每年期初以 CAGR 計算該年度持有資產的增長
     *   - 每年持續扣款投入，以月複利折算（近似值）
     *
     * @param ticker            標的代號
     * @param backtestStartDate 歷史回測起始日（用來計算 CAGR 的區間）
     * @param backtestEndDate   歷史回測結束日
     * @param investmentAmount  每次扣款金額
     * @param investmentDays    每月扣款日清單（用於計算每月總扣款次數）
     * @param reinvestDividends 是否將股利再投資（影響歷史 CAGR 計算）
     * @param projectionYears   模擬總年限（1-30）
     * @return ProjectionResultDTO 未來模擬結果
     */
    public ProjectionResultDTO runProjection(
            String ticker,
            LocalDate backtestStartDate,
            LocalDate backtestEndDate,
            BigDecimal investmentAmount,
            List<Integer> investmentDays,
            boolean reinvestDividends,
            int projectionYears) {

        // 1. 先執行歷史回測取得基準 CAGR
        BacktestResultDTO backtestResult = runDcaBacktest(
                ticker, backtestStartDate, backtestEndDate,
                investmentAmount, investmentDays, reinvestDividends);

        // 2. 計算歷史回測的年化複合成長率（CAGR）
        //    CAGR = (最終市值 / 投入本金)^(1/年數) - 1
        //    若投入本金為 0 或年數不足 1 年，則預設 CAGR 為 0
        long totalDays = backtestStartDate.until(backtestEndDate, java.time.temporal.ChronoUnit.DAYS);
        double years = totalDays / 365.25;

        BigDecimal cagr = BigDecimal.ZERO;
        if (backtestResult.getTotalInvested().compareTo(BigDecimal.ZERO) > 0 && years >= 1.0) {
            double finalValueDouble = backtestResult.getCurrentValue().doubleValue();
            double investedDouble = backtestResult.getTotalInvested().doubleValue();
            double cagrDouble = Math.pow(finalValueDouble / investedDouble, 1.0 / years) - 1.0;
            // 限制 CAGR 在 -50% 到 +100% 之間以防異常值
            cagrDouble = Math.max(-0.5, Math.min(1.0, cagrDouble));
            cagr = BigDecimal.valueOf(cagrDouble).setScale(4, RoundingMode.HALF_UP);
        }

        // 3. 計算每月總扣款金額（扣款日數 * 每次扣款金額）
        BigDecimal monthlyTotal = investmentAmount.multiply(BigDecimal.valueOf(investmentDays.size()));

        // 4. 逐年模擬
        //    模型：每年底 = (去年底市值 + 本年全年分攤新投入) * (1 + CAGR)
        //    以近似逐年模型計算（把當年投入的金額視為均勻投入，平均享有半年增長）
        List<ProjectionYearPointDTO> yearlyPoints = new ArrayList<>();
        BigDecimal currentPortfolioValue = backtestResult.getCurrentValue(); // 以回測末期市值為起點
        BigDecimal cumulativeInvested = backtestResult.getTotalInvested();

        BigDecimal annualInvestment = monthlyTotal.multiply(BigDecimal.valueOf(12));
        BigDecimal growthFactor = BigDecimal.ONE.add(cagr);

        for (int y = 1; y <= projectionYears; y++) {
            // 本年新增投入在增長後的終值（視為年中平均投入，享有半年複利：* sqrt(1+CAGR)）
            double halfYearGrowth = Math.sqrt(growthFactor.doubleValue());
            BigDecimal newInvestmentFV = annualInvestment.multiply(
                    BigDecimal.valueOf(halfYearGrowth)).setScale(2, RoundingMode.HALF_UP);

            // 本年底市值 = 去年底市值 * (1 + CAGR) + 本年新投入終值
            currentPortfolioValue = currentPortfolioValue.multiply(growthFactor)
                    .add(newInvestmentFV)
                    .setScale(2, RoundingMode.HALF_UP);

            cumulativeInvested = cumulativeInvested.add(annualInvestment).setScale(2, RoundingMode.HALF_UP);

            yearlyPoints.add(new ProjectionYearPointDTO(y, cumulativeInvested, currentPortfolioValue));
        }

        // 5. 取得最後一年的模擬結果
        BigDecimal projectedFinalValue = yearlyPoints.get(yearlyPoints.size() - 1).getProjectedValue();
        BigDecimal projectedTotalInvested = yearlyPoints.get(yearlyPoints.size() - 1).getCumulativeInvested();
        BigDecimal projectedTotalReturn = projectedFinalValue.subtract(projectedTotalInvested)
                .setScale(2, RoundingMode.HALF_UP);

        // 取得標的名稱
        AssetInfo asset = assetInfoRepository.findByTicker(ticker)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的標的代號: " + ticker));

        String cagrBasePeriod = backtestStartDate + " 至 " + backtestEndDate;

        return new ProjectionResultDTO(
                ticker,
                asset.getName(),
                cagrBasePeriod,
                cagr,
                backtestResult.getCurrentValue(),
                investmentAmount,
                investmentDays.size(),
                projectionYears,
                yearlyPoints,
                projectedTotalInvested,
                projectedFinalValue,
                projectedTotalReturn
        );
    }
}


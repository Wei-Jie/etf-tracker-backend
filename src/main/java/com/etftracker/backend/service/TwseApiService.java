package com.etftracker.backend.service;

import com.etftracker.backend.dto.TwseStockDayDTO;
import com.etftracker.backend.dto.TwseHistoryDayDTO;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * TWSE 台灣證券交易所 Open API 呼叫服務
 */
@Service
public class TwseApiService {

    private final RestClient restClient;

    public TwseApiService() {
        this.restClient = RestClient.create();
    }

    /**
     * 抓取 TWSE 每日所有股票/ETF 收盤價 (STOCK_DAY_ALL)
     * 每次回傳當日全市場資料
     */
    public List<TwseStockDayDTO> fetchDailyClosingPrices() {
        String url = "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL";
        try {
            return restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<TwseStockDayDTO>>() {});
        } catch (RestClientException e) {
            System.err.println("Failed to fetch data from TWSE: " + e.getMessage());
            throw new RuntimeException("Failed to fetch daily closing prices from TWSE", e);
        }
    }

    /**
     * 抓取指定股票代號在指定月份的每日成交資料
     * TWSE STOCK_DAY API：回傳該月所有交易日的收盤價
     *
     * @param stockNo  股票代號，例如 "0050"
     * @param yearMonth 年月字串，格式 "YYYYMM"，例如 "202008"
     * @return 該月每日交易資料列表，若 API 回傳無資料則回傳空列表
     */
    @SuppressWarnings("unchecked")
    public List<TwseHistoryDayDTO> fetchMonthlyHistory(String stockNo, String yearMonth) {
        // TWSE STOCK_DAY API 格式：date 參數為該月 1 日，格式 YYYYMMDD
        String dateParam = yearMonth + "01";
        String url = "https://www.twse.com.tw/exchangeReport/STOCK_DAY"
                + "?response=json&date=" + dateParam + "&stockNo=" + stockNo;
        try {
            Map<String, Object> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null) return Collections.emptyList();

            // TWSE 回傳格式：{ "stat": "OK", "data": [["日期","...",收盤價,...], ...] }
            String stat = (String) response.get("stat");
            if (!"OK".equals(stat)) return Collections.emptyList();

            List<List<String>> rawData = (List<List<String>>) response.get("data");
            if (rawData == null || rawData.isEmpty()) return Collections.emptyList();

            return rawData.stream()
                    .map(row -> new TwseHistoryDayDTO(
                            row.size() > 0 ? row.get(0) : "",   // 日期（民國年）
                            row.size() > 6 ? row.get(6) : ""    // 收盤價（第 7 欄）
                    ))
                    .toList();

        } catch (Exception e) {
            System.err.println("[TwseApiService] 抓取 " + stockNo + " " + yearMonth + " 失敗：" + e.getMessage());
            return Collections.emptyList();
        }
    }
}

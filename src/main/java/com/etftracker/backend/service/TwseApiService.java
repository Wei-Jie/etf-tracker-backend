package com.etftracker.backend.service;

import com.etftracker.backend.dto.TwseStockDayDTO;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Service
public class TwseApiService {

    private final RestClient restClient;

    public TwseApiService() {
        this.restClient = RestClient.create();
    }

    /**
     * 抓取 TWSE 每日所有股票/ETF收盤價 (STOCK_DAY_ALL)
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
}

package com.etftracker.backend.controller;

import com.etftracker.backend.service.DataSyncService;
import com.etftracker.backend.service.ReportService;
import com.etftracker.backend.repository.PriceHistoryRepository;
import com.etftracker.backend.model.PriceHistory;
import com.etftracker.backend.model.UserPortfolio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 資料同步與排程工作 Controller
 * 提供手動觸發 TWSE 資料同步與定時 Email 發報的 REST API 端點
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final DataSyncService dataSyncService;
    private final ReportService reportService;
    private final PriceHistoryRepository priceHistoryRepository;
    private final com.etftracker.backend.repository.UserPortfolioRepository portfolioRepository;

    public JobController(DataSyncService dataSyncService, 
                         ReportService reportService,
                         PriceHistoryRepository priceHistoryRepository,
                         com.etftracker.backend.repository.UserPortfolioRepository portfolioRepository) {
        this.dataSyncService = dataSyncService;
        this.reportService = reportService;
        this.priceHistoryRepository = priceHistoryRepository;
        this.portfolioRepository = portfolioRepository;
    }

    /**
     * 同步今日所有股票收盤價
     * POST /api/v1/jobs/sync-twse-data
     */
    @PostMapping("/sync-twse-data")
    public ResponseEntity<String> syncTwseData() {
        try {
            dataSyncService.syncDailyClosingPrices();
            return ResponseEntity.ok("TWSE 每日收盤價同步完成！");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("同步失敗：" + e.getMessage());
        }
    }

    /**
     * 批次同步指定標的的歷史收盤價
     * POST /api/v1/jobs/sync-history
     * Body 參數：
     *   tickers      - 標的代號列表，例如 ["0050","0056","00878"]
     *   startYearMonth - 起始年月，格式 "YYYYMM"，例如 "202008"
     *
     * 注意：此操作耗時較長（每次請求間隔 3 秒），請勿重複觸發
     */
    @PostMapping("/sync-history")
    public ResponseEntity<String> syncHistory(
            @RequestParam List<String> tickers,
            @RequestParam(defaultValue = "202008") String startYearMonth) {
        try {
            // 在背景執行緒中執行，避免 HTTP 請求超時
            String result = dataSyncService.syncHistoricalPrices(tickers, startYearMonth);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("歷史資料同步失敗：" + e.getMessage());
        }
    }

    /**
     * 定期資產損益與持倉報表寄送工作
     * POST /api/v1/jobs/send-periodic-reports
     * 受 ApiKeyFilter 防護，需於 Headers 攜帶正確的 X-API-KEY
     */
    @PostMapping("/send-periodic-reports")
    public ResponseEntity<String> sendPeriodicReports() {
        try {
            String result = reportService.sendPeriodicReports();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("定期報表發送失敗：" + e.getMessage());
        }
    }



    /**
     * 獲取今日所有同步的股價資料 (輔助診斷用)
     * GET /api/v1/jobs/check-prices
     */
    @GetMapping("/check-prices")
    public ResponseEntity<List<Map<String, Object>>> checkPrices() {
        try {
            List<UserPortfolio> list = portfolioRepository.findAll();
            List<Map<String, Object>> result = new ArrayList<>();
            for (UserPortfolio p : list) {
                Map<String, Object> map = new HashMap<>();
                map.put("portfolioId", p.getPortfolioId());
                map.put("ticker", p.getAsset().getTicker());
                map.put("quantity", p.getQuantity());
                map.put("unitPrice", p.getUnitPrice());
                map.put("fee", p.getFee());
                result.add(map);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}

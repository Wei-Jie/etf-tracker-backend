package com.etftracker.backend.controller;

import com.etftracker.backend.dto.BacktestResultDTO;
import com.etftracker.backend.service.BacktestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/backtest")
public class BacktestController {

    @Autowired
    private BacktestService backtestService;

    @GetMapping
    public ResponseEntity<?> getDcaBacktest(
            @RequestParam String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam BigDecimal investmentAmount,
            @RequestParam List<Integer> investmentDays,
            @RequestParam(defaultValue = "true") boolean reinvestDividends) {

        // 1. 業務邏輯防呆檢查
        if (ticker == null || ticker.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("標的代號 ticker 不能為空。");
        }

        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body("開始日期不可晚於結束日期。");
        }

        // 每次扣款金額限制 100~6000 元
        if (investmentAmount.compareTo(new BigDecimal("100")) < 0 || 
            investmentAmount.compareTo(new BigDecimal("6000")) > 0) {
            return ResponseEntity.badRequest().body("每次扣款金額必須介於 100 元至 6,000 元之間。");
        }

        // 扣款日天數限制 1~6 天
        if (investmentDays == null || investmentDays.isEmpty()) {
            return ResponseEntity.badRequest().body("扣款日清單不能為空。");
        }
        if (investmentDays.size() > 6) {
            return ResponseEntity.badRequest().body("每月扣款日設定最多不可超過 6 天。");
        }

        // 扣款日數值範圍限制 1~31
        for (int day : investmentDays) {
            if (day < 1 || day > 31) {
                return ResponseEntity.badRequest().body("扣款日數值必須介於 1 日至 31 日之間（輸入值: " + day + "）。");
            }
        }

        try {
            // 2. 執行回測計算
            BacktestResultDTO result = backtestService.runDcaBacktest(
                    ticker.trim().toUpperCase(),
                    startDate,
                    endDate,
                    investmentAmount,
                    investmentDays,
                    reinvestDividends
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("伺服器內部發生錯誤：" + e.getMessage());
        }
    }
}

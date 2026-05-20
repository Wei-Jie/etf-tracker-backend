package com.etftracker.backend.controller;

import com.etftracker.backend.dto.BacktestResultDTO;
import com.etftracker.backend.dto.ProjectionResultDTO;
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

/**
 * 回測與未來資產模擬 Controller
 * 提供 DCA 定期定額歷史回測與未來增值模擬的 REST API 端點
 */
@RestController
@RequestMapping("/api/v1/backtest")
public class BacktestController {

    @Autowired
    private BacktestService backtestService;

    /**
     * DCA 定期定額歷史回測 API
     * 模擬指定時間區間內，按多個扣款日定期買入某 ETF 的實際回測結果
     *
     * @param ticker            標的代號（例如 "0050"）
     * @param startDate         回測起始日（格式 yyyy-MM-dd）
     * @param endDate           回測結束日（格式 yyyy-MM-dd）
     * @param investmentAmount  每次扣款金額（100~6000 元）
     * @param investmentDays    每月扣款日清單（最多 6 個，每個值 1~31）
     * @param reinvestDividends 是否將股利再投資（預設 true）
     * @return 回測結果 BacktestResultDTO
     */
    @GetMapping
    public ResponseEntity<?> getDcaBacktest(
            @RequestParam String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam BigDecimal investmentAmount,
            @RequestParam List<Integer> investmentDays,
            @RequestParam(defaultValue = "true") boolean reinvestDividends) {

        // 業務邏輯防呆檢查
        if (ticker == null || ticker.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("標的代號 ticker 不能為空。");
        }
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body("開始日期不可晚於結束日期。");
        }
        if (investmentAmount.compareTo(new BigDecimal("100")) < 0 ||
            investmentAmount.compareTo(new BigDecimal("6000")) > 0) {
            return ResponseEntity.badRequest().body("每次扣款金額必須介於 100 元至 6,000 元之間。");
        }
        if (investmentDays == null || investmentDays.isEmpty()) {
            return ResponseEntity.badRequest().body("扣款日清單不能為空。");
        }
        if (investmentDays.size() > 6) {
            return ResponseEntity.badRequest().body("每月扣款日設定最多不可超過 6 天。");
        }
        for (int day : investmentDays) {
            if (day < 1 || day > 31) {
                return ResponseEntity.badRequest().body("扣款日數值必須介於 1 日至 31 日之間（輸入值: " + day + "）。");
            }
        }

        try {
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

    /**
     * 未來資產增值模擬 API
     * 以歷史回測得到的 CAGR 作為年化報酬率，模擬在持續定期定額投入下未來 N 年的資產規模
     *
     * @param ticker            標的代號
     * @param startDate         歷史回測起始日（作為 CAGR 計算依據）
     * @param endDate           歷史回測結束日
     * @param investmentAmount  每次扣款金額（100~6000 元）
     * @param investmentDays    每月扣款日清單（最多 6 個）
     * @param reinvestDividends 是否股利再投資（影響 CAGR 計算）
     * @param projectionYears   模擬年限（1~30 年，預設 10 年）
     * @return 未來各年度的資產增值模擬節點 ProjectionResultDTO
     */
    @GetMapping("/projection")
    public ResponseEntity<?> getProjection(
            @RequestParam String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam BigDecimal investmentAmount,
            @RequestParam List<Integer> investmentDays,
            @RequestParam(defaultValue = "true") boolean reinvestDividends,
            @RequestParam(defaultValue = "10") int projectionYears) {

        // 防呆：共用的基礎參數驗證
        if (ticker == null || ticker.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("標的代號 ticker 不能為空。");
        }
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body("開始日期不可晚於結束日期。");
        }
        if (investmentAmount.compareTo(new BigDecimal("100")) < 0 ||
            investmentAmount.compareTo(new BigDecimal("6000")) > 0) {
            return ResponseEntity.badRequest().body("每次扣款金額必須介於 100 元至 6,000 元之間。");
        }
        if (investmentDays == null || investmentDays.isEmpty()) {
            return ResponseEntity.badRequest().body("扣款日清單不能為空。");
        }
        if (investmentDays.size() > 6) {
            return ResponseEntity.badRequest().body("每月扣款日設定最多不可超過 6 天。");
        }
        for (int day : investmentDays) {
            if (day < 1 || day > 31) {
                return ResponseEntity.badRequest().body("扣款日數值必須介於 1 日至 31 日之間（輸入值: " + day + "）。");
            }
        }
        // 防呆：模擬年限限制 1~30 年
        if (projectionYears < 1 || projectionYears > 30) {
            return ResponseEntity.badRequest().body("模擬年限必須介於 1 至 30 年之間。");
        }

        try {
            ProjectionResultDTO result = backtestService.runProjection(
                    ticker.trim().toUpperCase(),
                    startDate,
                    endDate,
                    investmentAmount,
                    investmentDays,
                    reinvestDividends,
                    projectionYears
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("伺服器內部發生錯誤：" + e.getMessage());
        }
    }
}

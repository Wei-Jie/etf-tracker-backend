package com.etftracker.backend.controller;

import com.etftracker.backend.dto.AddHoldingRequestDTO;
import com.etftracker.backend.dto.HoldingRecordDTO;
import com.etftracker.backend.dto.PortfolioSummaryDTO;
import com.etftracker.backend.service.PortfolioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 投資組合管理 Controller
 * 提供個人持倉的查詢、新增、刪除與總覽摘要 REST API 端點
 */
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    @Autowired
    private PortfolioService portfolioService;

    /**
     * 查詢所有交易明細
     * 回傳每筆買入紀錄（包含 portfolioId，可供後續刪除使用）
     *
     * @return 交易明細列表 List<HoldingRecordDTO>
     */
    @GetMapping("/holdings")
    public ResponseEntity<?> getAllHoldings() {
        try {
            List<HoldingRecordDTO> records = portfolioService.getAllHoldingRecords();
            return ResponseEntity.ok(records);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("查詢持倉明細時發生錯誤：" + e.getMessage());
        }
    }

    /**
     * 新增一筆買入持倉紀錄
     *
     * @param request 請求體，包含 ticker、buyDate、quantity、unitPrice
     * @return 新增成功後的交易明細 HoldingRecordDTO（含自動產生的 portfolioId）
     */
    @PostMapping("/holdings")
    public ResponseEntity<?> addHolding(@Valid @RequestBody AddHoldingRequestDTO request) {
        try {
            HoldingRecordDTO result = portfolioService.addHolding(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("新增持倉紀錄時發生錯誤：" + e.getMessage());
        }
    }

    /**
     * 刪除指定 ID 的持倉交易紀錄
     *
     * @param portfolioId 要刪除的持倉紀錄 ID（路徑變數）
     * @return 204 No Content（成功刪除）或 404（找不到紀錄）
     */
    @DeleteMapping("/holdings/{portfolioId}")
    public ResponseEntity<?> deleteHolding(@PathVariable Long portfolioId) {
        try {
            portfolioService.deleteHolding(portfolioId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("刪除持倉紀錄時發生錯誤：" + e.getMessage());
        }
    }

    /**
     * 查詢投資組合總覽
     * 回傳持有總市值、累計本金、未實現損益與各資產持倉明細
     *
     * @return 投資組合摘要 PortfolioSummaryDTO
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getPortfolioSummary() {
        try {
            PortfolioSummaryDTO summary = portfolioService.getPortfolioSummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("查詢投資組合時發生錯誤：" + e.getMessage());
        }
    }
}

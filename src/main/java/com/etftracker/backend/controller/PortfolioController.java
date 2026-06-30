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
    public ResponseEntity<?> getAllHoldings(@RequestParam(value = "owner", defaultValue = "自己") String owner) {
        try {
            List<HoldingRecordDTO> records = portfolioService.getAllHoldingRecords(owner);
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
    public ResponseEntity<?> getPortfolioSummary(@RequestParam(value = "owner", defaultValue = "自己") String owner) {
        try {
            PortfolioSummaryDTO summary = portfolioService.getPortfolioSummary(owner);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("查詢投資組合時發生錯誤：" + e.getMessage());
        }
    }

    /**
     * 查詢目前所有不重複的持倉人員名單
     * GET /api/v1/portfolio/owners
     */
    @GetMapping("/owners")
    public ResponseEntity<?> getUniqueOwners() {
        try {
            List<String> owners = portfolioService.getUniqueOwners();
            return ResponseEntity.ok(owners);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("查詢人員清單時發生錯誤：" + e.getMessage());
        }
    }

    /**
     * 執行賣出持倉交易
     *
     * @param request 包含 ticker、sellDate (使用 buyDate 承載)、quantity、unitPrice 的請求 DTO
     * @return 200 OK (含有已實現損益記錄的實體) 或 400 (參數錯誤)
     */
    @PostMapping("/sell")
    public ResponseEntity<?> sellHolding(@jakarta.validation.Valid @RequestBody AddHoldingRequestDTO request) {
        try {
            com.etftracker.backend.model.RealizedPnL result = portfolioService.sellHolding(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("執行賣出持倉時發生錯誤：" + e.getMessage());
        }
    }

    /**
     * 查詢所有歷史已實現損益記錄
     * GET /api/v1/portfolio/realized
     */
    @GetMapping("/realized")
    public ResponseEntity<?> getRealizedHistory(@RequestParam(value = "owner", defaultValue = "自己") String owner) {
        try {
            List<com.etftracker.backend.model.RealizedPnL> history = portfolioService.getRealizedHistory(owner);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("查詢歷史已實現損益時發生錯誤：" + e.getMessage());
        }
    }

    /**
     * 編輯已存持倉交易明細
     * PUT /api/v1/portfolio/holdings/{portfolioId}
     */
    @PutMapping("/holdings/{portfolioId}")
    public ResponseEntity<?> updateHolding(
            @PathVariable Long portfolioId,
            @Valid @RequestBody AddHoldingRequestDTO request) {
        try {
            HoldingRecordDTO result = portfolioService.updateHolding(portfolioId, request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("編輯持倉紀錄時發生錯誤：" + e.getMessage());
        }
    }
}

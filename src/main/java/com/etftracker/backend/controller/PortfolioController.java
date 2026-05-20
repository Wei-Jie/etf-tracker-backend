package com.etftracker.backend.controller;

import com.etftracker.backend.dto.PortfolioSummaryDTO;
import com.etftracker.backend.service.PortfolioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 投資組合管理 Controller
 * 提供使用者投資組合摘要查詢的 REST API 端點
 */
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

    @Autowired
    private PortfolioService portfolioService;

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

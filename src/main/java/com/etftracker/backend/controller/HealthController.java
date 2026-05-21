package com.etftracker.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 系統健康檢查與防冷啟動 (Warm-up / Keep-Alive) 控制器
 * 提供輕量級免 API Key 的端點，供監控服務與前端預熱使用
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    /**
     * 健康檢查與保溫端點
     * GET /api/v1/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "ETF Tracker 後端服務已成功啟動且運作正常！");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
}

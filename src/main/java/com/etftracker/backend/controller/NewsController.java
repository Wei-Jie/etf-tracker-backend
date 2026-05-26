package com.etftracker.backend.controller;

import com.etftracker.backend.service.BriefingService;
import com.etftracker.backend.scheduler.DailyDataSyncScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 每日 AI 財經要聞與晨報 Controller
 * 提供前端加載與手動強製更新今日 AI 晨報的 REST API 端點
 */
@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private final BriefingService briefingService;
    private final DailyDataSyncScheduler dailyDataSyncScheduler;

    public NewsController(BriefingService briefingService, DailyDataSyncScheduler dailyDataSyncScheduler) {
        this.briefingService = briefingService;
        this.dailyDataSyncScheduler = dailyDataSyncScheduler;
    }

    /**
     * 獲取今日 AI 財經晨報 (免 X-API-KEY，直接白名單放行)
     * GET /api/v1/news/briefing
     */
    @GetMapping("/briefing")
    public ResponseEntity<Map<String, Object>> getDailyBriefing() {
        String briefHtml = briefingService.getTodayBriefing();
        Map<String, Object> response = new HashMap<>();
        response.put("briefingHtml", briefHtml);
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    /**
     * 手動強制重新生成今日 AI 晨報快取 (受 X-API-KEY 安全保護，非所有人皆可點擊)
     * POST /api/v1/news/briefing/refresh
     */
    @PostMapping("/briefing/refresh")
    public ResponseEntity<Map<String, Object>> forceRefreshBriefing() {
        String briefHtml = briefingService.forceRefreshTodayBriefing();
        Map<String, Object> response = new HashMap<>();
        response.put("briefingHtml", briefHtml);
        response.put("success", true);
        response.put("message", "今日 AI 晨報已強制重新生成！");
        return ResponseEntity.ok(response);
    }

    /**
     * 手動測試並發送今日 AI 電子晨報 (受 X-API-KEY 安全保護)
     * POST /api/v1/news/briefing/test-email
     */
    @PostMapping("/briefing/test-email")
    public ResponseEntity<Map<String, Object>> testSendEmail() {
        dailyDataSyncScheduler.autoGenerateAndSendDailyBriefing();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "今日 AI 財經晨報電子報已成功發送至您的 Gmail 信箱！");
        return ResponseEntity.ok(response);
    }
}

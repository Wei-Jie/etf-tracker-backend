package com.etftracker.backend.controller;

import com.etftracker.backend.service.BriefingService;
import com.etftracker.backend.scheduler.DailyDataSyncScheduler;
import com.etftracker.backend.repository.MemberProfileRepository;
import com.etftracker.backend.model.MemberProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 每日 AI 財經要聞與晨報 Controller
 * 提供前端加載與手動強製更新今日 AI 晨報的 REST API 端點
 */
@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private final BriefingService briefingService;
    private final DailyDataSyncScheduler dailyDataSyncScheduler;
    private final MemberProfileRepository memberProfileRepository;

    @Value("${spring.mail.username}")
    private String defaultReceiverEmail;

    public NewsController(BriefingService briefingService,
                          DailyDataSyncScheduler dailyDataSyncScheduler,
                          MemberProfileRepository memberProfileRepository) {
        this.briefingService = briefingService;
        this.dailyDataSyncScheduler = dailyDataSyncScheduler;
        this.memberProfileRepository = memberProfileRepository;
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
     * POST /api/v1/news/briefing/test-email?owner=xxx
     */
    @PostMapping("/briefing/test-email")
    public ResponseEntity<Map<String, Object>> testSendEmail(@RequestParam(value = "owner", required = false) String owner) {
        String targetEmail = null;
        String resolvedTargetName = "系統預設帳密信箱";

        if (owner != null && !owner.trim().isEmpty()) {
            Optional<MemberProfile> memberOpt = memberProfileRepository.findById(owner.trim());
            if (memberOpt.isPresent() && memberOpt.get().getEmail() != null && !memberOpt.get().getEmail().trim().isEmpty()) {
                targetEmail = memberOpt.get().getEmail().trim();
                resolvedTargetName = owner.trim() + " (" + targetEmail + ")";
            }
        }

        // 若沒有指定成員，或該成員未設定 Email，則 Fallback 採用系統預設發信信箱
        if (targetEmail == null) {
            targetEmail = defaultReceiverEmail;
        }

        dailyDataSyncScheduler.sendDailyBriefingTo(targetEmail);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "今日 AI 財經晨報電子報已成功發送至【" + resolvedTargetName + "】！");
        return ResponseEntity.ok(response);
    }
}

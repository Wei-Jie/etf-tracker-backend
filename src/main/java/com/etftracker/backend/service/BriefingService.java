package com.etftracker.backend.service;

import com.etftracker.backend.model.DailyBriefing;
import com.etftracker.backend.repository.DailyBriefingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 每日 AI 財經要聞與晨報業務服務
 * 負責快取邏輯管理：查詢今日快取、觸發爬蟲與 AI 生成、並將產出快取寫入資料庫
 */
@Service
public class BriefingService {

    private final NewsScraperService newsScraperService;
    private final GeminiApiService geminiApiService;
    private final DailyBriefingRepository dailyBriefingRepository;

    public BriefingService(NewsScraperService newsScraperService,
                           GeminiApiService geminiApiService,
                           DailyBriefingRepository dailyBriefingRepository) {
        this.newsScraperService = newsScraperService;
        this.geminiApiService = geminiApiService;
        this.dailyBriefingRepository = dailyBriefingRepository;
    }

    /**
     * 獲取今日 AI 財經晨報 (優先從資料庫快取讀取)
     *
     * @return 精美 HTML 晨報內容
     */
    @Transactional
    public String getTodayBriefing() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Taipei"));
        Optional<DailyBriefing> cache = dailyBriefingRepository.findByBriefingDate(today);

        // 1. 若資料庫今天已經有生成過快取，則直接以微秒級速度回傳，守護 API 額度與效能！
        if (cache.isPresent()) {
            System.out.println("[Briefing] 成功命中今日 AI 晨報快取，直接回傳數據。");
            return cache.get().getContentHtml();
        }

        // 2. 若今天尚未快取，則立刻發起一體化生成
        System.out.println("[Briefing] 今日無快取，啟動新聞爬取與 Gemini AI 晨報建置...");
        String rawNews = newsScraperService.scrapeDailyFinanceNews();
        String briefHtml = geminiApiService.generateBriefingHtml(rawNews);

        // 3. 只有在 API Key 配置好且正常產出時，才將產出寫入快取資料庫
        if (briefHtml != null && !briefHtml.contains("未配置 GEMINI_API_KEY") && !briefHtml.contains("召喚 AI 失敗")) {
            DailyBriefing briefing = new DailyBriefing(today, briefHtml);
            dailyBriefingRepository.save(briefing);
            System.out.println("[Briefing] 今日 AI 晨報已成功寫入 Supabase 資料庫快取！");
        }

        return briefHtml;
    }

    /**
     * 手動強制造訪並更新今日快取 (用於前端的「即時刷新」按鈕)
     *
     * @return 最新重新生成的精美 HTML 晨報
     */
    @Transactional
    public String forceRefreshTodayBriefing() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Taipei"));
        Optional<DailyBriefing> cache = dailyBriefingRepository.findByBriefingDate(today);

        // 1. 先把今日已存在的快取徹底刪除
        if (cache.isPresent()) {
            dailyBriefingRepository.delete(cache.get());
            dailyBriefingRepository.flush();
            System.out.println("[Briefing] 已手動清除今日 AI 晨報舊快取。");
        }

        // 2. 重新拉取並生成
        String rawNews = newsScraperService.scrapeDailyFinanceNews();
        String briefHtml = geminiApiService.generateBriefingHtml(rawNews);

        // 3. 寫入新快取
        if (briefHtml != null && !briefHtml.contains("未配置 GEMINI_API_KEY") && !briefHtml.contains("召喚 AI 失敗")) {
            DailyBriefing briefing = new DailyBriefing(today, briefHtml);
            dailyBriefingRepository.save(briefing);
            System.out.println("[Briefing] 手動更新之 AI 晨報已寫入資料庫快取！");
        }

        return briefHtml;
    }
}

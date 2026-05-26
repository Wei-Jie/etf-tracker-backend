package com.etftracker.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini 3.5 Flash 大型語言模型對接服務
 * 負責將今日新聞資料傳送至 Gemini API 並取得精準排版好的 HTML 晨報摘要
 */
@Service
public class GeminiApiService {

    private final RestClient restClient;

    @Value("${app.gemini-api-key:}")
    private String geminiApiKey;

    @Value("${app.gemini-model:gemini-1.5-flash}")
    private String geminiModel;

    public GeminiApiService() {
        this.restClient = RestClient.create();
    }

    /**
     * 呼叫 Google Gemini API 對新聞進行台灣理財專家視角的 HTML 格式摘要
     *
     * @param rawNews 爬取的新聞大字串
     * @return 適合直接渲染在前端的精美 HTML 摘要
     */
    public String generateBriefingHtml(String rawNews) {
        // 若使用者尚未配置 API Key，則回傳提醒配置的精美說明
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
            return "<div class='briefing-notice'>⚠️ <strong>系統提示</strong>：目前尚未配置 <code>GEMINI_API_KEY</code> 環境變數，AI 財經助理處於離線狀態。<br/>請至您的 GCP Cloud Run 環境變數或本地開發環境配置您的 Gemini API Key，即可立刻解鎖極具技術含金量的 3.5 代 AI 自動摘要功能！</div>";
        }

        // 使用備份與防錯機制的呼叫方法
        try {
            return callGeminiModel(rawNews, geminiModel);
        } catch (Exception e) {
            System.err.printf("[GeminiAPI] 使用選定模型 %s 呼叫失敗: %s。嘗試自動 Fallback 至穩定版 gemini-1.5-flash%n", geminiModel, e.getMessage());
            try {
                // 自動 Fallback 確保系統 100% 正常
                return callGeminiModel(rawNews, "gemini-1.5-flash");
            } catch (Exception ex) {
                ex.printStackTrace();
                return "<div class='alert alert-error'>⚠️ 召喚 AI 財經助理失敗，請確認您的 <code>GEMINI_API_KEY</code> 是否正確或是否已超出限流額度。</div>";
            }
        }
    }

    // 核心的 API POST 呼叫方法
    @SuppressWarnings("unchecked")
    private String callGeminiModel(String rawNews, String model) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + geminiApiKey;

        // 1. 精心調教的理財專屬 Prompt
        String prompt = "請以「台灣頂尖理財專家與資深財經分析師」的語氣，為我從以下最新新聞中精準整理出今天『理財必讀的 3 大黃金焦點』。\n"
                + "請嚴格遵守以下輸出與格式約束，這將直接渲染在我的 React 前端儀表板中：\n"
                + "1. 必須以乾淨的 HTML 片段輸出，最外層以 <div class='brief-wrapper'> 包裹，絕不能包含 <html>, <body>, ```html 等任何 Markdown 或外層標籤，直接給我 HTML 片段。\n"
                + "2. 針對每個焦點，使用一個 <div class='brief-item'> 包裹，內含 <h3> 焦點標題（加個小 icon，例如 🎯, 📈, 💡）與 <p> 深入淺出的專家分析。\n"
                + "3. 分析中，請具體說明這些新聞對於台灣股市以及熱門 ETF（例如 0050、0056、00878）的短中長期影響，字句必須精確、護眼、語氣專業溫和且符合台灣本土理財術語。\n"
                + "4. 在最底部加上一個 <div class='brief-summary'> 總結今日的整體操盤心法。\n\n"
                + "【以下為今日 Yahoo 股市新聞 raw 資料】\n" + rawNews;

        // 2. 封裝符合 Google Gemini API 的 JSON 請求結構
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", prompt)
                                )
                        )
                )
        );

        // 3. 發送 POST 請求
        Map<String, Object> response = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null) {
            throw new RuntimeException("Gemini API 回傳空內容");
        }

        // 4. 解析 API 回應，提取 text 內文
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("Gemini 回應中無 candidates 節點");
        }

        Map<String, Object> candidate = candidates.get(0);
        Map<String, Object> content = (Map<String, Object>) candidate.get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        
        String resultText = (String) parts.get(0).get("text");
        
        // 清理可能被大模型多吐出來的 Markdown html 圍欄包裹
        if (resultText != null) {
            resultText = resultText.replace("```html", "")
                                   .replace("```", "")
                                   .trim();
        }

        return resultText;
    }
}

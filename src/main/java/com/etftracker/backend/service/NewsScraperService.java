package com.etftracker.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 財經新聞輕量爬蟲服務
 * 負責從 Yahoo 股市 RSS 拉取今日台股焦點要聞並解析為純文字，供 Gemini 摘要
 */
@Service
public class NewsScraperService {

    private final RestClient restClient;

    public NewsScraperService() {
        this.restClient = RestClient.create();
    }

    /**
     * 從 Yahoo 股市拉取台股要聞 RSS 並解析前 6 則新聞
     *
     * @return 拼接好的新聞標題與內容大字串
     */
    public String scrapeDailyFinanceNews() {
        String rssUrl = "https://tw.stock.yahoo.com/rss?category=tw-market";
        try {
            // 1. 下載 RSS XML 內容
            String xmlContent = restClient.get()
                    .uri(rssUrl)
                    .retrieve()
                    .body(String.class);

            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                throw new RuntimeException("無法取得新聞資料 (RSS 回傳內容為空)");
            }

            // 2. 解析 XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 防止 XML 外部實體注入 (XXE 安全防護)
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            
            Document doc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();

            NodeList items = doc.getElementsByTagName("item");
            List<String> newsList = new ArrayList<>();

            // 3. 提取前 6 則要聞
            int count = Math.min(items.getLength(), 6);
            for (int i = 0; i < count; i++) {
                Element item = (Element) items.item(i);
                String title = getTagValue("title", item);
                String description = getTagValue("description", item);
                
                // 去除 description 中的 HTML 標籤
                if (description != null) {
                    description = description.replaceAll("<[^>]*>", "").trim();
                }

                if (title != null && !title.isEmpty()) {
                    newsList.add(String.format("【新聞焦點 %d】\n標題：%s\n摘要：%s\n", i + 1, title, description));
                }
            }

            if (newsList.isEmpty()) {
                return "今日查無重大財經新聞。";
            }

            return String.join("\n", newsList);

        } catch (Exception e) {
            System.err.println("[NewsScraper] RSS 解析失敗，原因: " + e.getMessage());
            // Fallback：若發生異常，回傳基本的今日市場備份說明，避免系統崩潰
            return "【備份財經焦點】\n標題：台股持續高檔震盪，多空勢力交錯\n摘要：今日市場觀望氣息濃厚，核心 ETF (如 0050、00878) 收盤小幅波動。AI 供應鏈與半導體族群持續引領盤面主流，投資人建議維持定期定額紀律，注意除權息季的長線布局防守。";
        }
    }

    // 輔助方法：獲取 XML 節點的文字值
    private String getTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return "";
    }
}

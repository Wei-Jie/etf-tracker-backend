package com.etftracker.backend.service;

import com.etftracker.backend.dto.PortfolioSummaryDTO;
import com.etftracker.backend.dto.PortfolioSummaryDTO.PortfolioHoldingDTO;
import com.etftracker.backend.model.MemberProfile;
import com.etftracker.backend.repository.MemberProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 定期報表派送業務邏輯服務
 * 撈取所有啟用報表的成員、獲取其持倉損益數據、生成精美的 HTML 報表並呼叫 EmailService 寄出
 */
@Service
public class ReportService {

    @Autowired
    private MemberProfileRepository memberProfileRepository;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private EmailService emailService;

    // 定義格式化工具，新台幣無小數點
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("NT$ #,##0");
    private static final DecimalFormat SHARES_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("+0.00%;-0.00%");

    /**
     * 執行定期發報工作
     * 撈取資料庫中所有有訂閱定期報表的成員，並寄送損益明細
     *
     * @return 執行結果字串，用以反饋給定時呼叫者
     */
    public String sendPeriodicReports() {
        List<MemberProfile> subscribers = memberProfileRepository.findAllByReportEnabledTrue();
        if (subscribers.isEmpty()) {
            return "目前無任何已啟用訂閱的成員。";
        }

        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;
        StringBuilder logBuilder = new StringBuilder();

        logBuilder.append("定期發信工作開始：\n");

        for (MemberProfile member : subscribers) {
            String name = member.getMemberName();
            String email = member.getEmail();

            try {
                // 1. 撈取該成員的投資組合損益
                PortfolioSummaryDTO summary = portfolioService.getPortfolioSummary(name);

                // 2. 防呆：若該成員無任何持倉明細，跳過不發送以防打擾使用者
                if (summary == null || summary.getHoldings() == null || summary.getHoldings().isEmpty()) {
                    logBuilder.append(String.format("- 成員【%s】因無持倉數據而跳過\n", name));
                    skipCount++;
                    continue;
                }

                // 3. 渲染拼接符合莫蘭迪設計色調的 HTML 報表
                String htmlContent = buildHtmlReport(name, summary);

                // 4. 發送郵件
                String subject = String.format("【ETF Tracker】%s 定期資產損益與持倉報告", name);
                emailService.sendHtmlEmail(email, subject, htmlContent);

                logBuilder.append(String.format("- 成員【%s】(%s) 郵件發送成功\n", name, email));
                successCount++;

            } catch (Exception e) {
                e.printStackTrace();
                logBuilder.append(String.format("- 成員【%s】郵件發送失敗：%s\n", name, e.getMessage()));
                failCount++;
            }
        }

        String summaryResult = String.format("發送工作結束。共成功: %d 筆, 跳過: %d 筆, 失敗: %d 筆", 
                successCount, skipCount, failCount);
        logBuilder.append(summaryResult);
        
        return logBuilder.toString();
    }

    /**
     * 建立符合莫蘭迪暖調護眼設計系統的精美 HTML 郵件內容
     * 為確保在各大郵件客戶端 (Gmail, Outlook, Yahoo) 完美渲染，採用經典的 table 表格佈局
     */
    private String buildHtmlReport(String memberName, PortfolioSummaryDTO summary) {
        String nowStr = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Taipei"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")) + " (台灣時間)";
        
        // 格式化總額度
        String investedStr = CURRENCY_FORMAT.format(summary.getTotalInvested());
        String marketValueStr = CURRENCY_FORMAT.format(summary.getTotalMarketValue());
        
        BigDecimal pnl = summary.getUnrealizedPnL();
        String pnlStr = (pnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + CURRENCY_FORMAT.format(pnl);
        String pnlClass = pnl.compareTo(BigDecimal.ZERO) >= 0 ? "profit" : "loss";
        
        String returnRateStr = PERCENT_FORMAT.format(summary.getUnrealizedReturnRate());

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        sb.append("<style>");
        sb.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #F8FAFC; color: #1E293B; margin: 0; padding: 20px; -webkit-font-smoothing: antialiased; }");
        sb.append(".container { max-width: 600px; margin: 0 auto; background: #ffffff; border: 1px solid rgba(148, 163, 184, 0.12); border-radius: 16px; padding: 28px; box-shadow: 0 4px 20px rgba(148, 163, 184, 0.04); }");
        sb.append(".header { border-bottom: 1px solid rgba(148, 163, 184, 0.12); padding-bottom: 18px; margin-bottom: 20px; }");
        sb.append(".title { font-size: 22px; font-weight: 800; color: #B27B5E; margin: 0 0 6px 0; }");
        sb.append(".subtitle { font-size: 13px; color: #7A6E67; margin: 0; }");
        sb.append(".metric-table { width: 100%; border-collapse: separate; border-spacing: 12px; margin-bottom: 20px; }");
        sb.append(".metric-card { background: #FAF7F5; border: 1px solid rgba(178, 123, 94, 0.12); border-radius: 12px; padding: 14px 18px; }");
        sb.append(".metric-label { font-size: 11px; font-weight: 600; color: #7A6E67; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 4px; }");
        sb.append(".metric-value { font-size: 20px; font-weight: 700; color: #1E293B; }");
        sb.append(".profit { color: #0D9488 !important; }");
        sb.append(".loss { color: #E11D48 !important; }");
        sb.append(".data-table { width: 100%; border-collapse: collapse; margin-top: 15px; }");
        sb.append(".data-table th { font-size: 11px; font-weight: 600; color: #475569; text-transform: uppercase; letter-spacing: 0.05em; border-bottom: 1px solid rgba(148, 163, 184, 0.15); padding: 10px 8px; text-align: left; }");
        sb.append(".data-table td { font-size: 13px; padding: 12px 8px; border-bottom: 1px solid rgba(148, 163, 184, 0.05); color: #1E293B; }");
        sb.append(".ticker { font-weight: 700; color: #1E293B; }");
        sb.append(".asset-name { font-size: 11px; color: #94A3B8; }");
        sb.append(".text-right { text-align: right !important; }");
        sb.append(".footer { margin-top: 32px; border-top: 1px solid rgba(148, 163, 184, 0.12); padding-top: 18px; font-size: 11px; color: #94A3B8; text-align: center; }");
        sb.append("</style></head><body>");

        sb.append("<div class=\"container\">");
        
        // Header
        sb.append("<div class=\"header\">");
        sb.append(String.format("<h1 class=\"title\">📊 %s 定期資產損益報告</h1>", memberName));
        sb.append(String.format("<p class=\"subtitle\">系統已於 %s 完成最新持倉市值與損益結算，以下為您目前的投資明細：</p>", nowStr));
        sb.append("</div>");

        // Metrics Grid (用 2x2 table 排版，保證郵件不破版)
        sb.append("<table class=\"metric-table\">");
        
        // Row 1
        sb.append("<tr>");
        sb.append(String.format("<td><div class=\"metric-card\"><div class=\"metric-label\">💰 累計投入本金</div><div class=\"metric-value\">%s</div></div></td>", investedStr));
        sb.append(String.format("<td><div class=\"metric-card\"><div class=\"metric-label\">📈 當前總市值</div><div class=\"metric-value\">%s</div></div></td>", marketValueStr));
        sb.append("</tr>");
        
        // Row 2
        sb.append("<tr>");
        sb.append(String.format("<td><div class=\"metric-card\"><div class=\"metric-label\">💹 未實現損益</div><div class=\"metric-value %s\">%s</div></div></td>", pnlClass, pnlStr));
        sb.append(String.format("<td><div class=\"metric-card\"><div class=\"metric-label\">📉 複利報酬率</div><div class=\"metric-value %s\">%s</div></div></td>", pnlClass, returnRateStr));
        sb.append("</tr>");
        sb.append("</table>");

        // Table Header
        sb.append("<div><p style=\"font-size: 14px; font-weight: 700; color: #7A6E67; margin: 15px 0 5px 8px;\">💼 各資產持倉明細</p></div>");
        sb.append("<table class=\"data-table\">");
        sb.append("<thead><tr><th>標的</th><th class=\"text-right\">持有股數</th><th class=\"text-right\">當前市值</th><th class=\"text-right\">損益</th><th class=\"text-right\">報酬率</th></tr></thead>");
        sb.append("<tbody>");

        // Table Rows
        for (PortfolioHoldingDTO h : summary.getHoldings()) {
            BigDecimal hpnl = h.getUnrealizedPnL();
            String hpnlStr = (hpnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + CURRENCY_FORMAT.format(hpnl);
            String hpnlClass = hpnl.compareTo(BigDecimal.ZERO) >= 0 ? "profit" : "loss";
            String hReturnStr = PERCENT_FORMAT.format(h.getUnrealizedReturnRate());
            String hSharesStr = SHARES_FORMAT.format(h.getTotalShares());
            String hMarketValueStr = CURRENCY_FORMAT.format(h.getMarketValue());

            sb.append("<tr>");
            sb.append(String.format("<td><div class=\"ticker\">%s</div><div class=\"asset-name\">%s</div></td>", h.getTicker(), h.getAssetName()));
            sb.append(String.format("<td class=\"text-right\">%s</td>", hSharesStr));
            sb.append(String.format("<td class=\"text-right\">%s</td>", hMarketValueStr));
            sb.append(String.format("<td class=\"text-right %s\">%s</td>", hpnlClass, hpnlStr));
            sb.append(String.format("<td class=\"text-right %s\">%s</td>", hpnlClass, hReturnStr));
            sb.append("</tr>");
        }

        sb.append("</tbody>");
        sb.append("</table>");

        // Footer
        sb.append("<div class=\"footer\">");
        sb.append("🔒 本報表經由理財系統安全鎖與 Google SSL 加密通道安全派送，請妥善保管本信件。<br>");
        sb.append("© 2026 ETF Tracker 理財分析系統. All rights reserved.");
        sb.append("</div>");

        sb.append("</div>");
        sb.append("</body></html>");

        return sb.toString();
    }
}

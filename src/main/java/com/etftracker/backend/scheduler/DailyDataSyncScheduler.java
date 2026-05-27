package com.etftracker.backend.scheduler;

import com.etftracker.backend.service.DataSyncService;
import com.etftracker.backend.service.BriefingService;
import com.etftracker.backend.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 每日資料自動同步與 AI 財經晨報排程器
 * 1. 每日早晨 08:30 自動生成並發送 AI 3.5 Flash 財經晨報至使用者信箱
 * 2. 每日下午 15:30 自動同步當日最新台股/ETF 收盤價
 */
@Component
public class DailyDataSyncScheduler {

    private final DataSyncService dataSyncService;
    private final BriefingService briefingService;
    private final EmailService emailService;

    @Value("${app.receiver-email:jeff.wang0211@gmail.com}")
    private String receiverEmail;

    public DailyDataSyncScheduler(DataSyncService dataSyncService,
                                  BriefingService briefingService,
                                  EmailService emailService) {
        this.dataSyncService = dataSyncService;
        this.briefingService = briefingService;
        this.emailService = emailService;
    }

    /**
     * 每日自動同步排程
     * 觸發時間：每週一至週五下午 15:30 (台灣時間 Asia/Taipei)
     * 此時股市已收盤，且證交所已結算並公布當日收盤數據。
     */
    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Taipei")
    public void autoSyncDailyClosingPrices() {
        System.out.println("[Scheduler] 開始執行每日收盤價自動同步排程，當前時間: " + LocalDateTime.now());
        try {
            dataSyncService.syncDailyClosingPrices();
            System.out.println("[Scheduler] 每日收盤價自動同步成功！");
        } catch (Exception e) {
            System.err.println("[Scheduler] 每日收盤價自動同步失敗，原因: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 每日早晨 AI 財經晨報自動發送排程
     * 觸發時間：每週一至週五早晨 08:30 (台灣時間 Asia/Taipei，開盤前半小時)
     * 自動爬取 Yahoo 股市要聞、呼叫 Gemini 3.5 Flash 生成摘要快取，並透過 Gmail 寄送 HTML 精美晨報。
     */
    @Scheduled(cron = "0 30 8 * * MON-FRI", zone = "Asia/Taipei")
    public void autoGenerateAndSendDailyBriefing() {
        System.out.println("[Scheduler] 開始執行今日 AI 財經要聞晨報排程，當前時間: " + LocalDateTime.now());
        sendDailyBriefingTo(receiverEmail);
    }

    /**
     * 指定收件人發送今日 AI 電子晨報
     *
     * @param targetEmail 收件者信箱
     */
    public void sendDailyBriefingTo(String targetEmail) {
        System.out.println("[Scheduler] 觸發 AI 財經要聞晨報發送，目標信箱: " + targetEmail);
        try {
            // 1. 自動觸發今日新聞爬取、Gemini AI 3.5 摘要與資料庫快取
            String briefHtml = briefingService.getTodayBriefing();

            // 2. 封裝精美護眼的 HTML 郵件格式 (結合莫蘭迪色調與卡片陰影)
            String mailContent = "<html><body style='font-family: sans-serif; background: #faf7f5; padding: 20px; color: #3c2f2f;'>"
                    + "<div style='max-width: 600px; margin: 0 auto; background: #ffffff; padding: 30px; border-radius: 16px; box-shadow: 0 4px 20px rgba(0,0,0,0.05); border: 1px solid rgba(178,123,94,0.1);'>"
                    + "<div style='text-align: center; border-bottom: 2px solid #b27b5e; padding-bottom: 15px; margin-bottom: 20px;'>"
                    + "<h2 style='color: #b27b5e; margin: 0;'>📈 ETF Tracker 理財晨報</h2>"
                    + "<p style='color: #7a6e67; margin: 5px 0 0 0; font-size: 13px;'>您的專屬 AI 3.5 Flash 財經晨報助理</p>"
                    + "</div>"
                    + "<style>"
                    + ".brief-wrapper { display: flex; flex-direction: column; gap: 20px; }"
                    + ".brief-item { background: #fdfcfb; border: 1px solid #f4ede8; border-radius: 12px; padding: 15px; margin-bottom: 15px; }"
                    + ".brief-item h3 { color: #b27b5e; margin-top: 0; font-size: 16px; border-left: 4px solid #b27b5e; padding-left: 10px; }"
                    + ".brief-item p { color: #475569; font-size: 14px; line-height: 1.6; margin-bottom: 0; }"
                    + ".brief-summary { background: rgba(178,123,94,0.08); border-radius: 12px; padding: 15px; font-size: 13px; color: #3c2f2f; line-height: 1.6; border-left: 4px solid #8d6b58; margin-top: 20px; }"
                    + "</style>"
                    + briefHtml
                    + "<div style='margin-top: 30px; border-top: 1px solid #f4ede8; padding-top: 15px; font-size: 11px; color: #a3958c; text-align: center;'>"
                    + "🔒 本郵件由 ETF Tracker AI 系統自動定時發送，保障您的理財數據與隱私安全。"
                    + "</div>"
                    + "</div>"
                    + "</body></html>";

            // 3. 呼叫 EmailService 寄出信件
            emailService.sendHtmlEmail(targetEmail, "📈 ETF Tracker 每日 AI 財經要聞晨報 ☕", mailContent);
            System.out.println("[Scheduler] 今日 AI 財經要聞晨報已成功發送至您的信箱: " + targetEmail);

        } catch (Exception e) {
            System.err.println("[Scheduler] 今日 AI 財經要聞晨報發送失敗，目標: " + targetEmail + "，原因: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("發送電子晨報郵件失敗，原因: " + e.getMessage());
        }
    }
}

package com.etftracker.backend.scheduler;

import com.etftracker.backend.service.DataSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 每日資料自動同步排程器
 * 每週一至週五股市收盤後，自動從證交所 (TWSE) 同步當日最新收盤價
 */
@Component
public class DailyDataSyncScheduler {

    private final DataSyncService dataSyncService;

    public DailyDataSyncScheduler(DataSyncService dataSyncService) {
        this.dataSyncService = dataSyncService;
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
}

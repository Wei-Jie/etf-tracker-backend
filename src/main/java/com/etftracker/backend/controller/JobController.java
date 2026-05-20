package com.etftracker.backend.controller;

import com.etftracker.backend.service.DataSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final DataSyncService dataSyncService;

    public JobController(DataSyncService dataSyncService) {
        this.dataSyncService = dataSyncService;
    }

    @PostMapping("/sync-twse-data")
    public ResponseEntity<String> syncTwseData() {
        try {
            dataSyncService.syncDailyClosingPrices();
            return ResponseEntity.ok("TWSE data synced successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to sync data: " + e.getMessage());
        }
    }
}

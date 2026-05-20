package com.etftracker.backend.controller;

import com.etftracker.backend.dto.PriceHistoryDTO;
import com.etftracker.backend.model.AssetInfo;
import com.etftracker.backend.service.EtfService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/etfs")
public class EtfController {

    private final EtfService etfService;

    public EtfController(EtfService etfService) {
        this.etfService = etfService;
    }

    /**
     * 獲取所有已同步的 ETF 與股票清單
     * GET /api/v1/etfs
     */
    @GetMapping
    public ResponseEntity<List<AssetInfo>> getAllAssets() {
        List<AssetInfo> assets = etfService.getAllAssets();
        return ResponseEntity.ok(assets);
    }

    /**
     * 獲取特定 Ticker 的歷史價格
     * GET /api/v1/etfs/{ticker}/prices
     */
    @GetMapping("/{ticker}/prices")
    public ResponseEntity<List<PriceHistoryDTO>> getPriceHistory(@PathVariable String ticker) {
        List<PriceHistoryDTO> historyList = etfService.getPriceHistory(ticker)
                .stream()
                .map(ph -> new PriceHistoryDTO(ph.getTradeDate(), ph.getClosingPrice()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(historyList);
    }
}

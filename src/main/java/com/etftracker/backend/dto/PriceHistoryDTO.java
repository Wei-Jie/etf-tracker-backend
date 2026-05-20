package com.etftracker.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 用於回傳前端歷史價格的 DTO，避免 Lazy loading 載入問題與多餘的關聯欄位
 */
public record PriceHistoryDTO(LocalDate tradeDate, BigDecimal closingPrice) {}

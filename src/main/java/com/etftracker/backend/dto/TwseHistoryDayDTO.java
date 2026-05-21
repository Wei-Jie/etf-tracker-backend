package com.etftracker.backend.dto;

/**
 * TWSE STOCK_DAY API 單日歷史資料 DTO
 * 對應 API 回傳的 data 陣列中每一列
 */
public record TwseHistoryDayDTO(
        /** 日期（民國年格式，例如 "113/05/21"） */
        String dateRoc,

        /** 收盤價字串（可能含逗號，例如 "183.50"） */
        String closingPrice
) {}

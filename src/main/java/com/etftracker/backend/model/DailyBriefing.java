package com.etftracker.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日 AI 財經要聞與晨報快取實體
 * 用於將 Gemini 產出的每日新聞摘要快取於資料庫中，防範重複呼叫並加快前端響應
 */
@Entity
@Table(name = "daily_briefing", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"briefing_date"})
})
public class DailyBriefing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "briefing_id")
    private Long briefingId;

    @Column(name = "briefing_date", nullable = false)
    private LocalDate briefingDate;

    @Column(name = "content_html", nullable = false, columnDefinition = "TEXT")
    private String contentHtml;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Hibernate 需要的無參建構子
    public DailyBriefing() {
    }

    public DailyBriefing(LocalDate briefingDate, String contentHtml) {
        this.briefingDate = briefingDate;
        this.contentHtml = contentHtml;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getBriefingId() {
        return briefingId;
    }

    public void setBriefingId(Long briefingId) {
        this.briefingId = briefingId;
    }

    public LocalDate getBriefingDate() {
        return briefingDate;
    }

    public void setBriefingDate(LocalDate briefingDate) {
        this.briefingDate = briefingDate;
    }

    public String getContentHtml() {
        return contentHtml;
    }

    public void setContentHtml(String contentHtml) {
        this.contentHtml = contentHtml;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

package com.etftracker.backend.repository;

import com.etftracker.backend.model.DailyBriefing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 每日 AI 財經要聞快取 Repository
 */
@Repository
public interface DailyBriefingRepository extends JpaRepository<DailyBriefing, Long> {

    /**
     * 依指定日期查詢當日已存在的 AI 晨報快取
     *
     * @param date 查詢日期
     * @return 每日晨報實體
     */
    Optional<DailyBriefing> findByBriefingDate(LocalDate date);
}

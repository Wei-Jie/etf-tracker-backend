package com.etftracker.backend.repository;

import com.etftracker.backend.model.DividendHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DividendHistoryRepository extends JpaRepository<DividendHistory, Long> {
    Optional<DividendHistory> findByAsset_AssetIdAndExDividendDate(Long assetId, LocalDate exDividendDate);
}

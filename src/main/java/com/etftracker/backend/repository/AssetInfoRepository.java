package com.etftracker.backend.repository;

import com.etftracker.backend.model.AssetInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetInfoRepository extends JpaRepository<AssetInfo, Long> {
    Optional<AssetInfo> findByTicker(String ticker);
}

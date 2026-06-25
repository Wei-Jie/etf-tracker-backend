package com.etftracker.backend.repository;

import com.etftracker.backend.model.RealizedPnL;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 已實現損益 Repository
 * 提供已實現損益記錄的資料存取操作
 */
@Repository
public interface RealizedPnLRepository extends JpaRepository<RealizedPnL, Long> {

    /**
     * 查詢指定成員的所有已實現損益記錄，依賣出日期降序排列
     */
    List<RealizedPnL> findAllByOwnerOrderBySellDateDesc(String owner);

    /**
     * 查詢指定成員在特定日期區間內的所有已實現損益記錄，依賣出日期降序排列
     */
    List<RealizedPnL> findAllByOwnerAndSellDateBetweenOrderBySellDateDesc(
            String owner, LocalDate startDate, LocalDate endDate);

    /**
     * 統計指定成員累計的已實現損益總額
     */
    @Query("SELECT COALESCE(SUM(r.realizedPnL), 0) FROM RealizedPnL r WHERE r.owner = :owner")
    BigDecimal sumRealizedPnLByOwner(@Param("owner") String owner);
}

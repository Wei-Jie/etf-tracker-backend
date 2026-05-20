package com.etftracker.backend.repository;

import com.etftracker.backend.model.UserPortfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 使用者投資組合 Repository
 * 提供投資組合交易明細的資料存取操作
 */
@Repository
public interface UserPortfolioRepository extends JpaRepository<UserPortfolio, Long> {

    /**
     * 查詢指定資產的所有持有記錄，依買入日期升序排列
     *
     * @param ticker 資產代號 (例如 "0050")
     * @return 符合的持有記錄列表
     */
    List<UserPortfolio> findAllByAsset_TickerOrderByBuyDateAsc(String ticker);

    /**
     * 查詢所有持有記錄，依資產代號再按買入日期排列
     *
     * @return 所有投資組合持有記錄
     */
    List<UserPortfolio> findAllByOrderByAsset_TickerAscBuyDateAsc();
}

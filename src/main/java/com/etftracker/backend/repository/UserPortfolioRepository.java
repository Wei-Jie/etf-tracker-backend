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

    /**
     * 依擁有人查詢所有持倉，依資產代號與買入日期排序
     */
    List<UserPortfolio> findAllByOwnerOrderByAsset_TickerAscBuyDateAsc(String owner);

    /**
     * 依擁有人與資產代號查詢持倉，依買入日期排序
     */
    List<UserPortfolio> findAllByOwnerAndAsset_TickerOrderByBuyDateAsc(String owner, String ticker);

    /**
     * 查詢目前資料庫中所有不重複的擁有人清單
     */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p.owner FROM UserPortfolio p")
    List<String> findDistinctOwners();
}

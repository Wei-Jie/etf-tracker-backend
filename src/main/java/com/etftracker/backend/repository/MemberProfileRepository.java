package com.etftracker.backend.repository;

import com.etftracker.backend.model.MemberProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 成員 Email Profile 的資料庫存取 Repository
 */
@Repository
public interface MemberProfileRepository extends JpaRepository<MemberProfile, String> {
    
    /**
     * 撈取所有已啟用定期發信報表的成員
     */
    List<MemberProfile> findAllByReportEnabledTrue();
}

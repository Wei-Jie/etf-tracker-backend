package com.etftracker.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.OffsetDateTime;

/**
 * 成員 Email 訂閱報表 Profile 實體
 * 對應資料庫中的 MEMBER_PROFILE 表
 */
@Entity
@Table(name = "MEMBER_PROFILE")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberProfile {

    @Id
    @Column(name = "member_name", nullable = false, length = 100)
    private String memberName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "report_enabled", nullable = false)
    private Boolean reportEnabled = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}

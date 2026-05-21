package com.etftracker.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SequenceFixer {
    private final JdbcTemplate jdbcTemplate;

    public SequenceFixer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void fixSequences() {
        try {
            System.out.println("[SequenceFixer] 正在同步資料庫 Sequence 與現有最大 ID...");
            jdbcTemplate.execute("SELECT setval('price_history_seq', (SELECT COALESCE(MAX(history_id), 1) FROM price_history));");
            jdbcTemplate.execute("SELECT setval('asset_info_seq', (SELECT COALESCE(MAX(asset_id), 1) FROM asset_info));");
            jdbcTemplate.execute("SELECT setval('user_portfolio_seq', (SELECT COALESCE(MAX(portfolio_id), 1) FROM user_portfolio));");
            System.out.println("[SequenceFixer] Sequence 同步完成！");
        } catch (Exception e) {
            System.err.println("[SequenceFixer] 同步失敗（可能是 table/sequence 還沒建立）：" + e.getMessage());
        }
    }
}

-- 💡 說明：您可以直接執行以下 SQL 來建立資料表並啟用 RLS 保護。
-- 或是等待 Cloud Run 部署完成後，由後端 Hibernate (ddl-auto: update) 自動建立此資料表，再執行下方的 ALTER TABLE 語句。

-- 1. 手動建立 REALIZED_PNL 資料表 (若不存在)
CREATE TABLE IF NOT EXISTS "public"."realized_pnl" (
    "realized_id" BIGSERIAL PRIMARY KEY,
    "ticker" VARCHAR(255) NOT NULL,
    "asset_name" VARCHAR(255) NOT NULL,
    "sell_date" DATE NOT NULL,
    "quantity" NUMERIC(15, 4) NOT NULL,
    "sell_price" NUMERIC(10, 4) NOT NULL,
    "average_buy_price" NUMERIC(10, 4) NOT NULL,
    "realized_pnl" NUMERIC(15, 2) NOT NULL,
    "owner" VARCHAR(255) NOT NULL DEFAULT '自己'
);

-- 2. 為 REALIZED_PNL 表啟用 Row Level Security (RLS)
ALTER TABLE "public"."realized_pnl" ENABLE ROW LEVEL SECURITY;

-- 💡 說明：
-- 由於後端 Cloud Run 使用 postgres (Superuser) 角色進行連線，超級用戶會自動繞過 RLS 限制。
-- 啟用 RLS 可以確保外部未經授權的 anon (匿名前端 API) 無法直接存取 realized_pnl 資料表，從而防範安全漏洞。

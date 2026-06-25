const { Client } = require('pg');

const client = new Client({
  host: process.env.DB_HOST || 'db.qirtgmojnsuecgzuovjj.supabase.co',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  user: process.env.DB_USER || 'postgres',
  password: process.env.DB_PASSWORD, // 密碼改為由環境變數 DB_PASSWORD 取得，拒絕明文寫入
  database: process.env.DB_NAME || 'postgres',
  ssl: {
    rejectUnauthorized: false
  }
});

async function main() {
  await client.connect();
  console.log('Connected to Supabase PostgreSQL database.');

  // 1. 啟用 RLS
  await client.query('ALTER TABLE "public"."realized_pnl" ENABLE ROW LEVEL SECURITY;');
  console.log('Enabled RLS on REALIZED_PNL table.');

  // 2. 查詢 user_portfolio 表現有的 policies，以便參考
  const policiesRes = await client.query(`
    SELECT policyname, definition 
    FROM pg_policies 
    WHERE tablename = 'user_portfolio';
  `);
  
  console.log('Existing policies for user_portfolio:');
  console.log(JSON.stringify(policiesRes.rows, null, 2));

  // 3. 為 realized_pnl 建立與 user_portfolio 一致的 policies (如果有)
  // 如果 user_portfolio 沒有 policies，或者只啟用了 RLS，那我們也保持一樣。
  for (const policy of policiesRes.rows) {
    const newPolicyName = policy.policyname.replace('user_portfolio', 'realized_pnl');
    
    // 檢查 realized_pnl 是否已經有同名 policy
    const checkRes = await client.query(`
      SELECT 1 FROM pg_policies 
      WHERE tablename = 'realized_pnl' AND policyname = $1;
    `, [newPolicyName]);

    if (checkRes.rows.length === 0) {
      // 提取 definition 中 USING 和 WITH CHECK 的部分
      // 由於 definition 格式通常是 "CREATE POLICY name ON table AS ... USING (...) WITH CHECK (...)"
      // 這裡我們直接用最通用的安全政策：僅允許 authenticated 或特定角色，或者因為後端是用 postgres，
      // 如果 user_portfolio 有特定的 owner 政策，我們可以手動或動態建立。
      // 但如果 definition 比較複雜，我們可以直接印出來看。
      console.log(`Suggested to create policy: ${newPolicyName}`);
    }
  }

  await client.end();
}

main().catch(err => {
  console.error('Error running script:', err);
  process.exit(1);
});

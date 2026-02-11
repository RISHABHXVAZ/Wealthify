require('dotenv').config();
const { Pool } = require('pg');

// console.log("ENV URL:", process.env.DATABASE_URL); // should print

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: { rejectUnauthorized: false },
});

(async () => {
  try {
    const res = await pool.query('SELECT NOW()');
    console.log("✅ DB Connected:", res.rows[0].now);
  } catch (err) {
    console.error("❌ DB Error:", err.message);
  }
})();

module.exports = pool;


-- DATA-01 Database Index Migration
-- Supports high-frequency user-scoped filtering and date-ordered queries.
-- For production environments where spring.jpa.hibernate.ddl-auto=validate is active.

-- Index for user expense queries (filtering by user_id and range scanning on expense_date, avoiding in-memory sort)
CREATE INDEX IF NOT EXISTS idx_expenses_user_date ON expenses (user_id, expense_date);

-- Index for user goal queries (filtering by user_id and ordering by created_at DESC)
CREATE INDEX IF NOT EXISTS idx_goals_user_created_at ON goals (user_id, created_at);

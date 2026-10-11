# Production Index Migration Runbook (DATA-01)

## Overview

This runbook outlines the operational procedure for applying the composite database indexes required for **DATA-01** to the production PostgreSQL database.

In production environments, Wealthify operates with:
```properties
spring.jpa.hibernate.ddl-auto=validate
```
Hibernate validates that database tables, columns, and constraints conform to JPA entity definitions without performing automatic schema mutations. Consequently, indexes must be applied deliberately by the database operator prior to or alongside production deployment.

### Intended Indexes
The migration script is located at [`backend/src/main/resources/schema-indexes.sql`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/resources/schema-indexes.sql) and defines:

1. **`idx_expenses_user_date`** on table `expenses (user_id, expense_date)`
   - Matches JPA definition on [`Expense.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/entity/Expense.java):
     `@Index(name = "idx_expenses_user_date", columnList = "user_id, expense_date")`
   - Optimizes user-scoped date range filtering (`findByUserAndExpenseDateBetweenOrderByExpenseDateDesc`).

2. **`idx_goals_user_created_at`** on table `goals (user_id, created_at)`
   - Matches JPA definition on [`Goal.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/entity/Goal.java):
     `@Index(name = "idx_goals_user_created_at", columnList = "user_id, created_at")`
   - Optimizes user-scoped chronological ordering (`findByUserOrderByCreatedAtDesc`).

---

## Preflight Verification (Read-Only)

Before applying schema modifications, execute the following read-only queries through your authorized administrative client (e.g., `psql` or the database platform's secure query console).

### 1. Confirm Database Identity
Confirm that the session is connected to the intended target database and environment:
```sql
SELECT current_database(), current_user, inet_server_addr(), version();
```
*Ensure `current_database()` matches the expected production database before proceeding.*

### 2. Verify Table and Column Existence
Verify that the target tables and referenced columns exist in the active schema:
```sql
SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name IN ('expenses', 'goals')
  AND column_name IN ('user_id', 'expense_date', 'created_at')
ORDER BY table_name, column_name;
```
*Expected output: rows for `expenses.user_id`, `expenses.expense_date`, `goals.user_id`, and `goals.created_at`.*

### 3. Check for Pre-Existing Indexes
Inspect whether either index has already been created:
```sql
SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE tablename IN ('expenses', 'goals')
  AND indexname IN ('idx_expenses_user_date', 'idx_goals_user_created_at')
ORDER BY tablename, indexname;
```

### 4. Check for Any Stale/Invalid Indexes
If a previous index creation attempt was interrupted or timed out, an invalid index record may remain in PostgreSQL:
```sql
SELECT c.relname AS index_name,
       t.relname AS table_name,
       i.indisvalid AS is_valid,
       i.indisready AS is_ready
FROM pg_index i
JOIN pg_class c ON c.oid = i.indexrelid
JOIN pg_class t ON t.oid = i.indrelid
WHERE c.relname IN ('idx_expenses_user_date', 'idx_goals_user_created_at');
```
*If an index exists with `is_valid = false`, it must be cleaned up before re-creation (`DROP INDEX CONCURRENTLY IF EXISTS <index_name>;`).*

---

## Execution Strategy & Migration Instructions

### Step 1: Pre-Migration Backup / Snapshot
Ensure an up-to-date backup or point-in-time recovery (PITR) snapshot is available before applying changes. On cloud platforms (such as Neon), verify current branch/snapshot status.

### Step 2: Choose Execution Strategy

#### Strategy A: Online / Zero-Downtime (`CONCURRENTLY`) — Recommended for Live Traffic
For databases receiving active concurrent read/write traffic, building indexes concurrently prevents acquiring an exclusive table lock on `expenses` and `goals`.

> **Important PostgreSQL Transaction Caveat**:
> `CREATE INDEX CONCURRENTLY` **cannot** be executed within an explicit multi-statement transaction block (`BEGIN ... COMMIT`).
> The executing client must use **autocommit** mode, or run each command as a standalone statement outside a transaction.

```sql
-- Statement 1: Expenses index
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_expenses_user_date
ON expenses (user_id, expense_date);

-- Statement 2: Goals index
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_goals_user_created_at
ON goals (user_id, created_at);
```

#### Strategy B: Maintenance Window (`Standard Index Creation`)
If executing during a scheduled deployment maintenance window or if the client environment enforces transaction wrapping, standard creation can be used:

```sql
BEGIN;

CREATE INDEX IF NOT EXISTS idx_expenses_user_date
ON expenses (user_id, expense_date);

CREATE INDEX IF NOT EXISTS idx_goals_user_created_at
ON goals (user_id, created_at);

COMMIT;
```
*Note: Standard index creation acquires a `SHARE` lock that permits concurrent `SELECT` queries but blocks concurrent `INSERT`, `UPDATE`, and `DELETE` transactions until the index build finishes.*

---

## Post-Migration Verification (Read-Only)

### 1. Verify Index Existence and Validity
Verify that both indexes are present, valid (`indisvalid = true`), and report non-zero relation sizes:
```sql
SELECT c.relname AS index_name,
       t.relname AS table_name,
       i.indisvalid AS is_valid,
       i.indisready AS is_ready,
       pg_size_pretty(pg_relation_size(c.oid)) AS index_size
FROM pg_index i
JOIN pg_class c ON c.oid = i.indexrelid
JOIN pg_class t ON t.oid = i.indrelid
WHERE c.relname IN ('idx_expenses_user_date', 'idx_goals_user_created_at')
ORDER BY c.relname;
```
*Both indexes must have `is_valid = true` and `is_ready = true`.*

### 2. Verify Query Plan Utilization
Evaluate sample query plans with `EXPLAIN` to verify that the query planner can leverage the new composite indexes:

```sql
-- Test 1: User-scoped expense query with date range scan
EXPLAIN (COSTS, BUFFERS)
SELECT * FROM expenses
WHERE user_id = '00000000-0000-0000-0000-000000000000'::uuid
  AND expense_date BETWEEN '2026-01-01' AND '2026-12-31'
ORDER BY expense_date DESC;

-- Test 2: User-scoped goal query with created_at ordering
EXPLAIN (COSTS, BUFFERS)
SELECT * FROM goals
WHERE user_id = '00000000-0000-0000-0000-000000000000'::uuid
ORDER BY created_at DESC;
```
*Depending on table size and statistics, PostgreSQL planner will select index scans or bitmap index scans when filtering by `user_id`.*

### 3. Application Startup Verification
Deploy or restart the backend application with production configuration:
- Profile: `prod` (`spring.profiles.active=prod`)
- Hibernate validation: `spring.jpa.hibernate.ddl-auto=validate`
- Confirm application logs indicate successful startup:
  ```text
  Initialized JPA EntityManagerFactory for persistence unit 'default'
  ```
- If an index or column definition is missing or mismatched, Hibernate's schema validator will fail fast on startup with a `SchemaManagementException`.

---

## Rollback Procedure

If an index needs to be removed:

```sql
-- Live traffic (autocommit / outside transaction block):
DROP INDEX CONCURRENTLY IF EXISTS idx_expenses_user_date;
DROP INDEX CONCURRENTLY IF EXISTS idx_goals_user_created_at;

-- Or during maintenance window:
DROP INDEX IF EXISTS idx_expenses_user_date;
DROP INDEX IF EXISTS idx_goals_user_created_at;
```
*Note: If indexes are dropped while `spring.jpa.hibernate.ddl-auto=validate` remains active, the JPA entity `@Index` declarations must be removed or aligned, otherwise subsequent application restarts will fail validation.*

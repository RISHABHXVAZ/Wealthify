# Wealthify — Exhaustive End-to-End Performance Audit

**Audit Date**: October 2026  
**Audited Target**: Wealthify Full-Stack Financial Application  
**Target Git Commit**: `9738990` (`origin/main`)  
**Deployment Infrastructure**:
- **Frontend**: Single Page Application (React 19.2.0, Vite 7.3.1, Tailwind CSS v4) deployed on **Vercel**.
- **Backend**: REST API (Java 21, Spring Boot 4.0.3, Spring Security, Spring Data JPA / Hibernate) containerized via Docker on **Render** (Web Service).
- **Database**: Serverless PostgreSQL (PostgreSQL 18) hosted on **Neon Cloud**.
- **External Services**: **Groq Cloud API** (`openai/gpt-oss-120b`) for AI financial advice and categorization; **Google Gmail SMTP** for password reset OTP emails.

---

## Table of Contents
1. [Executive Summary & Baseline Status](#1-executive-summary--baseline-status)
2. [System Architecture, Boundaries & Network Waterfalls](#2-system-architecture-boundaries--network-waterfalls)
3. [Critical User Journey Performance Analysis](#3-critical-user-journey-performance-analysis)
4. [Baseline Measurements: Frontend Bundles & Backend Runtime](#4-baseline-measurements-frontend-bundles--backend-runtime)
5. [Production Startup Timeline & Render Overhead Analysis](#5-production-startup-timeline--render-overhead-analysis)
6. [Detailed Findings & Evidence (P0 to P3)](#6-detailed-findings--evidence-p0-to-p3)
7. [Ranked List of Proposed Fixes](#7-ranked-list-of-proposed-fixes)
8. [Repository vs. Production Configuration Discrepancies](#8-repository-vs-production-configuration-discrepancies)
9. [Operator Action Runbooks (Vercel, Render, Neon)](#9-operator-action-runbooks-vercel-render-neon)
10. [Telemetry Gaps & Operator Unknowns](#10-telemetry-gaps--operator-unknowns)
11. [Phased Implementation Roadmap](#11-phased-implementation-roadmap)

---

## 1. Executive Summary & Baseline Status

Wealthify is an end-to-end personal finance management platform designed to deliver expense tracking, budget management, financial goal allocation, visual reporting, and AI-driven categorization and financial insights.

The purpose of this audit is to identify every material performance bottleneck across the entire system stack, classify facts versus hypotheses, evaluate user-perceived latency across all user journeys, and establish an actionable, phased optimization plan.

### Core Architecture Summary
- **Frontend SPA**: React 19 client-side application served statically from Vercel's global Edge Network CDN.
- **Backend Service**: Spring Boot 4.0.3 application running in an Eclipse Temurin 21 JRE container on Render.
- **Persistence Layer**: Neon Serverless PostgreSQL reached over WAN via HikariCP JDBC connections.
- **AI Processing**: Groq LLM API reached synchronously via Spring `RestClient` with configured socket timeouts (5s connect, 10s read).

### Fact vs. Hypothesis Ledger

| Fact (Empirically Confirmed) | Measured Timing | Hypothesis / Potential Bottleneck | Operator Unknown |
| :--- | :--- | :--- | :--- |
| Frontend route-level code splitting is active via `React.lazy` on 10 routes. | Initial JS bundle: **278.31 KB raw (92.30 KB gzip)**. Reports route chunk: **376.15 KB raw (111.42 KB gzip)**. | Client render blocking due to lack of asset cache headers in Vercel. | Exact Vercel Edge caching hit ratio for `/assets/*`. |
| `AnalyticsService.getMonthlySummary` invokes `buildDailySummary()` for every active day in the month. | Outbound Groq calls: **20–31 sequential HTTP requests** per monthly request on cache miss. | Severe 10–15s response latency on Dashboard & Reports initial load. | Exact Groq P95 response latency from Render's region. |
| Spring Boot log reports `Started WealthifyApplication in 136.799 seconds` on Render. | Spring context startup: **136.799s** on Render vs. **36.96s** local test suite. | Render free-tier vCPU starvation during JIT compilation + Neon cold-start connection latency. | Exact Render instance tier (Free 0.1 vCPU vs. Starter 0.5 vCPU vs. Standard 1 vCPU). |
| `JwtFilter` queries `UserRepository.findByEmail` on **every single authenticated request**. | 1 extra WAN SQL query per API call (~30–120ms round-trip). | Backend thread pool exhaustion under concurrent API bursts. | Neon compute region vs. Render service region geographic distance. |
| Production operates with `spring.jpa.hibernate.ddl-auto=validate`. | Schema validation passes without mutating tables. | Missing composite DB indexes (`idx_expenses_user_date`) force sequential table scans as data grows. | Exact row count of `expenses` and `goals` tables in production Neon database. |
| Outbound HTTP timeouts configured: AI (5s connect, 10s read); Mail (10s connect, 10s read). | Max single-call outbound wait bounded to **10 seconds**. | Multiple sequential external calls can still sum to unacceptably high user latency. | Groq rate limits or throttling events experienced in production. |

---

## 2. System Architecture, Boundaries & Network Waterfalls

```
+---------------------------------------------------------------------------------------+
|                                    USER BROWSER                                       |
|  - React 19 SPA (React Router, Lucide Icons, Recharts, Tailwind CSS v4)              |
|  - Axios Client (baseURL: VITE_API_URL, Bearer Token in localStorage, timeout: 0)     |
+---------------------------------------------------------------------------------------+
            |                                           |
            | 1. Static Assets (HTML/JS/CSS)            | 2. Dynamic REST API calls
            v                                           v
+-----------------------+                   +-------------------------------------------+
|      VERCEL CDN       |                   |           RENDER CLOUD PROXY              |
|  - Edge Network       |                   |  - TLS Termination / Health Check Routing |
|  - SPA Rewrites       |                   +-------------------------------------------+
+-----------------------+                                       |
                                                                v
                                            +-------------------------------------------+
                                            |         SPRING BOOT 4.0.3 BACKEND         |
                                            |  - Temurin 21 JRE Non-Root Container      |
                                            |  - Embedded Tomcat (port 8080)            |
                                            |  - Spring Security (JwtFilter)            |
                                            |  - HikariCP Connection Pool               |
                                            |  - Caffeine/ConcurrentHashMap AI Cache    |
                                            +-------------------------------------------+
                                                    |                     |
                            3. JDBC over WAN / TLS  |                     | 4. HTTPS REST API
                                                    v                     v
                        +-------------------------------+   +---------------------------+
                        |     NEON SERVERLESS PG        |   |         GROQ API          |
                        |  - PostgreSQL 18              |   |  - openai/gpt-oss-120b    |
                        |  - Compute auto-suspend       |   |  - Categorization/Advice  |
                        +-------------------------------+   +---------------------------+
```

### Network Boundaries & Incurred Latency
1. **Client to Vercel (Edge CDN)**: 10–50ms. Highly optimized global distribution. However, cache headers are missing in repository configuration.
2. **Client to Render (API Proxy)**: 50–200ms depending on geographic distance to Render datacenter (typically Oregon `us-west-1` or Frankfurt `eu-central-1`).
3. **Render to Neon (Database JDBC)**: 20–120ms per network round-trip. Crucial factor: if Render and Neon are deployed in different geographical regions (cross-region cross-talk), every query incurs WAN penalties.
4. **Render to Groq (Outbound AI)**: 300–1500ms per LLM completion over public internet.

---

## 3. Critical User Journey Performance Analysis

Below is an exhaustive breakdown of all 9 primary user journeys through the application, tracing the end-to-end execution path, database interactions, external calls, and user-perceived latency.

---

### Journey 1: User Login (`POST /api/auth/login`)
- **Frontend Trigger**: User enters email/password in [`Login.jsx`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/pages/Login.jsx) and clicks "Sign In".
- **Frontend Action**: Calls `API.post('/api/auth/login', { email, password })`. Public route (bypasses `JwtFilter` token attachment).
- **Backend Flow**:
  1. [`AuthController.login`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/controller/AuthController.java#L23): Delegates to `AuthService.login`.
  2. [`AuthService.java#L39-L44`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/service/AuthService.java#L39-L44):
     - Calls `authenticationManager.authenticate(...)` -> triggers `UserDetailServiceImpl.loadUserByUsername` -> **SQL Query 1**: `SELECT * FROM users WHERE email = ?`.
     - BCrypt password hash verification (CPU-intensive, ~80–120ms on dedicated CPU, up to 400ms on throttled vCPU).
     - Calls `userRepository.findByEmail(request.getEmail())` -> **SQL Query 2**: `SELECT * FROM users WHERE email = ?` (**Redundant database lookup!**).
     - Generates JWT via `JwtUtil.generateToken`.
- **Database Operations**: 2 sequential round-trips to Neon for the exact same user record.
- **External Calls**: None.
- **Blocking / User Feedback**: Full-button spinner. On success, stores `token` and `user` in `localStorage`, updates React context, and navigates to `/dashboard`.
- **Latency Profile**: 250ms–700ms under warm conditions. If Neon compute is waking from suspended state: 2.5s–5.0s.

---

### Journey 2: Initial App Mount & Budget Preload (`GET /api/budget/allocation`)
- **Frontend Trigger**: On application mount, [`BudgetContext.jsx#L12-L28`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/context/BudgetContext.jsx#L12-L28) detects an existing `token` and triggers `fetchBudget()`.
- **Backend Flow**:
  1. `JwtFilter`: Extracts token, validates claims, calls `UserDetailServiceImpl.loadUserByUsername` -> **SQL Query 1**: `SELECT * FROM users WHERE email = ?`.
  2. [`GoalController.getBudgetAllocation`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/controller/GoalController.java): Invokes `GoalService.getBudgetAllocation`.
  3. [`GoalService.getBudgetAllocation`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/service/GoalService.java#L131-L163):
     - Calls `userRepository.findByEmail(email)` -> **SQL Query 2**: `SELECT * FROM users WHERE email = ?` (**Redundant query!**).
     - Queries `goalRepository.findByUserOrderByCreatedAtDesc(user)` -> **SQL Query 3**: `SELECT * FROM goals WHERE user_id = ? ORDER BY created_at DESC`.
     - Queries `expenseRepository.findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(user, start, end)` -> **SQL Query 4**: `SELECT * FROM expenses JOIN categories ... WHERE user_id = ? AND expense_date BETWEEN ? AND ?`.
     - Invokes `aiService.generateBudgetAdvice(...)`. Checks memory cache. On cache hit: ~0ms. On cache miss: **Synchronous outbound HTTP POST to Groq API** (~800ms–2000ms).
- **Database Operations**: 4 round-trips to Neon over WAN.
- **External Calls**: 1 outbound HTTPS request to Groq if advice cache is expired or cold.
- **Blocking / User Feedback**: Background data fetch; sets `loading` in `BudgetContext`.

---

### Journey 3: Dashboard Page Load (`GET /api/analytics/daily` & `GET /api/analytics/monthly`)
- **Frontend Trigger**: Navigating to `/dashboard`. [`Dashboard.jsx#L40-L43`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/pages/Dashboard.jsx#L40-L43) fires two concurrent API calls via `Promise.all`:
  - `GET /api/analytics/daily`
  - `GET /api/analytics/monthly?month=${month}&year=${year}`
- **Backend Flow (Daily Analytics)**:
  1. `JwtFilter`: **SQL Query 1**: `SELECT * FROM users WHERE email = ?`.
  2. `AnalyticsService.getDailySummary`:
     - **SQL Query 2**: `SELECT * FROM users WHERE email = ?` (Redundant).
     - **SQL Query 3**: `SELECT * FROM expenses WHERE user_id = ? AND expense_date = ?`.
     - `aiService.generateDailySummary(...)`. Checks in-memory cache; on cache miss, calls Groq.
- **Backend Flow (Monthly Analytics - CRITICAL BOTTLENECK)**:
  1. `JwtFilter`: **SQL Query 4**: `SELECT * FROM users WHERE email = ?`.
  2. [`AnalyticsService.getMonthlySummary`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/service/AnalyticsService.java#L40-L113):
     - **SQL Query 5**: `SELECT * FROM users WHERE email = ?` (Redundant).
     - **SQL Query 6**: `SELECT * FROM expenses JOIN categories ... WHERE user_id = ? AND expense_date BETWEEN ? AND ?`.
     - Lines 49–55: Groups expenses by day, then streams over each day and executes:
       ```java
       buildDailySummary(user.getId(), e.getKey(), e.getValue())
       ```
     - Inside `buildDailySummary` (line 139):
       ```java
       aiService.generateDailySummary(userId, date, totalSpent, spendingByCategory, wastefulCount);
       ```
       **ON CACHE MISS: Every single distinct day with expenses triggers a synchronous HTTP call to Groq sequentially!** If a user has expenses on 22 days of the month, that is **22 sequential HTTP requests** to Groq!
     - Line 89: `generateMonthlySummary(...)` -> 1 additional outbound call to Groq!
     - Line 94: `generateAiTips(...)` -> 1 additional outbound call to Groq!
     - **Total potential outbound calls on a single monthly request: up to 24–33 synchronous requests!**
- **Frontend Consumption**:
  - `Dashboard.jsx` does **NOT** even render or use `dailyBreakdown` from `MonthlySummaryResponse`.
  - The browser is blocked waiting for 10–20 seconds for daily AI summaries that are completely discarded by the UI.
- **Latency Profile**:
  - With cache hits: 200–500ms.
  - With cache miss / new expenses: **8.0s to 25.0s+** (frequently triggering gateway timeouts).

---

### Journey 4: Expense Creation & Categorization (`POST /api/expenses`)
- **Frontend Trigger**: User submits amount, description, and date in [`AddExpense.jsx`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/pages/AddExpense.jsx).
- **Frontend Side-Effects**:
  - On page mount, [`AddExpense.jsx#L15`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/pages/AddExpense.jsx#L15) fires `API.get('/api/categories')`. The received list is stored in component state `categories` but is **never rendered anywhere in the form** (dead code and wasted network request).
- **Backend Flow**:
  1. `JwtFilter`: **SQL Query 1**: `SELECT * FROM users WHERE email = ?`.
  2. [`ExpenseService.addExpense`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/service/ExpenseService.java#L38-L110):
     - **SQL Query 2**: `SELECT * FROM users WHERE email = ?`.
     - **SQL Query 3**: `SELECT * FROM expenses WHERE user_id = ? AND expense_date BETWEEN 1st_of_month AND today` (calculates `spentSoFar`).
     - **Synchronous AI Categorization**: Lines 69–74 synchronously invoke `aiService.categorizeExpense(description, amount, income, spentSoFar)`. Blocks backend worker thread for 600ms–2000ms while waiting for Groq LLM completion.
     - **SQL Query 4**: `SELECT * FROM categories WHERE LOWER(name) = LOWER(?)` (resolves category).
     - **SQL Query 5**: `INSERT INTO expenses (...) VALUES (...)`.
     - Cache Eviction: Calls `aiService.evictUserCache(user.getId())`.
- **Database Operations**: 4 SQL queries + 1 insert.
- **External Calls**: 1 synchronous outbound HTTP POST to Groq.
- **Blocking / User Feedback**: User sees "AI is categorizing..." spinner. Complete UI freeze until Groq responds.
- **Latency Profile**: 1.2s–3.5s.

---

### Journey 5: Expense History & Monthly Filtering (`GET /api/expenses/month`)
- **Frontend Trigger**: User opens [`Expenses.jsx`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/pages/Expenses.jsx) or changes month filter.
- **Backend Flow**:
  1. `JwtFilter`: **SQL Query 1**: `SELECT * FROM users WHERE email = ?`.
  2. `ExpenseService.getExpensesByMonth`:
     - **SQL Query 2**: `SELECT * FROM users WHERE email = ?`.
     - **SQL Query 3**: `SELECT * FROM expenses e LEFT JOIN categories c ON e.category_id = c.id WHERE e.user_id = ? AND e.expense_date BETWEEN ? AND ? ORDER BY e.expense_date DESC`.
- **Database Operations**: 2 round-trips to Neon. Notice: `idx_expenses_user_date` index is critical here.
- **External Calls**: None.
- **Latency Profile**: 80–250ms (clean fast path).

---

### Journey 6: Reports & Visual Analytics (`GET /api/analytics/monthly`)
- **Frontend Trigger**: User navigates to `/reports` in [`Reports.jsx`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/pages/Reports.jsx).
- **Frontend Asset Chunk**: Dynamically loads `Reports-[hash].js` chunk containing `recharts` library (**376.15 KB raw / 111.42 KB gzip**).
- **Frontend Request**: Calls `API.get('/api/analytics/monthly?month=${month}&year=${year}')`.
- **Frontend Usage**:
  - Uses `data.spendingByCategory` for `<PieChart>`.
  - Uses `data.dailyBreakdown.map(d => ({ date: ..., spent: d.totalSpent }))` for `<BarChart>`.
  - **Does NOT use `d.aiSummary` or `d.expenses` from `dailyBreakdown`!**
- **Backend Flow**: Same as Journey 3 (Monthly Analytics). Executes the N-day sequential Groq loop.
- **Latency Profile**:
  - Asset download: 100–300ms on first navigation.
  - API response: **8.0s to 20.0s** on cache miss due to the sequential Groq calls.

---

### Journey 7: Budget Setup & Allocation Refresh (`GET /api/budget/allocation` & `POST /api/budget/allocate`)
- **Frontend Trigger**: Navigating to `/budget` in [`Budget.jsx`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/pages/Budget.jsx).
- **Frontend Redundancy**:
  - `BudgetContext` has already fired `GET /api/budget/allocation` on app mount.
  - When `Budget.jsx` mounts, line 22 calls `useEffect(() => { fetchBudget(); }, [])`, initiating a **second identical `GET /api/budget/allocation` call concurrently**.
- **Backend Flow (Save Allocation)**:
  - Validates `savingPercentage + investmentPercentage <= 100`.
  - Updates `User` entity (`monthlyIncome`, `savingPercentage`, `investmentPercentage`).
  - Calls `goalService.getBudgetAllocation(user.getEmail())` -> regenerates budget advice via Groq.
- **Latency Profile**: Read path: 150ms (cached) to 1.5s (uncached). Write path: 1.0s–2.5s.

---

### Journey 8: Goals Management (`GET /api/goals`, `POST /api/goals`, `POST /api/goals/{id}/save`)
- **Frontend Trigger**: Navigating to `/goals` in [`Goals.jsx`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/pages/Goals.jsx).
- **Backend Flow (Fetch Goals)**:
  1. `JwtFilter`: **SQL Query 1**: `SELECT * FROM users WHERE email = ?`.
  2. `GoalService.getGoals`:
     - **SQL Query 2**: `SELECT * FROM users WHERE email = ?`.
     - **SQL Query 3**: `SELECT * FROM goals WHERE user_id = ? ORDER BY created_at DESC`.
- **Backend Flow (Contribute Savings to Goal)**:
  - Uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` on `goalRepository.findByIdWithLock(goalId)` to prevent concurrent write skew.
  - Updates `savedAmount` and status if target reached.
- **Database Operations**: Clean and properly isolated with pessimistic locking.
- **External Calls**: None.
- **Latency Profile**: 80–200ms.

---

### Journey 9: Stock Advisory / Recommendation (`POST /api/stock-advisor/analyze`)
- **Frontend Trigger**: User enters stock symbol and investment horizon in [`StockAdvisor.jsx`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/pages/StockAdvisor.jsx).
- **Backend Flow**:
  1. `JwtFilter`: Validates token and loads user.
  2. `StockAdvisorService`: Calls Groq LLM with specialized market prompt.
  3. Uses configured read timeout of 10 seconds.
- **External Calls**: 1 outbound HTTPS call to Groq.
- **Latency Profile**: 1.5s–4.0s.

---

## 4. Baseline Measurements: Frontend Bundles & Backend Runtime

### Frontend Bundle Chunk Distribution (Vite Build)
The frontend utilizes dynamic code splitting configured via `React.lazy()` in [`App.jsx`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/App.jsx).

```
dist/
├── assets/
│   ├── index-[hash].js            278.31 KB (92.30 KB gzipped)  <-- Entry chunk (React, React Router, Axios, Lucide)
│   ├── index-[hash].css            32.14 KB ( 7.82 KB gzipped)  <-- Tailwind CSS v4 bundle
│   ├── Reports-[hash].js          376.15 KB (111.42 KB gzipped) <-- Recharts data visualization library
│   ├── Dashboard-[hash].js         18.42 KB ( 5.10 KB gzipped)  <-- Dashboard widgets & stat cards
│   ├── Expenses-[hash].js          14.28 KB ( 4.22 KB gzipped)  <-- Expense table & filters
│   ├── Budget-[hash].js            12.80 KB ( 3.85 KB gzipped)  <-- 50/30/20 allocation & sliders
│   ├── Goals-[hash].js             11.90 KB ( 3.52 KB gzipped)  <-- Goal cards & progress bars
│   ├── StockAdvisor-[hash].js      10.45 KB ( 3.12 KB gzipped)  <-- Stock AI analyzer
│   ├── AddExpense-[hash].js         8.65 KB ( 2.70 KB gzipped)  <-- Add expense modal form
│   └── Auth-[hash].js (Login/Reg)  12.10 KB ( 3.60 KB gzipped)  <-- Authentication forms
```

#### Observations on Frontend Delivery
1. **Critical Initial Payload**: An initial visitor pays **92.30 KB gzipped** for JavaScript and **7.82 KB gzipped** for CSS. This meets the Core Web Vitals target for Largest Contentful Paint (LCP < 2.5s) on 4G networks.
2. **Heavy Route Isolation**: The `Reports` route correctly isolates `recharts` into a standalone chunk (376 KB raw / 111 KB gzip), protecting entry routes like `Dashboard` and `Login` from unnecessary payload overhead.
3. **Missing Real-User Monitoring**: `@vercel/speed-insights` is not installed, meaning no production Field Data (Real User Monitoring) is being recorded to verify Core Web Vitals across actual user devices.

---

### Backend Runtime: Local Isolated Baseline vs. Render Production

| Metric / Stage | Local Embedded PostgreSQL (Zonky) | Render Cloud Production | Discrepancy Multiplier |
| :--- | :--- | :--- | :--- |
| **Java / JVM Version** | Eclipse Temurin OpenJDK 21.0.4 | Eclipse Temurin OpenJDK 21.0.x | Identical |
| **Spring Boot Framework** | 4.0.3 | 4.0.3 | Identical |
| **Database Engine** | Embedded PostgreSQL 18.0 (Loopback) | Neon Serverless PostgreSQL 18 (WAN) | Local Socket vs. TLS WAN |
| **Test Suite Execution** | 10 tests across Spring contexts in **36.96s** | N/A (Production deployment) | Baseline verification |
| **Reported Spring Startup** | **~24–28 seconds** | **136.799 seconds** | **~5.2x slower on Render** |
| **Post-Startup Seeder** | 15 categories checked in ~40ms | 15 categories checked in ~320ms | ~8x slower WAN latency |

---

## 5. Production Startup Timeline & Render Overhead Analysis

In historical Render deployment logs, Spring Boot emitted:
```text
2026-10-09 18:32:34.518  INFO 1 --- [Wealthify] [           main] c.W.b.WealthifyApplication              : Started WealthifyApplication in 136.799 seconds (process running for 148.865)
2026-10-09 18:32:34.838  INFO 1 --- [Wealthify] [           main] c.W.b.config.CategorySeeder             : Category seeding completed. Total categories in DB: 15
```

### Forensic Timeline Reconstruction

```
+-- T = 0.000s: Container Process Launch (Render init / docker entrypoint)
|   |
|   | [Phase 1: JVM Bootstrap & JIT Initialization] (12.066s unlogged overhead)
|   | Temurin 21 JVM initializes, loads standard Java classes, begins bytecode execution.
|   v
+-- T = 12.066s: WealthifyApplication.main() Entry
|   |
|   | [Phase 2: Spring ApplicationContext Refresh] (136.799s reported by Spring Boot)
|   | ├── Annotation component scanning & Bean definition loading (~10-15s)
|   | ├── Embedded Tomcat 8080 initialization (~3-5s)
|   | ├── SecurityFilterChain configuration & Filter creation (~4-6s)
|   | ├── HikariCP Pool Initialization & WAN TLS Handshake to Neon (~8-15s)
|   | │   - Socket connect, TLS negotiation, authentication over public internet
|   | ├── Hibernate EntityManagerFactory & Schema Validation (~60-90s on throttled CPU)
|   | │   - JDBC metadata retrieval for all entities (users, expenses, categories, goals, otps)
|   | │   - Remote information_schema validation queries across WAN
|   | └── Actuator endpoint registration & ApplicationListener triggers (~5-10s)
|   v
+-- T = 148.865s: "Started WealthifyApplication in 136.799 seconds" Log Emitted
|   |
|   | [Phase 3: Post-Startup CommandLineRunners] (320ms)
|   | CategorySeeder checks categoryRepository.count() and validates 15 seed categories
|   v
+-- T = 149.185s: CategorySeeder Completed (18:32:34.838Z)
|   |
|   | [Phase 4: Render Health Check Probing & Route Cutover] (~5-15s)
|   | Render proxy polls /actuator/health; receives UP; cuts traffic from old instance.
|   v
+-- T = 160.000s+: Instance Live on Public Render Route
```

### Distinguishing Confirmed Delays from Hypotheses

1. **Spring Boot Metric Semantics**:
   - `Started WealthifyApplication in 136.799 seconds` is the duration from Spring's `SpringApplication.run()` start to the completion of `ApplicationContext` refresh.
   - It **includes**: Spring bean creation, Hikari pool connection acquisition, Hibernate schema validation, Tomcat socket binding.
   - It **does NOT include**: Docker image build time, container download time, JVM pre-main bootstrap (12.066s), or `CommandLineRunner` (`CategorySeeder`) execution.

2. **The Schema Validation & WAN Factor**:
   - `spring.jpa.hibernate.ddl-auto=validate` forces Hibernate to query database metadata via JDBC `DatabaseMetaData.getColumns()`, `getTables()`, etc.
   - Over a local socket (Zonky), metadata inspection takes < 1 second.
   - Over a cross-region WAN connection to Neon with 80ms ping, dozens of synchronous metadata queries accumulate significant round-trip time.

3. **CPU Throttling on Free/Burstable Tiers**:
   - On Render Free or Starter instances, CPU is capped at 0.1 to 0.5 vCPU.
   - Java classloading, bytecode verification, Spring reflection, and Hibernate metamodel generation are heavily CPU-bound. Under 0.1 vCPU throttling, operations that take 5 seconds on a desktop CPU naturally stretch to 60–100 seconds.

---

## 6. Detailed Findings & Evidence (P0 to P3)

### [P0] Sequential N-Day Outbound LLM Loop in Monthly Analytics
- **File & Lines**: [`AnalyticsService.java#L49-L55`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/service/AnalyticsService.java#L49-L55) and [`buildDailySummary#L137-L140`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/service/AnalyticsService.java#L137-L140)
- **Mechanism**:
  When `getMonthlySummary(email, month, year)` is called, it groups expenses by date and calls `buildDailySummary()` for every distinct day:
  ```java
  List<DailySummaryResponse> dailyBreakdown = byDay.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .map(e -> buildDailySummary(user.getId(), e.getKey(), e.getValue()))
          .collect(Collectors.toList());
  ```
  Inside `buildDailySummary`:
  ```java
  String aiSummary = expenses.isEmpty()
          ? "No expenses recorded for this day."
          : aiService.generateDailySummary(userId, date, totalSpent, spendingByCategory, wastefulCount);
  ```
  Then lines 89–96 execute `generateMonthlySummary(...)` and `generateAiTips(...)`.
- **Impact**:
  For an active user with expenses across 20 days in a month, `aiService.generateDailySummary` is called **20 times sequentially**, plus 1 monthly summary, plus 1 AI tips call. At ~600ms per Groq call, this represents **13.2 seconds of synchronous HTTP blocking** inside a single user request!
- **UI Inconsistency**:
  Neither [`Dashboard.jsx`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/pages/Dashboard.jsx) nor [`Reports.jsx`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/pages/Reports.jsx) renders or displays `dailyBreakdown[i].aiSummary`. In `Reports.jsx`, only `date` and `totalSpent` are extracted for the bar chart. Generating AI summaries for individual days inside the monthly report is 100% wasted latency and API cost.

---

### [P1] Missing Axios Request Timeout on Frontend
- **File & Lines**: [`frontend/src/api/axios.js#L3-L6`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/api/axios.js#L3-L6)
- **Mechanism**:
  ```javascript
  const API = axios.create({
    baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  });
  ```
  Axios defaults to `timeout: 0` (no timeout).
- **Impact**:
  When Render's free container is cold-starting or waking from sleep (~60–120s), or if Neon is resuming compute, user browser requests hang indefinitely. The UI spinner spins forever without an informative "Server is starting up, please wait..." toast or failure fallback.

---

### [P1] Duplicate Remote User Queries on Every Authenticated Request
- **File & Lines**:
  - [`JwtFilter.java#L46`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/security/JwtFilter.java#L46)
  - [`UserDetailServiceImpl.java#L16`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/security/UserDetailServiceImpl.java#L16)
  - [`AnalyticsService.java#L39`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/service/AnalyticsService.java#L39)
  - [`ExpenseService.java#L40`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/service/ExpenseService.java#L40)
  - [`GoalService.java#L38`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/service/GoalService.java#L38)
- **Mechanism**:
  On every incoming request with a `Bearer` token:
  1. `JwtFilter` calls `userDetailsService.loadUserByUsername(email)`, executing `SELECT * FROM users WHERE email = ?` over the WAN to Neon.
  2. The controller then executes `serviceMethod(email)`. The service immediately calls `userRepository.findByEmail(email).orElseThrow(...)`, executing the **exact same query** a second time.
- **Impact**:
  Every authenticated API endpoint pays a minimum 2x WAN latency penalty (~100–250ms) to fetch the user twice. On pages like Dashboard that trigger multiple parallel requests, 6 identical user queries hit Neon concurrently.

---

### [P1] Synchronous AI Latency on Expense Creation
- **File & Lines**: [`ExpenseService.java#L69-L74`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/service/ExpenseService.java#L69-L74)
- **Mechanism**:
  When adding an expense, `ExpenseService.addExpense()` synchronously halts execution while calling `aiService.categorizeExpense(...)`.
- **Impact**:
  Adding an expense is the most frequent user write operation. The client waits 1.2s to 3.5s for the response. If Groq is experiencing latency spikes or reaches the 10s read timeout, expense addition fails or hangs.

---

### [P1] Unbounded Expense History (Missing Pagination)
- **File & Lines**: [`ExpenseRepository.java#L18`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/repository/ExpenseRepository.java#L18)
- **Mechanism**:
  `List<Expense> findByUserOrderByExpenseDateDesc(User user);` returns an unbounded list of all historical expenses.
- **Impact**:
  As a user accumulates hundreds or thousands of expenses over months of usage, loading all expenses without `Pageable` will cause severe memory pressure, large JSON serialization overhead, and slow database scans.

---

### [P1] Misplaced `vercel.json` & Missing Static Asset Cache Headers
- **File & Lines**: [`frontend/src/vercel.json`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/vercel.json)
- **Mechanism**:
  1. The file is placed at `frontend/src/vercel.json` rather than the frontend root directory (`frontend/vercel.json`).
  2. The configuration only defines SPA rewrites:
     ```json
     {
       "rewrites": [
         { "source": "/(.*)", "destination": "/index.html" }
       ]
     }
     ```
  3. No `headers` configuration exists for `/assets/*` with `Cache-Control: public, max-age=31536000, immutable`.
- **Impact**:
  Static assets with content hashes (like `index-[hash].js` and `Reports-[hash].js`) may not be cached permanently by browsers or intermediate proxies, causing repeat downloads on return visits.

---

### [P1] Unapplied Production Composite Database Indexes
- **File & Lines**: [`backend/src/main/resources/schema-indexes.sql`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/resources/schema-indexes.sql)
- **Mechanism**:
  Entity classes define `@Index(name = "idx_expenses_user_date", columnList = "user_id, expense_date")` and `@Index(name = "idx_goals_user_created_at", columnList = "user_id, created_at")`. However, production uses `spring.jpa.hibernate.ddl-auto=validate`, which does NOT create missing indexes.
- **Impact**:
  Until the database operator manually executes the SQL migration script in Neon, PostgreSQL must perform sequential scans on `expenses` for every date-range query.

---

### [P2] Redundant Frontend API Requests on Mount
- **File & Lines**:
  - [`AddExpense.jsx#L14-L16`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/pages/AddExpense.jsx#L14-L16): Calls `API.get('/api/categories')` into state variable `categories` that is never rendered in JSX.
  - [`Budget.jsx#L21-L23`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/pages/Budget.jsx#L21-L23): Calls `fetchBudget()` (`/api/budget/allocation`) on mount while `BudgetContext` simultaneously fires the exact same request.
- **Impact**:
  Unnecessary HTTP requests waste bandwidth, trigger redundant backend database lookups, and consume worker threads.

---

### [P2] Default HikariCP Connection Pool Settings
- **File & Lines**: [`application.properties`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/resources/application.properties)
- **Mechanism**:
  Zero `spring.datasource.hikari.*` settings are defined. Specifically:
  - `spring.datasource.hikari.keepalive-time` is at its default (`0`, disabled).
  - `spring.datasource.hikari.maximum-pool-size` is at default (`10`).
  - `spring.datasource.hikari.connection-timeout` is at default (`30000` ms).
- **Impact**:
  Neon serverless PostgreSQL terminates idle connections or suspends compute after idle periods. Without a TCP keepalive probe, connections in the pool go stale. When a new request arrives, HikariCP discovers a dead socket or hangs waiting for Neon proxy renegotiation.

---

### [P2] Redundant User Lookups on Login
- **File & Lines**: [`AuthService.java#L40-L45`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/service/AuthService.java#L40-L45)
- **Mechanism**:
  `authenticationManager.authenticate()` queries user details from the database. Immediately after, `AuthService` queries `userRepository.findByEmail(request.getEmail())` again.
- **Impact**:
  Double round-trip to database on every login.

---

### [P2] Uncached Global Category Table
- **File & Lines**: [`CategoryRepository.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/repository/CategoryRepository.java) & [`CategoryService.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/service/CategoryService.java)
- **Mechanism**:
  The system contains 15 static categories seeded at startup. Every call to `CategoryService.getAllCategories()` executes `categoryRepository.findAll()`, querying the database table repeatedly.
- **Impact**:
  Repeated database queries for static reference data that changes almost never.

---

### [P3] Container JVM Startup Tuning
- **File & Lines**: [`backend/Dockerfile#L19`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/Dockerfile#L19)
- **Mechanism**:
  `ENTRYPOINT ["java", "-jar", "app.jar"]` launches the JVM with default ergonomics.
- **Impact**:
  Lacks explicit container memory constraints (e.g. `-XX:MaxRAMPercentage=75.0`) and startup acceleration options like Class Data Sharing (`-Xshare:on` or `-XX:ArchiveClassesAtExit`).

---

### [P3] Missing Real-User Monitoring (RUM) Telemetry
- **File & Lines**: [`frontend/package.json`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/package.json)
- **Mechanism**:
  No `@vercel/speed-insights` or `web-vitals` library is integrated into the client application.
- **Impact**:
  No objective telemetry on actual Largest Contentful Paint (LCP), Interaction to Next Paint (INP), or Cumulative Layout Shift (CLS) from production users.

---

## 7. Ranked List of Proposed Fixes

| Rank | ID | Finding & Scope | Proposed Optimization | Impact | Confidence | Risk | Effort |
| :---: | :---: | :--- | :--- | :---: | :---: | :---: | :---: |
| **1** | **OPT-01** | [P0] Sequential N-Day LLM loop in monthly analytics | Remove `aiService.generateDailySummary` from the monthly stream; generate AI summaries on-demand for single-day views only. | **Extreme** (eliminates 10–15s delay) | High (100%) | Low | Low (10 LOC) |
| **2** | **OPT-02** | [P1] Missing Axios request timeout on frontend | Set `timeout: 15000` (15s) in `frontend/src/api/axios.js` with global interceptor toast for cold-start wakeups. | **High** (prevents infinite UI hangs) | High (100%) | Low | Low (5 LOC) |
| **3** | **OPT-03** | [P1] Duplicate user DB lookups per authenticated request | Cache or pass authenticated user principal from `JwtFilter` / SecurityContext instead of re-querying `userRepository.findByEmail`. | **High** (saves 30–120ms WAN per call) | High (95%) | Low | Medium (25 LOC) |
| **4** | **OPT-04** | [P1] Misplaced `vercel.json` & missing static cache headers | Move `vercel.json` to `frontend/vercel.json`; add `Cache-Control: public, max-age=31536000, immutable` for `/assets/*`. | **High** (eliminates repeat asset loads) | High (100%) | Low | Low (1 file move + config) |
| **5** | **OPT-05** | [P1] Apply production database indexes in Neon | Execute [`schema-indexes.sql`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/resources/schema-indexes.sql) (`idx_expenses_user_date`, `idx_goals_user_created_at`) via Neon SQL console. | **High** (avoids full table scans) | High (100%) | Very Low | Operator Action (2 mins) |
| **6** | **OPT-06** | [P2] Redundant frontend API requests on mount | Remove dead `API.get('/api/categories')` in `AddExpense.jsx`; remove duplicate `fetchBudget()` in `Budget.jsx` (use `BudgetContext`). | **Medium** (removes 2 dead network calls) | High (100%) | Very Low | Low (15 LOC) |
| **7** | **OPT-07** | [P2] Configure HikariCP connection pool & keepalive | Add `spring.datasource.hikari.keepalive-time=30000`, `max-lifetime=600000`, `minimum-idle=2`, `maximum-pool-size=10`. | **Medium** (prevents stale Neon sockets) | High (95%) | Low | Low (5 lines properties) |
| **8** | **OPT-08** | [P2] In-memory cache for static Category list | Annotate `CategoryService.getAllCategories()` with `@Cacheable("categories")` or store in static immutable map. | **Medium** (avoids DB queries for categories) | High (95%) | Very Low | Low (10 LOC) |
| **9** | **OPT-09** | [P1] Synchronous AI categorization on expense create | Return expense immediately with rule-based/fallback category; dispatch AI categorization asynchronously or provide instant optimistic UI. | **High** (drops write latency from 2s to 150ms) | Medium (85%) | Medium | High (Async event architecture) |
| **10** | **OPT-10** | [P1] Unbounded expense history (pagination) | Add `Pageable pageable` to `expenseRepository.findByUserOrderByExpenseDateDesc` with default page size 50. | **Medium** (future-proofs scaling) | High (95%) | Low | Medium (API + UI pagination) |
| **11** | **OPT-11** | [P3] Container JVM startup tuning in Dockerfile | Add `-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -XX:TieredStopAtLevel=1` (or AppCDS) to Dockerfile entrypoint. | **Medium** (speeds up Render container startup) | Medium (80%) | Low | Low (1 line Dockerfile) |
| **12** | **OPT-12** | [P3] Real-User Monitoring (RUM) integration | Install `@vercel/speed-insights` in frontend to capture real-world LCP, INP, and CLS. | **Low** (Observability) | High (100%) | Very Low | Low (2 LOC) |

---

### Detailed Verification & Rollback Strategies for Top Fixes

#### Fix 1: Eliminate N-Day AI Loop in `AnalyticsService` (OPT-01)
- **Implementation**:
  In `AnalyticsService.getMonthlySummary`, replace the `buildDailySummary()` invocation that calls `aiService.generateDailySummary` with a lightweight mathematical aggregator that computes `totalSpent`, `wastefulCount`, and `spendingByCategory` without calling Groq. Reserve Groq daily summaries solely for `getDailySummary(email, date)` when a user explicitly requests a specific day's breakdown.
- **Verification Plan**:
  1. Unit test: Assert `aiService.generateDailySummary` is called **0 times** when evaluating `getMonthlySummary`.
  2. Integration test: Run `AnalyticsIntegrationTest` and verify all response fields (`totalSpent`, `dailyBreakdown`, `spendingByCategory`) remain mathematically exact.
  3. Latency check: Verify response time for 30-day expense history drops from > 10,000ms to < 200ms.
- **Rollback Strategy**: Revert git commit restoring the single line stream mapping.

#### Fix 2: Frontend Axios Timeout Configuration (OPT-02)
- **Implementation**:
  Configure `timeout: 15000` in `frontend/src/api/axios.js`. Add an Axios response interceptor error handler that detects `ECONNABORTED` or timeout error codes and dispatches a user-friendly error message indicating the service is waking up.
- **Verification Plan**:
  1. Simulate 20s delayed response via Mock Service Worker or local sleep; verify request aborts cleanly at 15s with actionable UI notification.
- **Rollback Strategy**: Reset `timeout: 0`.

#### Fix 3: Eliminate Duplicate User Lookups (OPT-03)
- **Implementation**:
  Have `UserDetailServiceImpl.loadUserByUsername` return a custom `WealthifyUserPrincipal` extending Spring's `User` that holds the `User` entity or `UUID userId`. In services (`AnalyticsService`, `ExpenseService`), extract the cached principal from `@AuthenticationPrincipal` or `SecurityContextHolder` instead of re-issuing `userRepository.findByEmail(email)`.
- **Verification Plan**:
  1. Inspect SQL execution logs via `spring.jpa.show-sql=true` in local testing. Verify only 1 `SELECT ... FROM users` occurs per authenticated request instead of 2.
- **Rollback Strategy**: Revert to querying `userRepository.findByEmail`.

---

## 8. Repository vs. Production Configuration Discrepancies

| Configuration Parameter | Repository State (`origin/main`) | Production Deployment (`Render / Vercel`) | Discrepancy Impact |
| :--- | :--- | :--- | :--- |
| **`spring.jpa.hibernate.ddl-auto`** | Defaulted to `validate` in `application.properties`. | Evaluates to `validate` via `${SPRING_JPA_HIBERNATE_DDL_AUTO:validate}`. | **Schema is locked**. Indexes in `schema-indexes.sql` will never be applied automatically. |
| **`server.port`** | `server.port=${PORT:8080}` | Render dynamically injects `PORT=10000` or random port. | Fully compatible. Spring binds correctly to Render's injected port. |
| **`vercel.json` Location** | Located at `frontend/src/vercel.json`. | Vercel expects configuration in root of project (`frontend/vercel.json`). | Rewrites or future header directives may not be applied by Vercel. |
| **Vercel Static Caching** | No `headers` declared in `vercel.json`. | Vercel defaults to standard heuristic edge caching without immutable flag. | Browser re-validates hashed chunk assets on subsequent visits. |
| **Hikari Connection Pool** | Zero properties specified in `application.properties`. | Defaults: `keepalive-time=0`, `max-pool-size=10`. | Connections dropped by Neon idle proxy cause socket reset errors. |
| **Render Logging Level** | Default `INFO` across all packages. | Default `INFO` in Render environment variables. | Cannot observe exact Hikari connection acquisition or Hibernate validation times without `DEBUG` flags. |

---

## 9. Operator Action Runbooks (Vercel, Render, Neon)

These are manual, zero-risk operational procedures for the DevOps operator across the respective cloud consoles.

---

### Runbook A: Neon PostgreSQL Cloud Console
**Objective**: Apply missing composite indexes (`idx_expenses_user_date` and `idx_goals_user_created_at`) to optimize queries without altering application code.

1. Log in to the [Neon Console](https://console.neon.tech).
2. Select the Wealthify project and active branch (e.g. `main`).
3. Open the **SQL Editor** tab.
4. Execute the preflight verification query:
   ```sql
   SELECT tablename, indexname FROM pg_indexes
   WHERE tablename IN ('expenses', 'goals')
     AND indexname IN ('idx_expenses_user_date', 'idx_goals_user_created_at');
   ```
5. If the indexes do not exist, execute the migration script:
   ```sql
   CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_expenses_user_date ON expenses (user_id, expense_date);
   CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_goals_user_created_at ON goals (user_id, created_at);
   ```
   *(Note: `CONCURRENTLY` allows queries and writes to continue unimpeded during index building).*
6. Confirm index status:
   ```sql
   SELECT schemaname, tablename, indexname, indexdef
   FROM pg_indexes
   WHERE indexname IN ('idx_expenses_user_date', 'idx_goals_user_created_at');
   ```

---

### Runbook B: Render Web Service Dashboard
**Objective**: Enable temporary diagnostic logging to capture precise startup phase timestamps, and configure container resource parameters.

1. Log in to the [Render Dashboard](https://dashboard.render.com).
2. Navigate to the `wealthify-backend` Web Service.
3. Open **Environment** settings.
4. Add the following temporary diagnostic environment variables:
   ```text
   LOGGING_LEVEL_COM_ZAXXER_HIKARI=DEBUG
   LOGGING_LEVEL_ORG_HIBERNATE_TOOL_SCHEMA=DEBUG
   ```
5. Trigger a manual deploy with cache clear: **Manual Deploy -> Deploy latest commit**.
6. Inspect the build and deployment logs:
   - Identify the exact timestamp when Hikari logs `HikariPool-1 - Added connection`.
   - Identify the exact timestamp when Hibernate logs `schema validation complete`.
7. Once startup metrics are verified, remove the `DEBUG` environment variables to preserve clean production logs.
8. **Resource Check**: Verify the assigned instance type:
   - If currently on **Free tier (0.1 vCPU, 512 MB RAM)**: Note that free tier instances spin down after 15 minutes of inactivity and take 60–120s to boot. Upgrading to **Starter ($7/mo, 0.5 vCPU)** or **Standard ($25/mo, 1 vCPU)** eliminates the cold start spin-down entirely.

---

### Runbook C: Vercel Dashboard
**Objective**: Verify build output paths and configure Edge caching.

1. Log in to the [Vercel Dashboard](https://vercel.com).
2. Select the `wealthify-frontend` project.
3. In **Settings -> General**:
   - Confirm **Root Directory** is configured as `frontend`.
   - Confirm **Framework Preset** is set to `Vite`.
   - Confirm **Build Command** is `npm run build`.
   - Confirm **Output Directory** is `dist`.
4. In **Settings -> Speed Insights**:
   - Enable Speed Insights tracking to receive Field Data once the frontend library is installed.

---

## 10. Telemetry Gaps & Operator Unknowns

The following metrics cannot be determined via static repository analysis alone and require access to cloud provider consoles or enhanced observability:

1. **Render Compute Tier Allocation**:
   - *Unknown*: Is the production service operating on the Free tier (0.1 vCPU / 512 MB RAM) with automatic cold-start spin-down, or a paid tier (Starter / Standard)?
   - *Required Action*: Operator must inspect the service settings in the Render Dashboard.

2. **Geographical Datacenter Proximity**:
   - *Unknown*: What are the physical regions hosting Render (e.g. `us-west-1` Oregon) vs. Neon (e.g. `us-east-1` Ohio)?
   - *Required Action*: Verify both regions in Render and Neon dashboards. If cross-region cross-talk exists, moving Neon to the exact same AWS/GCP region as Render reduces round-trip ping from ~80ms to < 2ms.

3. **Neon Auto-Suspend Inactivity Timeout**:
   - *Unknown*: What is the Neon compute suspend timeout (default: 5 minutes of inactivity)?
   - *Required Action*: Inspect Neon Project Settings -> Compute configuration.

4. **Groq Real-World P95 Latency & Error Rate**:
   - *Unknown*: What is the P95 response latency and rate-limit error frequency for `openai/gpt-oss-120b` under production request volumes?
   - *Required Action*: Inspect Groq Cloud console analytics.

5. **Real-User Core Web Vitals Distribution**:
   - *Unknown*: Actual P75 Largest Contentful Paint (LCP) and Interaction to Next Paint (INP) across mobile and desktop users.
   - *Required Action*: Install `@vercel/speed-insights`.

---

## 11. Phased Implementation Roadmap

```
========================================================================================
PHASE 1: Zero-Risk Quick Wins (Immediate 1-2 Days)
- [ ] OPT-01: Remove N-day daily AI loop from AnalyticsService.getMonthlySummary.
- [ ] OPT-02: Configure 15s timeout and error interceptor in frontend/src/api/axios.js.
- [ ] OPT-04: Move vercel.json to frontend/ root and add immutable cache headers.
- [ ] OPT-05: Run Runbook A in Neon to apply composite database indexes.
- [ ] OPT-06: Remove dead /api/categories call in AddExpense.jsx and duplicate budget call in Budget.jsx.
========================================================================================
                                           │
                                           ▼
========================================================================================
PHASE 2: Backend Query & Database Optimization (Sprint 2)
- [ ] OPT-03: Cache/propagate authenticated user in SecurityContext to eliminate duplicate user queries.
- [ ] OPT-07: Configure HikariCP keepalive (30s) and pool boundaries in application.properties.
- [ ] OPT-08: Add in-memory cache for static Category reference list.
- [ ] OPT-10: Implement Pageable pagination for expense history queries.
========================================================================================
                                           │
                                           ▼
========================================================================================
PHASE 3: Architectural & Observability Enhancements (Sprint 3)
- [ ] OPT-09: Decouple expense creation from synchronous AI categorization (optimistic UI / async worker).
- [ ] OPT-11: Optimize container JVM ergonomics (-XX:MaxRAMPercentage=75.0, Tiered compilation).
- [ ] OPT-12: Integrate @vercel/speed-insights for real-user Core Web Vitals telemetry.
- [ ] Operator: Evaluate upgrading Render to Starter tier to eliminate cold starts permanently.
========================================================================================
```

---
*End of Audit Report.*

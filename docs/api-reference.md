# Wealthify REST API Reference

This document details all 22 application REST endpoints and 3 Actuator health endpoints provided by the Wealthify Spring Boot backend.

Every endpoint below has been verified directly against controller source files in `backend/src/main/java/com/Wealthify/backend/controller/`.

---

## Authentication & Headers

Protected endpoints require a valid JSON Web Token (JWT) passed via standard HTTP Bearer authentication:

```http
Authorization: Bearer <your_jwt_token>
```

Tokens are obtained by logging in via `POST /api/auth/login`. Unauthenticated requests to protected endpoints return `401 Unauthorized` or `403 Forbidden`.

---

## 1. Authentication Endpoints (`/api/auth`)

Controller: [`AuthController.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/controller/AuthController.java)

### Register User
- **Method**: `POST`
- **Path**: `/api/auth/register`
- **Access**: Public
- **Request Body**:
  ```json
  {
    "name": "Rishabh",
    "email": "user@example.com",
    "password": "SecurePassword123!",
    "monthlyIncome": 25000.00
  }
  ```
- **Response** (`200 OK`): String confirmation message (`"User registered successfully"`).
- **Validation**: Enforces strict password requirements (min 8 characters, upper/lower/digit/special) and unique email.

### Login
- **Method**: `POST`
- **Path**: `/api/auth/login`
- **Access**: Public
- **Request Body**:
  ```json
  {
    "email": "user@example.com",
    "password": "SecurePassword123!"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "name": "Rishabh",
    "email": "user@example.com",
    "monthlyIncome": 25000.00
  }
  ```

### Forgot Password (Trigger OTP)
- **Method**: `POST`
- **Path**: `/api/auth/forgot-password`
- **Access**: Public (Protected by in-memory `OtpRateLimiter` per IP and email)
- **Request Body**:
  ```json
  {
    "email": "user@example.com"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "message": "If the account exists, a secure OTP code has been sent to your inbox."
  }
  ```
- **Behavior**: Uses non-enumerating generic response. If the user exists, sends a 6-digit OTP code to the provided email via Spring Mail (Gmail SMTP).

### Reset Password (Verify OTP)
- **Method**: `POST`
- **Path**: `/api/auth/reset-password`
- **Access**: Public (Protected by verification lockout limits)
- **Request Body**:
  ```json
  {
    "email": "user@example.com",
    "otp": "482910",
    "newPassword": "NewSecurePassword123!"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "message": "Password reset successful."
  }
  ```

---

## 2. Expenses Endpoints (`/api/expenses`)

Controller: [`ExpenseController.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/controller/ExpenseController.java)

### Add Expense (AI-Categorized)
- **Method**: `POST`
- **Path**: `/api/expenses`
- **Access**: Authenticated
- **Request Body**:
  ```json
  {
    "title": "Swiggy dinner order",
    "amount": 350.00,
    "date": "2026-10-15",
    "categoryName": "Food & Dining",
    "notes": "Late night study session"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "id": "e4b3e8a0-2f9b-4e89-a2e6-81cf43c683b1",
    "title": "Swiggy dinner order",
    "amount": 350.00,
    "expenseDate": "2026-10-15",
    "category": "Food & Dining",
    "type": "WANT",
    "wasteful": false,
    "confidence": 0.95,
    "reason": "Dining out rather than home cooking; discretionary food expenditure",
    "notes": "Late night study session"
  }
  ```
- **AI Integration**: Automatically queries Groq LLM to categorize the expense, determine spending type (`NEED` vs. `WANT`), and evaluate wastefulness based on remaining monthly budget. Returns safe fallback defaults if Groq is slow or unreachable.

### Get Today's Expenses
- **Method**: `GET`
- **Path**: `/api/expenses/today`
- **Access**: Authenticated
- **Response** (`200 OK`): Array of `ExpenseResponse` records for the current date.

### Get Expenses by Specific Date
- **Method**: `GET`
- **Path**: `/api/expenses/date/{date}`
- **Access**: Authenticated
- **Path Parameter**: `date` (`YYYY-MM-DD`)
- **Response** (`200 OK`): Array of `ExpenseResponse` records for the specified date.

### Get Expenses by Month
- **Method**: `GET`
- **Path**: `/api/expenses/month/{year}/{month}`
- **Access**: Authenticated
- **Path Parameters**:
  - `year` (Integer: 1970–2100)
  - `month` (Integer: 1–12)
- **Response** (`200 OK`): Array of `ExpenseResponse` records matching the year and month, sorted by date descending using index `idx_expenses_user_date`.

### Delete Expense
- **Method**: `DELETE`
- **Path**: `/api/expenses/{id}`
- **Access**: Authenticated
- **Path Parameter**: `id` (UUID)
- **Response** (`200 OK`): String confirmation (`"Deleted successfully"`). Enforces user ownership.

---

## 3. Budget & Income Endpoints

Controllers: [`GoalController.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/controller/GoalController.java), [`InsightController.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/controller/InsightController.java)

### Configure Budget Allocation
- **Method**: `POST`
- **Path**: `/api/budget/setup`
- **Access**: Authenticated
- **Request Body**:
  ```json
  {
    "monthlyIncome": 30000.00,
    "savingPercentage": 20.0,
    "investmentPercentage": 10.0
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "monthlyIncome": 30000.00,
    "needsBudget": 15000.00,
    "wantsBudget": 9000.00,
    "savingsBudget": 6000.00,
    "totalSpent": 12450.00,
    "remainingBudget": 17550.00,
    "budgetStatus": "HEALTHY"
  }
  ```

### Get Budget Allocation & Status
- **Method**: `GET`
- **Path**: `/api/budget/allocation`
- **Access**: Authenticated
- **Response** (`200 OK`): `BudgetAllocationResponse` containing target split, current spent totals, remaining budget, and status (`HEALTHY`, `CAUTION`, `CRITICAL`).

### Update Monthly Income Directly
- **Method**: `POST`
- **Path**: `/api/user/income`
- **Access**: Authenticated
- **Request Body**:
  ```json
  {
    "income": 35000.00
  }
  ```
- **Response** (`200 OK`): String confirmation message. Automatically evicts cached user income lookups.

---

## 4. Savings Goals Endpoints (`/api/goals`)

Controller: [`GoalController.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/controller/GoalController.java)

### Create Savings Goal
- **Method**: `POST`
- **Path**: `/api/goals`
- **Access**: Authenticated
- **Request Body**:
  ```json
  {
    "title": "New Laptop for College",
    "targetAmount": 65000.00,
    "targetDate": "2027-04-30"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "title": "New Laptop for College",
    "targetAmount": 65000.00,
    "savedAmount": 0.00,
    "targetDate": "2027-04-30",
    "status": "IN_PROGRESS",
    "aiAdvice": "To reach ₹65,000 by April 2027, allocate ₹3,611 monthly from your savings quota."
  }
  ```

### List Goals
- **Method**: `GET`
- **Path**: `/api/goals`
- **Access**: Authenticated
- **Response** (`200 OK`): Array of `GoalResponse` records sorted by `created_at DESC` via index `idx_goals_user_created_at`.

### Contribute to Goal Savings
- **Method**: `PATCH`
- **Path**: `/api/goals/{id}/save?amount=2500.00`
- **Access**: Authenticated
- **Query Parameter**: `amount` (Positive decimal number)
- **Response** (`200 OK`): Updated `GoalResponse` record. If `savedAmount >= targetAmount`, status transitions to `COMPLETED`.

### Delete Goal
- **Method**: `DELETE`
- **Path**: `/api/goals/{id}`
- **Access**: Authenticated
- **Path Parameter**: `id` (UUID)
- **Response** (`200 OK`): String confirmation (`"Goal deleted successfully"`).

---

## 5. Analytics Endpoints (`/api/analytics`)

Controller: [`AnalyticsController.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/controller/AnalyticsController.java)

### Daily Analytics Summary
- **Method**: `GET`
- **Path**: `/api/analytics/daily`
- **Access**: Authenticated
- **Query Parameter**: `date` (`YYYY-MM-DD`, optional, defaults to current date)
- **Response** (`200 OK`):
  ```json
  {
    "date": "2026-10-15",
    "totalSpent": 450.00,
    "expenseCount": 2,
    "byCategory": {
      "Food & Dining": 350.00,
      "Transportation": 100.00
    },
    "wastefulCount": 0,
    "wastefulAmount": 0.00,
    "aiSummary": "Spending remained within daily limits. No wasteful expenses detected."
  }
  ```

### Monthly Analytics Summary
- **Method**: `GET`
- **Path**: `/api/analytics/monthly`
- **Access**: Authenticated
- **Query Parameters**:
  - `month` (Integer: 1–12, optional, defaults to current month)
  - `year` (Integer: 1970–2100, optional, defaults to current year)
- **Response** (`200 OK`):
  ```json
  {
    "month": 10,
    "year": 2026,
    "totalSpent": 12450.00,
    "monthlyIncome": 30000.00,
    "savingsAmount": 17550.00,
    "budgetStatus": "HEALTHY",
    "topCategories": {
      "Food & Dining": 5200.00,
      "Books & Academic": 3100.00,
      "Entertainment": 2150.00
    },
    "dailyBreakdown": [
      {
        "date": "2026-10-01",
        "totalSpent": 420.00,
        "expenseCount": 2,
        "wastefulCount": 0
      }
    ]
  }
  ```
- **Performance Optimization**: `getMonthlySummary` computes the daily breakdown purely in-memory across the month's expenses, eliminating up to 31 synchronous external Groq AI requests per monthly request.

---

## 6. Insights & Stock Advisory Endpoints

Controller: [`InsightController.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/controller/InsightController.java)

### Wasteful Spending Analysis
- **Method**: `GET`
- **Path**: `/api/insights/wasteful`
- **Access**: Authenticated
- **Query Parameters**: `month`, `year` (optional)
- **Response** (`200 OK`):
  ```json
  {
    "totalWastefulAmount": 1850.00,
    "wastefulPercentage": 14.8,
    "wastefulTransactionCount": 4,
    "wastefulByCategory": {
      "Entertainment": 1200.00,
      "Food & Dining": 650.00
    },
    "wastefulExpenses": [ ... ],
    "aiRecommendations": [
      "Reduce weekend online food orders to save approximately ₹1,500 monthly.",
      "Review active subscription services that were tagged as non-essential wants."
    ],
    "aiSummary": "Wasteful spending represents 14.8% of your total expenditure this month."
  }
  ```

### Stock & Investment Advisory
- **Method**: `GET`
- **Path**: `/api/stocks/recommend`
- **Access**: Authenticated
- **Query Parameters**: `month`, `year` (optional)
- **Response** (`200 OK`):
  ```json
  {
    "surplusAmount": 6000.00,
    "riskAppetite": "MODERATE",
    "recommendations": [
      {
        "assetClass": "Index ETF (Nifty 50)",
        "instrument": "NIFTYBEES / UTI Nifty 50 Index Fund",
        "allocationPercentage": 50.0,
        "recommendedAmount": 3000.00,
        "rationale": "Low-cost foundational equity exposure suitable for long-term student investment."
      },
      {
        "assetClass": "Gold ETF / Sovereign Gold Bonds",
        "instrument": "GOLDBEES",
        "allocationPercentage": 20.0,
        "recommendedAmount": 1200.00,
        "rationale": "Hedge against inflation with liquid exposure to gold."
      },
      {
        "assetClass": "Liquid Mutual Funds / High-Yield Savings",
        "instrument": "Overnight / Liquid Debt Funds",
        "allocationPercentage": 30.0,
        "recommendedAmount": 1800.00,
        "rationale": "Emergency reserve fund maintaining capital safety and same-day liquidity."
      }
    ],
    "disclaimer": "Informational educational suggestions tailored for student budgets. Not formal SEBI-registered financial advisory."
  }
  ```

---

## 7. Expense Categories Endpoints (`/api/categories`)

Controller: [`CategoryController.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/controller/CategoryController.java)

### List All Categories
- **Method**: `GET`
- **Path**: `/api/categories`
- **Access**: Authenticated
- **Response** (`200 OK`): Array of standard and custom categories (`id`, `name`, `icon`, `type`).

### Create Custom Category
- **Method**: `POST`
- **Path**: `/api/categories`
- **Access**: Authenticated
- **Request Body**:
  ```json
  {
    "name": "Hostel Supplies",
    "icon": "home",
    "type": "NEED"
  }
  ```
- **Response** (`200 OK`): Created `CategoryDto`.

---

## 8. System Health Probes (`/actuator/health`)

Configuration: [`SecurityConfig.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/config/SecurityConfig.java), [`application.properties`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/resources/application.properties)

| Endpoint | Access | Probe Group | Components Evaluated | Response Payload |
| :--- | :--- | :--- | :--- | :--- |
| `GET /actuator/health` | Public | Default | Aggregated health | `{"status": "UP"}` |
| `GET /actuator/health/readiness` | Public | **Readiness** | `readinessState`, `db` (Neon Postgres) | `{"status": "UP"}` or `503 Service Unavailable` |
| `GET /actuator/health/liveness` | Public | **Liveness** | `livenessState` (JVM only) | `{"status": "UP"}` |

> [!NOTE]
> Output sanitization is enforced (`management.endpoint.health.show-details=never`). Responses deliberately suppress hostnames, usernames, disk space, and database connection details to prevent information leakage.

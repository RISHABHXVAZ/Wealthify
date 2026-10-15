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

Controller: [`AuthController.java`](../backend/src/main/java/com/Wealthify/backend/controller/AuthController.java)

### Register User
- **Method**: `POST`
- **Path**: `/api/auth/register`
- **Access**: Public
- **Request Body**:
  ```json
  {
    "name": "Rishabh",
    "email": "user@example.com",
    "password": "SecurePassword123!"
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
    "email": "user@example.com"
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

Controller: [`ExpenseController.java`](../backend/src/main/java/com/Wealthify/backend/controller/ExpenseController.java)

### Add Expense (AI-Categorized)
- **Method**: `POST`
- **Path**: `/api/expenses`
- **Access**: Authenticated
- **Request Body**:
  ```json
  {
    "amount": 350.00,
    "description": "Swiggy dinner order",
    "categoryId": 1,
    "expenseDate": "2026-10-15",
    "splitCount": 1
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "id": "e4b3e8a0-2f9b-4e89-a2e6-81cf43c683b1",
    "amount": 350.00,
    "description": "Swiggy dinner order",
    "category": {
      "id": 1,
      "name": "Food & Dining",
      "type": "WANT",
      "isEssential": false
    },
    "aiCategoryConfidence": 0.95,
    "isFlaggedWasteful": false,
    "flaggedWasteful": false,
    "aiReason": "Dining out rather than home cooking; discretionary food expenditure",
    "expenseDate": "2026-10-15",
    "createdAt": "2026-10-15T21:30:00"
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

Controllers: [`GoalController.java`](../backend/src/main/java/com/Wealthify/backend/controller/GoalController.java), [`InsightController.java`](../backend/src/main/java/com/Wealthify/backend/controller/InsightController.java)

### Configure Budget Allocation
- **Method**: `POST`
- **Path**: `/api/budget/setup`
- **Access**: Authenticated
- **Request Body**:
  ```json
  {
    "monthlyIncome": 10000.00,
    "savingPercentage": 20.0,
    "investmentPercentage": 10.0
  }
  ```
- **Allocation Formula**:
  - `savingPercentage`: Percentage of total income targeted for savings (`monthlyIncome * savingPercentage / 100`, e.g., 20% of ₹10,000 = ₹2,000).
  - `investmentPercentage`: Percentage of the monthly savings targeted for investments (`monthlySavingAmount * investmentPercentage / 100`, e.g., 10% of ₹2,000 = ₹200).
  - `availableForExpenditure`: Total income remaining after savings quota (`monthlyIncome - monthlySavingAmount`, e.g., ₹8,000).
- **Response** (`200 OK`):
  ```json
  {
    "monthlyIncome": 10000.00,
    "savingPercentage": 20.0,
    "investmentPercentage": 10.0,
    "monthlySavingAmount": 2000.00,
    "monthlyInvestmentAmount": 200.00,
    "availableForExpenditure": 8000.00,
    "spentThisMonth": 7398.00,
    "remainingBudget": 602.00,
    "isOverBudget": false,
    "wastefulThisMonth": 3599.00,
    "budgetUsedPercentage": 92.48,
    "aiAdvice": "Your spending is within limits. Consider curbing food deliveries to preserve your savings target."
  }
  ```

### Get Budget Allocation & Status
- **Method**: `GET`
- **Path**: `/api/budget/allocation`
- **Access**: Authenticated
- **Response** (`200 OK`): `BudgetAllocationResponse` containing target split, current spent totals, remaining budget, wasteful spending total, and budget usage status.

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

Controller: [`GoalController.java`](../backend/src/main/java/com/Wealthify/backend/controller/GoalController.java)

### Create Savings Goal
- **Method**: `POST`
- **Path**: `/api/goals`
- **Access**: Authenticated
- **Request Body**:
  ```json
  {
    "itemName": "New Laptop for College",
    "targetAmount": 65000.00,
    "targetDate": "2027-04-30"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "itemName": "New Laptop for College",
    "targetAmount": 65000.00,
    "targetDate": "2027-04-30",
    "currentSaved": 0.00,
    "status": "ACTIVE",
    "aiPlan": "To reach ₹65,000 by April 2027, allocate ₹3,611 monthly from your savings quota.",
    "requiredMonthlySaving": 3611.11,
    "monthsRemaining": 18,
    "progressPercentage": 0.0,
    "isAchievable": true,
    "achievabilityReason": "Required monthly saving fits within projected savings allocation."
  }
  ```

### List Goals
- **Method**: `GET`
- **Path**: `/api/goals`
- **Access**: Authenticated
- **Response** (`200 OK`): Array of `GoalResponse` records sorted by `created_at DESC` via index `idx_goals_user_created_at`.

### Contribute to Goal Savings
- **Method**: `PATCH`
- **Path**: `/api/goals/{id}/save`
- **Access**: Authenticated
- **Query Parameter**: `amount` (Positive decimal number, e.g., `/api/goals/{id}/save?amount=2500.00`)
- **Response** (`200 OK`): Updated `GoalResponse` record. If `currentSaved >= targetAmount`, status transitions to `ACHIEVED`.

### Delete Goal
- **Method**: `DELETE`
- **Path**: `/api/goals/{id}`
- **Access**: Authenticated
- **Path Parameter**: `id` (UUID)
- **Response** (`200 OK`): String confirmation (`"Goal deleted successfully"`).

---

## 5. Analytics Endpoints (`/api/analytics`)

Controller: [`AnalyticsController.java`](../backend/src/main/java/com/Wealthify/backend/controller/AnalyticsController.java)

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
    "totalTransactions": 2,
    "spendingByCategory": {
      "Food & Dining": 350.00,
      "Transportation": 100.00
    },
    "expenses": [
      {
        "id": "e4b3e8a0-2f9b-4e89-a2e6-81cf43c683b1",
        "description": "Swiggy dinner order",
        "amount": 350.00,
        "categoryName": "Food & Dining",
        "expenseDate": "2026-10-15",
        "flaggedWasteful": false
      }
    ],
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
    "totalIncome": 30000.00,
    "savingsAmount": 17550.00,
    "savingsPercentage": 58.5,
    "totalTransactions": 14,
    "spendingByCategory": {
      "Food & Dining": 5200.00,
      "Books & Academic": 3100.00,
      "Entertainment": 2150.00
    },
    "dailyBreakdown": [
      {
        "date": "2026-10-01",
        "totalSpent": 420.00,
        "totalTransactions": 2,
        "spendingByCategory": {
          "Food & Dining": 420.00
        },
        "expenses": [],
        "wastefulCount": 0,
        "wastefulAmount": 0.00,
        "aiSummary": null
      }
    ],
    "wastefulAmount": 1850.00,
    "wastefulPercentage": 14.86,
    "aiSummary": "Discretionary spending accounted for 14.8% of your expenses.",
    "aiTips": [
      "Limit weekend food delivery to reduce non-essential spending."
    ]
  }
  ```
- **Performance Optimization**: `getMonthlySummary` computes the daily breakdown purely in-memory across the month's expenses, eliminating up to 31 synchronous external Groq AI requests per monthly request.

---

## 6. Insights & Stock Advisory Endpoints

Controller: [`InsightController.java`](../backend/src/main/java/com/Wealthify/backend/controller/InsightController.java)

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
    "monthlyIncome": 10000.00,
    "totalMonthlyExpenses": 7398.00,
    "investableSurplus": 2602.00,
    "savingsRate": 26.02,
    "recommendations": [
      {
        "ticker": "NIFTYBEES",
        "name": "Nippon India ETF Nifty 50 BeES",
        "type": "ETF",
        "riskLevel": "LOW",
        "reason": "Foundational low-cost passive index exposure ideal for disciplined monthly SIPs.",
        "suggestedAllocation": "50% of investable surplus (~₹1,300/mo)"
      },
      {
        "ticker": "GOLDBEES",
        "name": "Nippon India ETF Gold BeES",
        "type": "ETF",
        "riskLevel": "LOW",
        "reason": "Hedge against inflation with liquid gold investment.",
        "suggestedAllocation": "20% of investable surplus (~₹520/mo)"
      }
    ],
    "aiRationale": "Based on a monthly income of ₹10,000 and ₹2,602 surplus, a conservative 50/20 index-and-gold allocation provides stable compounding without excessive volatility."
  }
  ```

---

## 7. Expense Categories Endpoints (`/api/categories`)

Controller: [`CategoryController.java`](../backend/src/main/java/com/Wealthify/backend/controller/CategoryController.java)

### List All Categories
- **Method**: `GET`
- **Path**: `/api/categories`
- **Access**: Authenticated
- **Response** (`200 OK`): Array of standard and custom categories (`id`, `name`, `type`, `isEssential`).
  ```json
  [
    {
      "id": 1,
      "name": "Food & Dining",
      "type": "WANT",
      "isEssential": false
    },
    {
      "id": 2,
      "name": "Academic & Books",
      "type": "NEED",
      "isEssential": true
    }
  ]
  ```

### Create Custom Category
- **Method**: `POST`
- **Path**: `/api/categories`
- **Access**: Authenticated
- **Request Body**:
  ```json
  {
    "name": "Hostel Supplies",
    "type": "NEED",
    "isEssential": true
  }
  ```
- **Response** (`200 OK`): Created `CategoryDto`.

---

## 8. System Health Probes (`/actuator/health`)

Configuration: [`SecurityConfig.java`](../backend/src/main/java/com/Wealthify/backend/config/SecurityConfig.java), [`application.properties`](../backend/src/main/resources/application.properties)

| Endpoint | Access | Probe Group | Components Evaluated | Response Payload |
| :--- | :--- | :--- | :--- | :--- |
| `GET /actuator/health` | Public | Default | Aggregated health | `{"status": "UP"}` |
| `GET /actuator/health/readiness` | Public | **Readiness** | `readinessState`, `db` (Neon Postgres) | `{"status": "UP"}` or `503 Service Unavailable` |
| `GET /actuator/health/liveness` | Public | **Liveness** | `livenessState` (JVM only) | `{"status": "UP"}` |

> [!NOTE]
> Output sanitization is enforced (`management.endpoint.health.show-details=never`). Responses deliberately suppress hostnames, usernames, disk space, and database connection details to prevent information leakage.

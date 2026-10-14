# Wealthify Frontend

The client-side single-page application (SPA) for **Wealthify**, built with React 19, Vite 7, and Tailwind CSS v4.

For the comprehensive system architecture, backend instructions, and deployment details, see the root [Project README](../README.md).

---

## Tech Stack & Architecture

- **UI Framework**: React 19.2 + React Router v7 (declarative client-side routing)
- **Bundler & Dev Server**: Vite 7.3 with Fast Refresh
- **Styling**: Tailwind CSS v4 via `@tailwindcss/vite`
- **Charts & Visualizations**: Recharts 3.8
- **Icons**: Lucide React
- **HTTP Client**: Axios with custom interceptors for bounded request timeouts (15s default / 30s extended) and user-friendly error formatting
- **Cold-Start Resilience**: Adaptive backend readiness manager (`src/services/backendReadiness.js`) providing automated polling and status updates for Render free-tier cold starts
- **Real-User Monitoring**: Vercel Speed Insights (`@vercel/speed-insights/react`) tracking Core Web Vitals (LCP, INP, CLS)

### Route Architecture & Code Splitting

All application pages are loaded lazily via `React.lazy()` and `Suspense` to minimize the initial JavaScript payload:

| Route | Component | Chunk Description |
| :--- | :--- | :--- |
| `/login` | `Login.jsx` | User authentication & password reset trigger |
| `/register` | `Register.jsx` | New user onboarding |
| `/reset-password` | `ResetPassword.jsx` | OTP verification & credential update |
| `/dashboard` | `Dashboard.jsx` | Financial snapshot, budget progress, recent expenses |
| `/expenses` | `AddExpense.jsx` | Expense logging with synchronous AI categorization |
| `/budget` | `Budget.jsx` | 50/30/20 budget planner and category breakdown |
| `/goals` | `Goals.jsx` | Target savings goals and contribution tracking |
| `/reports` | `Reports.jsx` | Monthly analytics, category charts, trend evaluation |
| `/insights` | `Insights.jsx` | Wasteful spending analysis & savings recommendations |
| `/stocks` | `StockAdvisor.jsx` | Indian market stock and ETF investment guidance |

---

## Getting Started

### Prerequisites
- **Node.js**: `v20.x` or later (tested on Node v24 LTS)
- **npm**: `v10.x` or later

### 1. Environment Configuration

Copy the example environment template:

```bash
cp .env.example .env.local
```

For local development against a backend running on `localhost:8080`, no modifications are needed (`VITE_API_URL` defaults to `http://localhost:8080` if unset).

To target a remote backend (such as a staging or production Render instance):
```properties
VITE_API_URL=https://your-backend-service.onrender.com
```

> [!NOTE]
> Environment variables prefixed with `VITE_` are embedded into static JavaScript bundles at build time. Never place secrets or private credentials in frontend environment files.

### 2. Install Dependencies

```bash
npm install
```

### 3. Start Development Server

```bash
npm run dev
```

The application will start on `http://localhost:5173`.

---

## Available Scripts

| Script | Command | Purpose |
| :--- | :--- | :--- |
| **Development** | `npm run dev` | Starts the Vite development server with Hot Module Replacement (HMR). |
| **Test** | `npm test` | Runs the automated contract and unit tests using the Node.js native test runner (`node --test`). |
| **Lint** | `npm run lint` | Analyzes code quality using ESLint with React Hooks rules. |
| **Build** | `npm run build` | Compiles and optimizes assets into `dist/` with content-hashed chunks. |
| **Preview** | `npm run preview` | Serves the production build locally for verification. |

---

## Testing

The frontend test suite is located in `frontend/test/` and runs without third-party test framework overhead:

```bash
npm test
```

Test suites cover:
- **API Timeouts**: Validates 15-second default and 30-second extended timeouts and error messaging.
- **Backend Readiness**: Verifies cold-start detection, exponential backoff, retry exhaustion, and stale state revalidation.
- **Request Reductions**: Enforces avoidance of redundant network requests on page mounts.
- **Static Caching**: Confirms `vercel.json` immutable cache configuration for hashed assets.
- **Speed Insights**: Verifies telemetry component mounting and privacy safeguards.

---

## Production Deployment

The frontend is deployed to **Vercel** with static asset caching configured in [`vercel.json`](./vercel.json):
- Immutable long-term caching (`max-age=31536000, immutable`) for content-hashed assets under `/assets/*`.
- SPA fallback rewrite (`/* -> /index.html`) to support client-side routing.

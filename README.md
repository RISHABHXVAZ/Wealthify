# Wealthify

AI-powered expense tracker for Indian college students. Log expenses, get AI categorization, spot wasteful spending, and receive investment suggestions — all in one place.

---

## Tech Stack

**Frontend** — React 19, Vite, Tailwind CSS, Recharts  
**Backend** — Spring Boot 4, Java 21, PostgreSQL (Neon), JWT Auth  
**AI** — Groq API (LLaMA 3.3 70B)

---

## Features

- AI auto-categorizes every expense and flags wasteful spending
- Daily & monthly summaries with personalized tips
- Budget planner, savings goals, and stock/ETF recommendations for Indian markets
- Secure JWT-based auth

---

## Getting Started

### Backend

```bash
cd backend
# Set environment variables
export SPRING_DATASOURCE_URL=...
export SPRING_DATASOURCE_USERNAME=...
export SPRING_DATASOURCE_PASSWORD=...
export APP_JWT_SECRET=...
export GROQ_API_KEY=...

./mvnw spring-boot:run
```

Runs on `http://localhost:8080`

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on `http://localhost:5173`

---

## Key API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register |
| POST | `/api/auth/login` | Login → JWT |
| POST | `/api/expenses` | Add expense (AI-categorized) |
| GET | `/api/analytics/daily` | Daily summary |
| GET | `/api/analytics/monthly` | Monthly summary |
| GET | `/api/insights` | Spending tips |
| GET | `/api/insights/stock-advisor` | Investment suggestions |
| POST | `/api/goals` | Create savings goal |

---

## Contributing

Fork → branch → PR. All contributions welcome.

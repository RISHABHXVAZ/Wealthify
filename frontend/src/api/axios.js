import axios from 'axios';
import { backendReadiness, ReadinessStatus } from '../services/backendReadiness.js';

export const API_BASE_URL = (typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_URL) || 'http://localhost:8080';

const API = axios.create({
  // Uses your Vercel URL in production, or falls back to localhost during local testing
  baseURL: API_BASE_URL,
});

// Public auth endpoints never need (and shouldn't send) a bearer token.
const PUBLIC_AUTH_PATHS = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/forgot-password',
  '/api/auth/reset-password',
];

// Pre-flight readiness check when backend is waking or readiness is stale
API.interceptors.request.use(async (config) => {
  const isHealthCheck = config.url?.includes('/actuator/health');
  if (!isHealthCheck && (backendReadiness.isStale() || backendReadiness.getStatus() === ReadinessStatus.WAKING)) {
    try {
      await backendReadiness.checkReadiness();
    } catch {
      // Proceed; let normal Axios error handling manage the outcome
    }
  }

  const isPublicAuthRoute = PUBLIC_AUTH_PATHS.some((path) => config.url?.includes(path));
  const token = localStorage.getItem('token');
  if (token && !isPublicAuthRoute) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor: detect Render idle gateway responses (502, 503, 504)
API.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    if (status === 502 || status === 503 || status === 504) {
      backendReadiness.markWaking();
      backendReadiness.checkReadiness({ force: true });
    }
    // NEVER automatically replay mutating requests (POST, PUT, PATCH, DELETE)
    return Promise.reject(error);
  }
);

export default API;
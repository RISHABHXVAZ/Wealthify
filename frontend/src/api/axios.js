import axios from 'axios';
import { backendReadiness, ReadinessStatus } from '../services/backendReadiness.js';

export const API_BASE_URL = (typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_URL) || 'http://localhost:8080';

export const DEFAULT_TIMEOUT_MS = 15000;  // 15 seconds for ordinary operations & auth
export const EXTENDED_TIMEOUT_MS = 30000; // 30 seconds for AI / Analytics / SMTP operations

// Paths with external integrations (Groq LLM or Gmail SMTP) requiring longer bounded timeouts
export const SLOW_OPERATION_PATHS = [
  '/api/analytics',
  '/api/insights',
  '/api/stocks',
  '/api/expenses',             // synchronous Groq categorization
  '/api/goals',                // synchronous Groq advice/plan on creation & contribution
  '/api/auth/forgot-password', // Gmail SMTP connection & delivery
];

export const getTimeoutForUrl = (url = '') => {
  const isSlow = SLOW_OPERATION_PATHS.some((path) => url.includes(path));
  return isSlow ? EXTENDED_TIMEOUT_MS : DEFAULT_TIMEOUT_MS;
};

export function getFriendlyErrorMessage(
  err,
  fallbackMessage = 'An unexpected error occurred. Please try again.',
  isMutation = false
) {
  if (err?.name === 'CanceledError' || err?.name === 'AbortError' || err?.code === 'ERR_CANCELED') {
    return 'The request was cancelled.';
  }

  const status = err?.response?.status;
  if (status === 502 || status === 503 || status === 504) {
    return 'The server is currently starting up after inactivity. Please wait a moment and try again.';
  }

  if (err?.code === 'ECONNABORTED' || (!err?.response && err?.message?.toLowerCase().includes('timeout'))) {
    if (isMutation) {
      return 'The request took longer than expected. The operation may have completed — please check your dashboard before trying again.';
    }
    return 'The server took too long to respond. Please check your connection and try again.';
  }

  if (!err?.response && (err?.code === 'ERR_NETWORK' || err?.message?.toLowerCase().includes('network'))) {
    return 'Unable to reach the server. Please check your internet connection or try again shortly.';
  }

  const backendMsg = typeof err?.response?.data === 'string'
    ? err.response.data
    : err?.response?.data?.message;

  return backendMsg || fallbackMessage;
}

const API = axios.create({
  baseURL: API_BASE_URL,
});

// Public auth endpoints never need (and shouldn't send) a bearer token.
const PUBLIC_AUTH_PATHS = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/forgot-password',
  '/api/auth/reset-password',
];

// Pre-flight readiness check and timeout enforcement
API.interceptors.request.use(async (config) => {
  // Apply URL-specific timeout if not explicitly set by the caller
  if (!config.timeout) {
    config.timeout = getTimeoutForUrl(config.url);
  }

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
import axios from 'axios';

const API = axios.create({
  // Uses your Vercel URL in production, or falls back to localhost during local testing
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
});

// Public auth endpoints never need (and shouldn't send) a bearer token.
// Sending a stale/expired token here was tripping up the backend's JWT
// filter and turning "forgot password" into a hard failure instead of
// just working like an unauthenticated request should.
const PUBLIC_AUTH_PATHS = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/forgot-password',
  '/api/auth/reset-password',
];

API.interceptors.request.use((config) => {
  const isPublicAuthRoute = PUBLIC_AUTH_PATHS.some((path) => config.url?.includes(path));
  const token = localStorage.getItem('token');
  if (token && !isPublicAuthRoute) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default API;
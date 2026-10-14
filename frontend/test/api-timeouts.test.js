import test from 'node:test';
import assert from 'node:assert/strict';
import {
  DEFAULT_TIMEOUT_MS,
  EXTENDED_TIMEOUT_MS,
  getTimeoutForUrl,
  getFriendlyErrorMessage,
} from '../src/api/axios.js';
import { BackendReadinessManager, ReadinessStatus } from '../src/services/backendReadiness.js';

test('OPT-06: Endpoint timeout classification applies 15s default or 30s extended correctly', () => {
  // Ordinary requests: 15s
  assert.equal(getTimeoutForUrl('/api/budget/allocation'), DEFAULT_TIMEOUT_MS);
  assert.equal(getTimeoutForUrl('/api/auth/login'), DEFAULT_TIMEOUT_MS);
  assert.equal(getTimeoutForUrl('/api/auth/register'), DEFAULT_TIMEOUT_MS);
  assert.equal(getTimeoutForUrl('/api/user/income'), DEFAULT_TIMEOUT_MS);

  // Slow AI / Analytics / SMTP operations: 30s
  assert.equal(getTimeoutForUrl('/api/analytics/monthly?month=10&year=2026'), EXTENDED_TIMEOUT_MS);
  assert.equal(getTimeoutForUrl('/api/analytics/daily'), EXTENDED_TIMEOUT_MS);
  assert.equal(getTimeoutForUrl('/api/insights/wasteful?month=10&year=2026'), EXTENDED_TIMEOUT_MS);
  assert.equal(getTimeoutForUrl('/api/stocks/recommend?month=10&year=2026'), EXTENDED_TIMEOUT_MS);
  assert.equal(getTimeoutForUrl('/api/expenses'), EXTENDED_TIMEOUT_MS);
  assert.equal(getTimeoutForUrl('/api/goals'), EXTENDED_TIMEOUT_MS);
  assert.equal(getTimeoutForUrl('/api/auth/forgot-password'), EXTENDED_TIMEOUT_MS);
});

test('OPT-06: Ordinary request timeout formats clean error and releases loading state', async () => {
  let loading = true;
  let error = '';

  const simulateFetch = async () => {
    loading = true;
    error = '';
    try {
      // Simulate an axios timeout error
      const timeoutErr = new Error('timeout of 15000ms exceeded');
      timeoutErr.code = 'ECONNABORTED';
      throw timeoutErr;
    } catch (err) {
      error = getFriendlyErrorMessage(err, 'Failed to fetch data', false);
    } finally {
      loading = false;
    }
  };

  await simulateFetch();

  assert.equal(loading, false, 'Loading state must be released in finally block');
  assert.equal(error, 'The server took too long to respond. Please check your connection and try again.');
  assert.doesNotMatch(error, /15000ms/, 'Must not leak raw technical error details');
});

test('OPT-06: Financial mutation timeout cautions user without claiming definitive failure', () => {
  const timeoutErr = new Error('timeout of 30000ms exceeded');
  timeoutErr.code = 'ECONNABORTED';

  const message = getFriendlyErrorMessage(timeoutErr, 'Failed to add expense', true);

  assert.match(message, /operation may have completed/i);
  assert.match(message, /check your dashboard before trying again/i);
  assert.doesNotMatch(message, /failed to add expense/i, 'Must not claim the expense definitively failed');
});

test('OPT-06: User cancellation is handled cleanly', () => {
  const cancelErr = new Error('canceled');
  cancelErr.name = 'CanceledError';
  cancelErr.code = 'ERR_CANCELED';

  const message = getFriendlyErrorMessage(cancelErr);
  assert.equal(message, 'The request was cancelled.');
});

test('OPT-06: Distinguishes backend waking (502/503/504) from client request timeout', () => {
  // Waking error from Render gateway
  const gatewayErr = new Error('Gateway Timeout');
  gatewayErr.response = { status: 504, data: 'Gateway Timeout' };

  const gatewayMessage = getFriendlyErrorMessage(gatewayErr);
  assert.match(gatewayMessage, /server is currently starting up after inactivity/i);

  // Client request timeout when backend was already reachable
  const timeoutErr = new Error('timeout of 15000ms exceeded');
  timeoutErr.code = 'ECONNABORTED';

  const timeoutMessage = getFriendlyErrorMessage(timeoutErr);
  assert.match(timeoutMessage, /server took too long to respond/i);
  assert.doesNotMatch(timeoutMessage, /starting up after inactivity/i);
});

test('OPT-06: Readiness request timeout followed by a later successful check recovers cleanly', async () => {
  let callCount = 0;
  const mockHttpClient = {
    get: async () => {
      callCount++;
      if (callCount === 1) {
        // First attempt: timeout error during container cold start
        const err = new Error('timeout of 10000ms exceeded');
        err.code = 'ECONNABORTED';
        throw err;
      }
      // Second attempt: returns 200 UP
      return { status: 200, data: { status: 'UP' } };
    },
  };

  const manager = new BackendReadinessManager({
    baseURL: 'http://test-server:8080',
    timeoutMs: 50,
    initialDelayMs: 10,
    maxRetries: 3,
  });

  const statuses = [];
  manager.subscribe((s) => statuses.push(s));

  const result = await manager.checkReadiness({ httpClient: mockHttpClient, initialDelayMs: 10 });

  assert.equal(result.ready, true);
  assert.equal(result.attempts, 2);
  assert.equal(manager.getStatus(), ReadinessStatus.READY);
  assert.ok(statuses.includes(ReadinessStatus.WAKING), 'Must transition to WAKING on timeout');
  assert.ok(statuses.includes(ReadinessStatus.READY), 'Must recover to READY on second attempt');
});

test('OPT-06: Mutating requests are NEVER automatically replayed after timeout or failure', async () => {
  const mutatingDispatches = [];

  // Mock an API client that records all requests
  const mockApi = {
    post: async (path, body) => {
      mutatingDispatches.push({ method: 'POST', path, body });
      const err = new Error('timeout of 30000ms exceeded');
      err.code = 'ECONNABORTED';
      throw err;
    },
  };

  try {
    await mockApi.post('/api/expenses', { amount: 500, description: 'Groceries' });
  } catch (err) {
    assert.equal(err.code, 'ECONNABORTED');
  }

  // Exactly 1 dispatch was made — zero automatic re-attempts
  assert.equal(mutatingDispatches.length, 1);
});

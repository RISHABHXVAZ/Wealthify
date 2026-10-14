import test from 'node:test';
import assert from 'node:assert/strict';
import { BackendReadinessManager, ReadinessStatus } from '../src/services/backendReadiness.js';

test('OPT-05: Successful readiness check transitions to READY on first attempt', async () => {
  let callCount = 0;
  const mockHttpClient = {
    get: async (url, config) => {
      callCount++;
      assert.match(url, /\/actuator\/health\/readiness$/);
      return { status: 200, data: { status: 'UP' } };
    },
  };

  const manager = new BackendReadinessManager({
    baseURL: 'http://test-server:8080',
  });

  const statuses = [];
  manager.subscribe((s) => statuses.push(s));

  const result = await manager.checkReadiness({
    httpClient: mockHttpClient,
    initialDelayMs: 10,
    maxRetries: 3,
  });

  assert.equal(result.ready, true);
  assert.equal(result.attempts, 1);
  assert.equal(manager.getStatus(), ReadinessStatus.READY);
  assert.equal(callCount, 1);
  assert.ok(statuses.includes(ReadinessStatus.CHECKING));
  assert.ok(statuses.includes(ReadinessStatus.READY));
});

test('OPT-05: Temporary failure followed by recovery transitions through WAKING to READY', async () => {
  let callCount = 0;
  const mockHttpClient = {
    get: async () => {
      callCount++;
      if (callCount === 1) {
        // First attempt: Render gateway returns 503 or HTML loading page
        return { status: 503, data: 'Service Unavailable' };
      }
      // Second attempt: Spring Boot is ready
      return { status: 200, data: { status: 'UP' } };
    },
  };

  const manager = new BackendReadinessManager({
    baseURL: 'http://test-server:8080',
  });

  const statuses = [];
  manager.subscribe((s) => statuses.push(s));

  const result = await manager.checkReadiness({
    httpClient: mockHttpClient,
    initialDelayMs: 15,
    maxRetries: 3,
  });

  assert.equal(result.ready, true);
  assert.equal(result.attempts, 2);
  assert.equal(manager.getStatus(), ReadinessStatus.READY);
  assert.equal(callCount, 2);
  assert.ok(statuses.includes(ReadinessStatus.WAKING), 'Should transition to WAKING on failure');
  assert.ok(statuses.includes(ReadinessStatus.READY), 'Should transition to READY upon recovery');
});

test('OPT-05: Retry exhaustion transitions to EXHAUSTED when budget is exceeded', async () => {
  let callCount = 0;
  const mockHttpClient = {
    get: async () => {
      callCount++;
      throw new Error('Network Error: Connection refused');
    },
  };

  const manager = new BackendReadinessManager({
    baseURL: 'http://test-server:8080',
  });

  const statuses = [];
  manager.subscribe((s) => statuses.push(s));

  const result = await manager.checkReadiness({
    httpClient: mockHttpClient,
    initialDelayMs: 10,
    maxDelayMs: 20,
    maxRetries: 2,
    timeoutMs: 50,
  });

  assert.equal(result.ready, false);
  assert.equal(result.exhausted, true);
  assert.equal(result.attempts, 3); // initial + 2 retries = 3 attempts
  assert.equal(manager.getStatus(), ReadinessStatus.EXHAUSTED);
  assert.equal(callCount, 3);
  assert.ok(statuses.includes(ReadinessStatus.WAKING));
  assert.ok(statuses.includes(ReadinessStatus.EXHAUSTED));
});

test('OPT-05: Simultaneous calls share a single in-flight warm-up operation', async () => {
  let callCount = 0;
  let resolveGet;
  const getPromise = new Promise((resolve) => {
    resolveGet = resolve;
  });

  const mockHttpClient = {
    get: async () => {
      callCount++;
      await getPromise;
      return { status: 200, data: { status: 'UP' } };
    },
  };

  const manager = new BackendReadinessManager({
    baseURL: 'http://test-server:8080',
  });

  // Launch two checks concurrently
  const check1 = manager.checkReadiness({ httpClient: mockHttpClient, initialDelayMs: 10 });
  const check2 = manager.checkReadiness({ httpClient: mockHttpClient, initialDelayMs: 10 });

  // Verify only 1 HTTP request is dispatched
  assert.equal(callCount, 1);

  resolveGet();

  const [res1, res2] = await Promise.all([check1, check2]);

  assert.equal(res1.ready, true);
  assert.equal(res2.ready, true);
  assert.equal(callCount, 1, 'Only one HTTP call should be made for concurrent requests');
});

test('OPT-05: Subsequent checkReadiness calls bypass network once confirmed READY', async () => {
  let callCount = 0;
  const mockHttpClient = {
    get: async () => {
      callCount++;
      return { status: 200, data: { status: 'UP' } };
    },
  };

  const manager = new BackendReadinessManager({
    baseURL: 'http://test-server:8080',
  });

  const first = await manager.checkReadiness({ httpClient: mockHttpClient, initialDelayMs: 10 });
  assert.equal(first.ready, true);
  assert.equal(callCount, 1);

  // Subsequent check should return immediately without making network calls
  const second = await manager.checkReadiness({ httpClient: mockHttpClient });
  assert.equal(second.ready, true);
  assert.equal(callCount, 1, 'Network call count must remain 1 after becoming ready');
});

test('OPT-05: Cleanup and cancellation via AbortSignal cancels pending check', async () => {
  let callCount = 0;
  const abortController = new AbortController();

  const mockHttpClient = {
    get: async () => {
      callCount++;
      // Simulate failure that would trigger backoff
      return { status: 503, data: 'Starting...' };
    },
  };

  const manager = new BackendReadinessManager({
    baseURL: 'http://test-server:8080',
  });

  const checkPromise = manager.checkReadiness({
    httpClient: mockHttpClient,
    initialDelayMs: 200,
    maxRetries: 5,
    signal: abortController.signal,
  });

  // Abort while waiting for backoff
  setTimeout(() => abortController.abort(), 20);

  const result = await checkPromise;
  assert.equal(result.ready, false);
  assert.equal(result.aborted, true);
  assert.equal(callCount, 1, 'Should not continue retrying once aborted');
});

test('OPT-05: Mutating API requests (POST, PUT, PATCH, DELETE) are NEVER executed or replayed', async () => {
  const mutatingCalls = [];
  const mockHttpClient = {
    get: async () => ({ status: 200, data: { status: 'UP' } }),
    post: async (...args) => { mutatingCalls.push(['POST', ...args]); },
    put: async (...args) => { mutatingCalls.push(['PUT', ...args]); },
    patch: async (...args) => { mutatingCalls.push(['PATCH', ...args]); },
    delete: async (...args) => { mutatingCalls.push(['DELETE', ...args]); },
  };

  const manager = new BackendReadinessManager({
    baseURL: 'http://test-server:8080',
  });

  await manager.checkReadiness({ httpClient: mockHttpClient, initialDelayMs: 10 });

  assert.equal(mutatingCalls.length, 0, 'Readiness check must never issue mutating requests');
});

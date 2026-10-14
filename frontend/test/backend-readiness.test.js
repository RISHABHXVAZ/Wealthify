import test from 'node:test';
import assert from 'node:assert/strict';
import { BackendReadinessManager, ReadinessStatus } from '../src/services/backendReadiness.js';

test('OPT-05: Successful readiness check transitions to READY on first attempt', async () => {
  let callCount = 0;
  const mockHttpClient = {
    get: async (url) => {
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
  assert.ok(manager.getLastReadyTime() > 0, 'Must record lastReadyTime');
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

test('OPT-05: Subsequent checkReadiness calls bypass network while readiness remains fresh', async () => {
  let callCount = 0;
  const mockHttpClient = {
    get: async () => {
      callCount++;
      return { status: 200, data: { status: 'UP' } };
    },
  };

  const manager = new BackendReadinessManager({
    baseURL: 'http://test-server:8080',
    staleThresholdMs: 60000, // 1 minute
  });

  const first = await manager.checkReadiness({ httpClient: mockHttpClient, initialDelayMs: 10 });
  assert.equal(first.ready, true);
  assert.equal(callCount, 1);

  // 5 subsequent calls within freshness window should bypass network
  for (let i = 0; i < 5; i++) {
    const res = await manager.checkReadiness({ httpClient: mockHttpClient });
    assert.equal(res.ready, true);
    assert.equal(res.fresh, true);
  }
  assert.equal(callCount, 1, 'Network call count must remain 1 while fresh');
});

test('OPT-05A: Returning to the application after readiness has become stale triggers revalidation', async () => {
  let callCount = 0;
  const mockHttpClient = {
    get: async () => {
      callCount++;
      return { status: 200, data: { status: 'UP' } };
    },
  };

  const manager = new BackendReadinessManager({
    baseURL: 'http://test-server:8080',
    staleThresholdMs: 40, // 40ms threshold for test
  });

  // 1. Initial check marks it ready
  const first = await manager.checkReadiness({ httpClient: mockHttpClient, initialDelayMs: 10 });
  assert.equal(first.ready, true);
  assert.equal(callCount, 1);
  assert.equal(manager.isStale(), false);

  // 2. Wait until staleThreshold has passed
  await new Promise((resolve) => setTimeout(resolve, 55));
  assert.equal(manager.isStale(), true, 'Must report stale after threshold elapses');

  // 3. Next check triggers revalidation probe
  const second = await manager.checkReadiness({ httpClient: mockHttpClient, initialDelayMs: 10 });
  assert.equal(second.ready, true);
  assert.equal(callCount, 2, 'Must issue a new network probe after becoming stale');
  assert.equal(manager.isStale(), false, 'Must be fresh again after successful revalidation');
});

test('OPT-05A: Multiple concurrent requests after becoming stale share a single revalidation probe', async () => {
  let callCount = 0;
  let resolveGet;
  const getPromise = new Promise((resolve) => {
    resolveGet = resolve;
  });

  const mockHttpClient = {
    get: async () => {
      callCount++;
      if (callCount === 2) {
        await getPromise;
      }
      return { status: 200, data: { status: 'UP' } };
    },
  };

  const manager = new BackendReadinessManager({
    baseURL: 'http://test-server:8080',
    staleThresholdMs: 20,
  });

  // Initial check
  await manager.checkReadiness({ httpClient: mockHttpClient, initialDelayMs: 5 });
  assert.equal(callCount, 1);

  // Wait for staleness
  await new Promise((resolve) => setTimeout(resolve, 30));
  assert.equal(manager.isStale(), true);

  // Fire 4 concurrent checks when stale
  const p1 = manager.checkReadiness({ httpClient: mockHttpClient, initialDelayMs: 5 });
  const p2 = manager.checkReadiness({ httpClient: mockHttpClient, initialDelayMs: 5 });
  const p3 = manager.checkReadiness({ httpClient: mockHttpClient, initialDelayMs: 5 });
  const p4 = manager.checkReadiness({ httpClient: mockHttpClient, initialDelayMs: 5 });

  assert.equal(callCount, 2, 'Only 1 additional HTTP request should be made across all concurrent checks');

  resolveGet();
  const results = await Promise.all([p1, p2, p3, p4]);

  results.forEach((r) => assert.equal(r.ready, true));
  assert.equal(callCount, 2);
  assert.equal(manager.isStale(), false);
});

test('OPT-05A: Retry exhaustion provides explicit manual reset and recovery', async () => {
  let callCount = 0;
  let serverAwake = false;

  const mockHttpClient = {
    get: async () => {
      callCount++;
      if (!serverAwake) {
        throw new Error('Connection refused (Render sleeping)');
      }
      return { status: 200, data: { status: 'UP' } };
    },
  };

  const manager = new BackendReadinessManager({
    baseURL: 'http://test-server:8080',
    maxRetries: 2,
    initialDelayMs: 5,
    maxDelayMs: 10,
    timeoutMs: 20,
  });

  // Exhaust retries
  const exhaustedResult = await manager.checkReadiness({ httpClient: mockHttpClient });
  assert.equal(exhaustedResult.ready, false);
  assert.equal(exhaustedResult.exhausted, true);
  assert.equal(manager.getStatus(), ReadinessStatus.EXHAUSTED);

  // Server wakes up in the background
  serverAwake = true;

  // Manual retry resets manager and recovers smoothly
  manager.reset();
  assert.equal(manager.getStatus(), ReadinessStatus.IDLE);

  const recoveredResult = await manager.checkReadiness({ httpClient: mockHttpClient, initialDelayMs: 5 });
  assert.equal(recoveredResult.ready, true);
  assert.equal(manager.getStatus(), ReadinessStatus.READY);
});

test('OPT-05: Cleanup and cancellation via AbortSignal cancels pending check', async () => {
  let callCount = 0;
  const abortController = new AbortController();

  const mockHttpClient = {
    get: async () => {
      callCount++;
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

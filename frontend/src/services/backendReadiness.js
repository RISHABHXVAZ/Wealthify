import axios from 'axios';

export const ReadinessStatus = {
  IDLE: 'idle',
  CHECKING: 'checking',
  WAKING: 'waking',
  READY: 'ready',
  EXHAUSTED: 'exhausted',
};

export const DEFAULT_API_BASE_URL =
  (typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_URL) || 'http://localhost:8080';

class BackendReadinessManager {
  constructor(options = {}) {
    this.baseURL = options.baseURL || DEFAULT_API_BASE_URL;
    this.endpoint = options.endpoint || '/actuator/health/readiness';
    // Extended retry budget (14 retries comfortably covers Render free-tier cold-start timelines up to ~180s)
    this.maxRetries = options.maxRetries ?? 14;
    this.initialDelayMs = options.initialDelayMs ?? 2000;
    this.maxDelayMs = options.maxDelayMs ?? 8000;
    this.timeoutMs = options.timeoutMs ?? 10000;
    // Render Free spins down after 15 min of inactivity; mark stale after 10 min
    this.staleThresholdMs = options.staleThresholdMs ?? (10 * 60 * 1000);
    this.status = ReadinessStatus.IDLE;
    this.inFlightPromise = null;
    this.listeners = new Set();
    this.abortController = null;
    this.retryCount = 0;
    this.lastReadyTime = null;
  }

  getStatus() {
    return this.status;
  }

  getLastReadyTime() {
    return this.lastReadyTime;
  }

  isStale(now = Date.now()) {
    if (this.status !== ReadinessStatus.READY || !this.lastReadyTime) {
      return true;
    }
    return (now - this.lastReadyTime) >= this.staleThresholdMs;
  }

  markWaking() {
    if (this.status !== ReadinessStatus.WAKING) {
      this.status = ReadinessStatus.WAKING;
      this.notify();
    }
  }

  subscribe(listener) {
    this.listeners.add(listener);
    listener(this.status);
    return () => this.listeners.delete(listener);
  }

  notify() {
    for (const listener of this.listeners) {
      try {
        listener(this.status);
      } catch (err) {
        console.error('Error in readiness listener:', err);
      }
    }
  }

  reset() {
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }
    this.status = ReadinessStatus.IDLE;
    this.inFlightPromise = null;
    this.retryCount = 0;
    this.lastReadyTime = null;
    this.notify();
  }

  async checkReadiness(overrideOptions = {}) {
    const force = overrideOptions.force || false;
    const now = overrideOptions.now || Date.now();

    // If confirmed ready, not stale, and not forced, resolve immediately
    if (this.status === ReadinessStatus.READY && !force && !this.isStale(now)) {
      return { ready: true, status: this.status, fresh: true };
    }

    // Share existing in-flight check promise if one is already active
    if (this.inFlightPromise) {
      return this.inFlightPromise;
    }

    const {
      signal: externalSignal,
      maxRetries = this.maxRetries,
      timeoutMs = this.timeoutMs,
      initialDelayMs = this.initialDelayMs,
      maxDelayMs = this.maxDelayMs,
      httpClient = axios,
      endpoint = this.endpoint,
      baseURL = this.baseURL,
    } = overrideOptions;

    this.abortController = new AbortController();
    const internalSignal = this.abortController.signal;

    const onExternalAbort = () => {
      this.abortController?.abort();
    };

    if (externalSignal) {
      if (externalSignal.aborted) {
        this.abortController.abort();
      } else {
        externalSignal.addEventListener('abort', onExternalAbort, { once: true });
      }
    }

    this.inFlightPromise = (async () => {
      this.status = ReadinessStatus.CHECKING;
      this.notify();

      let attempt = 0;
      let delay = initialDelayMs;

      while (attempt <= maxRetries) {
        if (internalSignal.aborted) {
          this.inFlightPromise = null;
          return { ready: false, aborted: true };
        }

        try {
          const url = `${baseURL}${endpoint}`;
          // Lightweight GET health check with timeout
          const response = await httpClient.get(url, {
            timeout: timeoutMs,
            signal: internalSignal,
            validateStatus: () => true, // Don't throw on 5xx so status can be inspected
          });

          const isUp =
            response.status === 200 &&
            (response.data?.status === 'UP' ||
             (typeof response.data === 'string' && response.data.includes('UP')) ||
             (typeof response.data === 'object' && response.data !== null && response.data.status !== 'DOWN'));

          if (isUp) {
            this.status = ReadinessStatus.READY;
            this.lastReadyTime = Date.now();
            this.inFlightPromise = null;
            this.notify();
            return { ready: true, attempts: attempt + 1 };
          }
        } catch (err) {
          if (internalSignal.aborted || err?.name === 'CanceledError' || err?.name === 'AbortError') {
            this.inFlightPromise = null;
            return { ready: false, aborted: true };
          }
          // Network error or timeout indicates the free container is starting
        }

        attempt++;
        this.retryCount = attempt;

        if (attempt > maxRetries) {
          break;
        }

        // On first failed attempt, transition to WAKING so UI calmly informs the user
        if (this.status !== ReadinessStatus.WAKING) {
          this.status = ReadinessStatus.WAKING;
          this.notify();
        }

        // Backoff delay with cancellation awareness
        try {
          await new Promise((resolve, reject) => {
            const timer = setTimeout(resolve, delay);
            const abortHandler = () => {
              clearTimeout(timer);
              reject(new Error('Aborted'));
            };
            if (internalSignal.aborted) {
              clearTimeout(timer);
              reject(new Error('Aborted'));
            } else {
              internalSignal.addEventListener('abort', abortHandler, { once: true });
            }
          });
        } catch {
          this.inFlightPromise = null;
          return { ready: false, aborted: true };
        }

        delay = Math.min(delay * 1.5, maxDelayMs);
      }

      this.status = ReadinessStatus.EXHAUSTED;
      this.inFlightPromise = null;
      this.notify();
      return { ready: false, exhausted: true, attempts: attempt };
    })();

    return this.inFlightPromise;
  }
}

export const backendReadiness = new BackendReadinessManager();
export { BackendReadinessManager };

import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { backendReadiness, ReadinessStatus } from '../services/backendReadiness.js';

const BackendStatusContext = createContext({
  status: ReadinessStatus.IDLE,
  isReady: false,
  isWaking: false,
  isExhausted: false,
  retry: () => {},
});

export const BackendStatusProvider = ({ children }) => {
  const [status, setStatus] = useState(backendReadiness.getStatus());

  useEffect(() => {
    const unsubscribe = backendReadiness.subscribe((newStatus) => {
      setStatus(newStatus);
    });

    // Initiate early readiness check on app mount
    backendReadiness.checkReadiness();

    // Revalidate readiness when the user returns to an inactive tab
    const handleVisibilityOrFocus = () => {
      if (typeof document !== 'undefined' && document.visibilityState === 'visible') {
        if (backendReadiness.isStale()) {
          backendReadiness.checkReadiness({ force: true });
        }
      }
    };

    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', handleVisibilityOrFocus);
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('focus', handleVisibilityOrFocus);
    }

    return () => {
      unsubscribe();
      if (typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', handleVisibilityOrFocus);
      }
      if (typeof window !== 'undefined') {
        window.removeEventListener('focus', handleVisibilityOrFocus);
      }
    };
  }, []);

  const retry = useCallback(() => {
    backendReadiness.reset();
    backendReadiness.checkReadiness();
  }, []);

  const value = {
    status,
    isReady: status === ReadinessStatus.READY,
    isWaking: status === ReadinessStatus.WAKING,
    isExhausted: status === ReadinessStatus.EXHAUSTED,
    retry,
  };

  return (
    <BackendStatusContext.Provider value={value}>
      {children}
    </BackendStatusContext.Provider>
  );
};

export const useBackendStatus = () => useContext(BackendStatusContext);

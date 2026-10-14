import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { backendReadiness, ReadinessStatus } from '../services/backendReadiness';

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

    return () => {
      unsubscribe();
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

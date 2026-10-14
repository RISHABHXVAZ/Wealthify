import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import API from '../api/axios';
import { useAuth } from './AuthContext';

const BudgetContext = createContext();

export const BudgetProvider = ({ children }) => {
  const { token } = useAuth();
  const [budget, setBudget] = useState(null);
  const [loading, setLoading] = useState(Boolean(token));
  const [error, setError] = useState('');

  const fetchBudget = useCallback(async () => {
    if (!token) {
      setBudget(null);
      setLoading(false);
      return null;
    }
    setLoading(true);
    setError('');
    try {
      const res = await API.get('/api/budget/allocation');
      setBudget(res.data);
      return res.data;
    } catch (err) {
      console.error('Budget fetch failed:', err);
      setError(err.response?.data?.message || 'Failed to fetch budget');
      return null;
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    fetchBudget();
  }, [fetchBudget]);

  return (
    <BudgetContext.Provider value={{ budget, setBudget, loading, error, fetchBudget }}>
      {children}
    </BudgetContext.Provider>
  );
};

export const useBudget = () => useContext(BudgetContext);
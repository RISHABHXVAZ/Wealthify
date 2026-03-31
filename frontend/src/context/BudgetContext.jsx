import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import API from '../api/axios';
import { useAuth } from './AuthContext';

const BudgetContext = createContext();

export const BudgetProvider = ({ children }) => {
  const { token } = useAuth();
  const [budget, setBudget] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchBudget = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    try {
      const res = await API.get('/api/budget/allocation');
      setBudget(res.data);
    } catch (err) {
      console.error('Budget fetch failed:', err);
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    fetchBudget();
  }, [fetchBudget]);

  return (
    <BudgetContext.Provider value={{ budget, loading, fetchBudget }}>
      {children}
    </BudgetContext.Provider>
  );
};

export const useBudget = () => useContext(BudgetContext);
import { useState, useEffect } from 'react';
import API from '../api/axios';
import {
  PiggyBank, Loader, Save, AlertTriangle, CheckCircle, Bot
} from 'lucide-react';
import { useBudget } from '../context/BudgetContext';

const Budget = () => {
  const { budget: globalBudget, fetchBudget: refreshGlobal } = useBudget();
  const [budget, setBudget] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    monthlyIncome: '',
    savingPercentage: '20',
    investmentPercentage: '30',
  });
  const [msg, setMsg] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    fetchBudget();
  }, []);

  // Sync local form when global budget loads
  useEffect(() => {
    if (globalBudget && !budget) {
      setBudget(globalBudget);
      setForm({
        monthlyIncome: globalBudget.monthlyIncome || '',
        savingPercentage: globalBudget.savingPercentage || '20',
        investmentPercentage: globalBudget.investmentPercentage || '30',
      });
      setLoading(false);
    }
  }, [globalBudget]);

  const fetchBudget = async () => {
    setLoading(true);
    try {
      const res = await API.get('/api/budget/allocation');
      setBudget(res.data);
      setForm({
        monthlyIncome: res.data.monthlyIncome || '',
        savingPercentage: res.data.savingPercentage || '20',
        investmentPercentage: res.data.investmentPercentage || '30',
      });
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleSetup = async (e) => {
    e.preventDefault();
    setSaving(true);
    setMsg('');
    setError('');
    try {
      const res = await API.post('/api/budget/setup', {
        monthlyIncome: parseFloat(form.monthlyIncome),
        savingPercentage: parseFloat(form.savingPercentage),
        investmentPercentage: parseFloat(form.investmentPercentage),
      });
      setBudget(res.data);
      await refreshGlobal(); // ← sync all tabs
      setMsg('Budget setup saved successfully! All tabs are now updated.');
    } catch (err) {
      setError('Failed to save budget setup.');
    } finally {
      setSaving(false);
    }
  };

  // Live preview calculations
  const income = parseFloat(form.monthlyIncome) || 0;
  const savePct = parseFloat(form.savingPercentage) || 0;
  const investPct = parseFloat(form.investmentPercentage) || 0;
  const savingAmt = (income * savePct) / 100;
  const investAmt = (savingAmt * investPct) / 100;
  const availableAmt = income - savingAmt;

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <Loader className="animate-spin text-green-400" size={32} />
    </div>
  );

  return (
    <div className="space-y-6">

      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-white">Budget Planner</h1>
        <p className="text-gray-400 text-sm mt-1">
          Set your saving and investment targets —
          changes reflect across all tabs instantly
        </p>
      </div>

      {/* Cross-tab sync banner */}
      <div className="bg-blue-500/10 border border-blue-500/20 rounded-2xl px-4 py-3 flex items-center gap-3">
        <div className="w-2 h-2 bg-blue-400 rounded-full animate-pulse shrink-0" />
        <p className="text-blue-400 text-sm">
          Budget settings sync with Dashboard, Add Expense, Goals and Stock Advisor automatically.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

        {/* Setup Form */}
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-6">
          <h3 className="text-white font-semibold mb-5 flex items-center gap-2">
            <PiggyBank size={18} className="text-green-400" />
            Budget Setup
          </h3>

          <form onSubmit={handleSetup} className="space-y-4">
            <div>
              <label className="text-sm text-gray-400 mb-1 block">
                Monthly Income (₹)
              </label>
              <input
                type="number"
                value={form.monthlyIncome}
                onChange={e => setForm({ ...form, monthlyIncome: e.target.value })}
                className="w-full bg-gray-800 text-white rounded-xl px-4 py-3 border border-gray-700 focus:border-green-500 focus:outline-none"
                placeholder="e.g. 15000"
                required
              />
            </div>

            <div>
              <label className="text-sm text-gray-400 mb-1 block">
                Saving Percentage — % of income
              </label>
              <input
                type="number"
                value={form.savingPercentage}
                onChange={e => setForm({ ...form, savingPercentage: e.target.value })}
                className="w-full bg-gray-800 text-white rounded-xl px-4 py-3 border border-gray-700 focus:border-green-500 focus:outline-none"
                placeholder="e.g. 20"
                min="0" max="100"
                required
              />
              <p className="text-xs text-gray-500 mt-1">
                Recommended: 20% — ₹{savingAmt.toLocaleString('en-IN', {
                  maximumFractionDigits: 0
                })} will be saved
              </p>
            </div>

            <div>
              <label className="text-sm text-gray-400 mb-1 block">
                Investment Percentage — % of savings
              </label>
              <input
                type="number"
                value={form.investmentPercentage}
                onChange={e => setForm({ ...form, investmentPercentage: e.target.value })}
                className="w-full bg-gray-800 text-white rounded-xl px-4 py-3 border border-gray-700 focus:border-green-500 focus:outline-none"
                placeholder="e.g. 30"
                min="0" max="100"
                required
              />
              <p className="text-xs text-gray-500 mt-1">
                Recommended: 30% — ₹{investAmt.toLocaleString('en-IN', {
                  maximumFractionDigits: 0
                })} will go to stocks/mutual funds
              </p>
            </div>

            {msg && (
              <div className="flex items-center gap-2 bg-green-500/10 border border-green-500/20 rounded-xl px-4 py-3">
                <CheckCircle size={16} className="text-green-400" />
                <p className="text-green-400 text-sm">{msg}</p>
              </div>
            )}

            {error && (
              <div className="flex items-center gap-2 bg-red-500/10 border border-red-500/20 rounded-xl px-4 py-3">
                <AlertTriangle size={16} className="text-red-400" />
                <p className="text-red-400 text-sm">{error}</p>
              </div>
            )}

            <button
              type="submit"
              disabled={saving}
              className="w-full bg-green-500 hover:bg-green-600 text-white font-semibold py-3 rounded-xl transition-all flex items-center justify-center gap-2"
            >
              {saving
                ? <Loader size={16} className="animate-spin" />
                : <Save size={16} />}
              {saving ? 'Saving & syncing all tabs...' : 'Save Budget Setup'}
            </button>
          </form>
        </div>

        {/* Right Column */}
        <div className="space-y-4">

          {/* Live Preview */}
          <div className="bg-gray-900 border border-gray-800 rounded-2xl p-6">
            <h3 className="text-white font-semibold mb-4">
              📊 Live Breakdown Preview
            </h3>
            <div className="space-y-4">
              {[
                {
                  label: 'Monthly Income',
                  amount: income,
                  color: 'text-white',
                  bar: 'bg-gray-600',
                  pct: 100
                },
                {
                  label: `Savings (${savePct}% of income)`,
                  amount: savingAmt,
                  color: 'text-blue-400',
                  bar: 'bg-blue-500',
                  pct: savePct
                },
                {
                  label: `Investments (${investPct}% of savings → Stocks tab)`,
                  amount: investAmt,
                  color: 'text-purple-400',
                  bar: 'bg-purple-500',
                  pct: income > 0 ? (investAmt / income) * 100 : 0
                },
                {
                  label: 'Available for Expenses',
                  amount: availableAmt,
                  color: 'text-green-400',
                  bar: 'bg-green-500',
                  pct: income > 0 ? (availableAmt / income) * 100 : 0
                },
              ].map(({ label, amount, color, bar, pct }) => (
                <div key={label}>
                  <div className="flex justify-between text-sm mb-1">
                    <span className="text-gray-400">{label}</span>
                    <span className={`font-semibold ${color}`}>
                      ₹{amount.toLocaleString('en-IN', {
                        maximumFractionDigits: 0
                      })}
                    </span>
                  </div>
                  <div className="w-full bg-gray-800 rounded-full h-2">
                    <div
                      className={`${bar} h-2 rounded-full transition-all`}
                      style={{ width: `${Math.min(pct, 100)}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Current Month Status */}
          {budget && (
            <div className={`border rounded-2xl p-5 ${
              budget.overBudget
                ? 'bg-red-500/5 border-red-500/30'
                : budget.budgetUsedPercentage > 80
                ? 'bg-yellow-500/5 border-yellow-500/30'
                : 'bg-green-500/5 border-green-500/30'
            }`}>
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  {budget.overBudget
                    ? <AlertTriangle size={18} className="text-red-400" />
                    : <CheckCircle size={18} className="text-green-400" />
                  }
                  <span className={`font-semibold text-sm ${
                    budget.overBudget ? 'text-red-400'
                    : budget.budgetUsedPercentage > 80 ? 'text-yellow-400'
                    : 'text-green-400'
                  }`}>
                    {budget.overBudget
                      ? 'Over Budget This Month!'
                      : budget.budgetUsedPercentage > 80
                      ? 'Almost at Budget Limit'
                      : 'On Track This Month'}
                  </span>
                </div>
                <span className="text-gray-400 text-xs">
                  {(budget.budgetUsedPercentage || 0).toFixed(1)}% used
                </span>
              </div>

              {/* Budget progress bar */}
              <div className="w-full bg-gray-800 rounded-full h-2 mb-4">
                <div
                  className={`h-2 rounded-full transition-all ${
                    budget.overBudget ? 'bg-red-500'
                    : budget.budgetUsedPercentage > 80 ? 'bg-yellow-500'
                    : 'bg-green-500'
                  }`}
                  style={{
                    width: `${Math.min(budget.budgetUsedPercentage || 0, 100)}%`
                  }}
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                {[
                  {
                    label: 'Spent This Month',
                    value: budget.spentThisMonth,
                    color: 'text-red-400'
                  },
                  {
                    label: 'Remaining Budget',
                    value: budget.remainingBudget,
                    color: budget.overBudget ? 'text-red-400' : 'text-green-400'
                  },
                  {
                    label: 'Saving Target',
                    value: budget.monthlySavingAmount,
                    color: 'text-blue-400'
                  },
                  {
                    label: 'Investment Target',
                    value: budget.monthlyInvestmentAmount,
                    color: 'text-purple-400'
                  },
                  {
                    label: 'Wasteful This Month',
                    value: budget.wastefulThisMonth,
                    color: 'text-orange-400'
                  },
                  {
                    label: 'Available for Expenses',
                    value: budget.availableForExpenditure,
                    color: 'text-gray-300'
                  },
                ].map(({ label, value, color }) => (
                  <div key={label} className="bg-gray-800/50 rounded-xl p-3">
                    <p className="text-gray-500 text-xs">{label}</p>
                    <p className={`font-bold ${color}`}>
                      ₹{Number(value || 0).toLocaleString()}
                    </p>
                  </div>
                ))}
              </div>

              {budget.aiAdvice && (
                <div className="mt-4 flex items-start gap-2 bg-gray-800/50 rounded-xl p-3">
                  <Bot size={16} className="text-green-400 shrink-0 mt-0.5" />
                  <p className="text-gray-300 text-sm">{budget.aiAdvice}</p>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Budget;
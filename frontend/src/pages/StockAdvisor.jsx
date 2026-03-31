import { useState, useEffect } from 'react';
import API from '../api/axios';
import { TrendingUp, Loader, DollarSign, AlertCircle } from 'lucide-react';
import { useBudget } from '../context/BudgetContext';

const riskColor = {
  LOW: 'text-green-400 bg-green-500/10',
  MEDIUM: 'text-yellow-400 bg-yellow-500/10',
  HIGH: 'text-red-400 bg-red-500/10',
};

const typeColor = {
  STOCK: 'text-blue-400 bg-blue-500/10',
  ETF: 'text-purple-400 bg-purple-500/10',
  MUTUAL_FUND: 'text-orange-400 bg-orange-500/10',
};

const StockAdvisor = () => {
  const now = new Date();
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [year, setYear] = useState(now.getFullYear());
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [income, setIncome] = useState('');
  const [savingIncome, setSavingIncome] = useState(false);
  const [incomeMsg, setIncomeMsg] = useState('');
  const [error, setError] = useState('');
  const { budget } = useBudget();

  const fetchRecommendations = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await API.get(`/api/stocks/recommend?month=${month}&year=${year}`);
      setData(res.data);
    } catch (err) {
      setError(err.response?.data || 'Please set your monthly income first.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchRecommendations(); }, [month, year]);

  const handleSaveIncome = async () => {
    if (!income) return;
    setSavingIncome(true);
    try {
      const res = await API.post('/api/user/income', { income: parseFloat(income) });
      setIncomeMsg(res.data);
      fetchRecommendations();
    } catch (err) {
      setIncomeMsg('Failed to update income');
    } finally {
      setSavingIncome(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Stock Advisor</h1>
          <p className="text-gray-400 text-sm mt-1">AI-powered investment recommendations based on your expenses</p>
        </div>
        <div className="flex gap-2">
          <select value={month} onChange={e => setMonth(Number(e.target.value))}
            className="bg-gray-800 text-white rounded-lg px-3 py-2 text-sm border border-gray-700 focus:outline-none">
            {Array.from({ length: 12 }, (_, i) => (
              <option key={i + 1} value={i + 1}>
                {new Date(0, i).toLocaleString('default', { month: 'short' })}
              </option>
            ))}
          </select>
          <select value={year} onChange={e => setYear(Number(e.target.value))}
            className="bg-gray-800 text-white rounded-lg px-3 py-2 text-sm border border-gray-700 focus:outline-none">
            {[2024, 2025, 2026].map(y => <option key={y} value={y}>{y}</option>)}
          </select>
        </div>
      </div>

      {/* Set Income */}
      <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
        <h3 className="text-white font-semibold mb-3">Set Monthly Income</h3>
        <div className="flex gap-3">
          <div className="relative flex-1">
            <DollarSign className="absolute left-3 top-3 text-gray-500" size={16} />
            <input
              type="number"
              value={income}
              onChange={e => setIncome(e.target.value)}
              className="w-full bg-gray-800 text-white rounded-xl pl-9 pr-4 py-2.5 border border-gray-700 focus:border-green-500 focus:outline-none"
              placeholder="Enter monthly income in ₹"
            />
          </div>
          <button
            onClick={handleSaveIncome}
            disabled={savingIncome}
            className="bg-green-500 hover:bg-green-600 text-white px-5 py-2.5 rounded-xl font-medium transition-all flex items-center gap-2"
          >
            {savingIncome ? <Loader size={16} className="animate-spin" /> : null}
            Save
          </button>
        </div>
        {incomeMsg && <p className="text-green-400 text-sm mt-2">{incomeMsg}</p>}
      </div>

      {error && (
        <div className="bg-orange-500/10 border border-orange-500/30 rounded-2xl p-5 flex items-center gap-3">
          <AlertCircle size={20} className="text-orange-400 shrink-0" />
          <p className="text-orange-400 text-sm">{error}</p>
        </div>
      )}

      {loading && (
        <div className="flex items-center justify-center h-40">
          <Loader className="animate-spin text-green-400" size={32} />
        </div>
      )}


      {data && !loading && (
        <>
          {/* Budget Source Banner */}
          {budget && (
            <div className="bg-purple-500/10 border border-purple-500/20 rounded-2xl p-4 flex items-center justify-between">
              <div>
                <p className="text-purple-400 font-medium text-sm">
                  Investment Amount from Budget Setup
                </p>
                <p className="text-gray-400 text-xs mt-0.5">
                  {Number(budget.savingPercentage)}% saving →{' '}
                  {Number(budget.investmentPercentage)}% of savings goes to investments
                </p>
              </div>
              <div className="text-right">
                <p className="text-purple-400 font-bold text-2xl">
                  ₹{Number(budget.monthlyInvestmentAmount || 0).toLocaleString()}
                </p>
                <p className="text-gray-500 text-xs">monthly investment budget</p>
              </div>
            </div>
          )}
          {/* Financial Summary */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {[
              { label: 'Monthly Income', value: data.monthlyIncome, color: 'text-green-400' },
              { label: 'Total Expenses', value: data.totalMonthlyExpenses, color: 'text-red-400' },
              { label: 'Investable Surplus', value: data.investableSurplus, color: 'text-blue-400' },
              { label: 'Savings Rate', value: null, display: `${(data.savingsRate || 0).toFixed(1)}%`, color: 'text-purple-400' },
            ].map(({ label, value, display, color }) => (
              <div key={label} className="bg-gray-900 border border-gray-800 rounded-2xl p-4">
                <p className="text-gray-400 text-xs mb-1">{label}</p>
                <p className={`text-xl font-bold ${color}`}>
                  {display || `₹${Number(value || 0).toLocaleString()}`}
                </p>
              </div>
            ))}
          </div>

          {/* AI Rationale */}
          {data.aiRationale && (
            <div className="bg-gray-900 border border-blue-500/20 rounded-2xl p-5">
              <div className="flex items-center gap-2 mb-2">
                <div className="w-2 h-2 bg-blue-400 rounded-full animate-pulse" />
                <span className="text-blue-400 text-sm font-medium">AI Rationale</span>
              </div>
              <p className="text-gray-300 text-sm">{data.aiRationale}</p>
            </div>
          )}

          {/* Stock Recommendations */}
          <div>
            <h3 className="text-white font-semibold mb-4">Recommended Investments</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {data.recommendations?.map((stock, i) => (
                <div key={i} className="bg-gray-900 border border-gray-800 rounded-2xl p-5 hover:border-green-500/30 transition-all">
                  <div className="flex items-start justify-between mb-3">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <TrendingUp size={16} className="text-green-400" />
                        <span className="text-white font-bold">{stock.ticker}</span>
                      </div>
                      <p className="text-gray-400 text-sm">{stock.name}</p>
                    </div>
                    <span className="text-green-400 font-bold text-lg">{stock.suggestedAllocation}</span>
                  </div>

                  <div className="flex gap-2 mb-3">
                    <span className={`text-xs px-2 py-1 rounded-full font-medium ${typeColor[stock.type] || 'text-gray-400 bg-gray-800'}`}>
                      {stock.type?.replace('_', ' ')}
                    </span>
                    <span className={`text-xs px-2 py-1 rounded-full font-medium ${riskColor[stock.riskLevel] || 'text-gray-400 bg-gray-800'}`}>
                      {stock.riskLevel} RISK
                    </span>
                  </div>

                  <p className="text-gray-400 text-sm">{stock.reason}</p>
                </div>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  );
};

export default StockAdvisor;
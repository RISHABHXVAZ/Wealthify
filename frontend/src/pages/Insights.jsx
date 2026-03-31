import { useState, useEffect } from 'react';
import API from '../api/axios';
import { AlertTriangle, Loader, TrendingDown } from 'lucide-react';

const Insights = () => {
  const now = new Date();
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [year, setYear] = useState(now.getFullYear());
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetch = async () => {
      setLoading(true);
      try {
        const res = await API.get(`/api/insights/wasteful?month=${month}&year=${year}`);
        setData(res.data);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetch();
  }, [month, year]);

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <Loader className="animate-spin text-green-400" size={32} />
    </div>
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Wasteful Spending Insights</h1>
          <p className="text-gray-400 text-sm mt-1">AI-powered analysis of unnecessary expenses</p>
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

      {/* Summary */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-red-500/10 border border-red-500/20 rounded-2xl p-5">
          <p className="text-red-400 text-sm mb-1">Total Wasteful</p>
          <p className="text-3xl font-bold text-white">₹{Number(data?.totalWastefulAmount || 0).toLocaleString()}</p>
        </div>
        <div className="bg-orange-500/10 border border-orange-500/20 rounded-2xl p-5">
          <p className="text-orange-400 text-sm mb-1">Wasteful %</p>
          <p className="text-3xl font-bold text-white">{(data?.wastefulPercentage || 0).toFixed(1)}%</p>
        </div>
        <div className="bg-yellow-500/10 border border-yellow-500/20 rounded-2xl p-5">
          <p className="text-yellow-400 text-sm mb-1">Transactions</p>
          <p className="text-3xl font-bold text-white">{data?.wastefulTransactionCount || 0}</p>
        </div>
      </div>

      {/* AI Summary */}
      {data?.aiSummary && (
        <div className="bg-gray-900 border border-red-500/20 rounded-2xl p-5">
          <div className="flex items-center gap-2 mb-2">
            <AlertTriangle size={16} className="text-red-400" />
            <span className="text-red-400 text-sm font-medium">AI Analysis</span>
          </div>
          <p className="text-gray-300 text-sm">{data.aiSummary}</p>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Wasteful by Category */}
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
          <h3 className="text-white font-semibold mb-4">By Category</h3>
          {data?.wastefulByCategory && Object.keys(data.wastefulByCategory).length > 0 ? (
            <div className="space-y-3">
              {Object.entries(data.wastefulByCategory).map(([cat, amount]) => (
                <div key={cat} className="flex items-center justify-between bg-gray-800 rounded-xl px-4 py-3">
                  <span className="text-gray-300 text-sm">{cat}</span>
                  <span className="text-red-400 font-semibold">₹{Number(amount).toLocaleString()}</span>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-8">
              <TrendingDown size={32} className="text-green-400 mx-auto mb-2" />
              <p className="text-green-400 font-medium">No wasteful spending!</p>
              <p className="text-gray-500 text-sm">Great financial discipline this month.</p>
            </div>
          )}
        </div>

        {/* AI Recommendations */}
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
          <h3 className="text-white font-semibold mb-4">💡 AI Recommendations</h3>
          {data?.aiRecommendations?.length > 0 ? (
            <div className="space-y-3">
              {data.aiRecommendations.map((tip, i) => (
                <div key={i} className="flex gap-3 bg-gray-800 rounded-xl p-4">
                  <span className="text-green-400 font-bold text-sm shrink-0">#{i + 1}</span>
                  <p className="text-gray-300 text-sm">{tip}</p>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-gray-500 text-sm">No recommendations needed — great spending habits!</p>
          )}
        </div>
      </div>

      {/* Wasteful Expenses List */}
      {data?.wastefulExpenses?.length > 0 && (
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
          <h3 className="text-white font-semibold mb-4">Flagged Expenses</h3>
          <div className="space-y-2">
            {data.wastefulExpenses.map((exp) => (
              <div key={exp.id} className="flex items-center justify-between bg-gray-800 rounded-xl px-4 py-3">
                <div>
                  <p className="text-white text-sm">{exp.description}</p>
                  <p className="text-gray-500 text-xs">{exp.category} • {exp.expenseDate}</p>
                </div>
                <div className="text-right">
                  <p className="text-red-400 font-semibold">₹{Number(exp.amount).toLocaleString()}</p>
                  <p className="text-gray-500 text-xs">{exp.aiReason}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default Insights;
import { useState, useEffect } from 'react';
import API from '../api/axios';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip,
  PieChart, Pie, Cell, ResponsiveContainer, Legend
} from 'recharts';
import { Loader, Calendar } from 'lucide-react';

const COLORS = ['#22c55e', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899'];

const Reports = () => {
  const now = new Date();
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [year, setYear] = useState(now.getFullYear());
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetch = async () => {
      setLoading(true);
      try {
        const res = await API.get(`/api/analytics/monthly?month=${month}&year=${year}`);
        setData(res.data);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetch();
  }, [month, year]);

  const pieData = data?.spendingByCategory
    ? Object.entries(data.spendingByCategory).map(([name, value]) => ({ name, value: Number(value) }))
    : [];

  const barData = data?.dailyBreakdown?.map(d => ({
    date: new Date(d.date).getDate(),
    spent: Number(d.totalSpent),
  })) || [];

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <Loader className="animate-spin text-green-400" size={32} />
    </div>
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-white">Reports</h1>
        <div className="flex items-center gap-2">
          <Calendar size={16} className="text-gray-400" />
          <select
            value={month}
            onChange={e => setMonth(Number(e.target.value))}
            className="bg-gray-800 text-white rounded-lg px-3 py-2 text-sm border border-gray-700 focus:outline-none"
          >
            {Array.from({ length: 12 }, (_, i) => (
              <option key={i + 1} value={i + 1}>
                {new Date(0, i).toLocaleString('default', { month: 'long' })}
              </option>
            ))}
          </select>
          <select
            value={year}
            onChange={e => setYear(Number(e.target.value))}
            className="bg-gray-800 text-white rounded-lg px-3 py-2 text-sm border border-gray-700 focus:outline-none"
          >
            {[2024, 2025, 2026].map(y => (
              <option key={y} value={y}>{y}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {[
          { label: 'Total Spent', value: data?.totalSpent, color: 'text-red-400' },
          { label: 'Total Income', value: data?.totalIncome, color: 'text-green-400' },
          { label: 'Savings', value: data?.savingsAmount, color: 'text-blue-400' },
          { label: 'Wasteful', value: data?.wastefulAmount, color: 'text-orange-400' },
        ].map(({ label, value, color }) => (
          <div key={label} className="bg-gray-900 border border-gray-800 rounded-2xl p-4">
            <p className="text-gray-400 text-xs mb-1">{label}</p>
            <p className={`text-xl font-bold ${color}`}>₹{Number(value || 0).toLocaleString()}</p>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Daily Bar Chart */}
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
          <h3 className="text-white font-semibold mb-4">Daily Spending</h3>
          {barData.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={barData}>
                <XAxis dataKey="date" stroke="#6b7280" tick={{ fontSize: 12 }} />
                <YAxis stroke="#6b7280" tick={{ fontSize: 12 }} />
                <Tooltip
                  contentStyle={{ background: '#1f2937', border: 'none', borderRadius: 8 }}
                  labelStyle={{ color: '#9ca3af' }}
                  itemStyle={{ color: '#22c55e' }}
                  formatter={val => [`₹${Number(val).toLocaleString()}`, 'Spent']}
                />
                <Bar dataKey="spent" fill="#22c55e" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <p className="text-gray-500 text-sm text-center py-10">No data for this period</p>
          )}
        </div>

        {/* Category Pie Chart */}
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
          <h3 className="text-white font-semibold mb-4">By Category</h3>
          {pieData.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie
                  data={pieData}
                  cx="50%"
                  cy="50%"
                  innerRadius={55}
                  outerRadius={85}
                  paddingAngle={3}
                  dataKey="value"
                >
                  {pieData.map((_, i) => (
                    <Cell key={i} fill={COLORS[i % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{ background: '#1f2937', border: 'none', borderRadius: 8 }}
                  formatter={val => [`₹${Number(val).toLocaleString()}`, '']}
                />
                <Legend
                  formatter={val => <span style={{ color: '#d1d5db', fontSize: 12 }}>{val}</span>}
                />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <p className="text-gray-500 text-sm text-center py-10">No data for this period</p>
          )}
        </div>
      </div>

      {/* AI Tips */}
      {data?.aiTips?.length > 0 && (
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
          <h3 className="text-white font-semibold mb-4">💡 AI Tips</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {data.aiTips.map((tip, i) => (
              <div key={i} className="bg-gray-800 rounded-xl p-4">
                <span className="text-green-400 font-bold text-sm">#{i + 1}</span>
                <p className="text-gray-300 text-sm mt-1">{tip}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default Reports;
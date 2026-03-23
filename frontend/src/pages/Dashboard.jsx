import { useState, useEffect } from 'react';
import API from '../api/axios';
import { useAuth } from '../context/AuthContext';
import {
  Wallet, TrendingDown, AlertTriangle, Calendar,
  Loader, PlusCircle, ArrowRight
} from 'lucide-react';
import { Link } from 'react-router-dom';

const StatCard = ({ title, value, icon: Icon, color, sub }) => (
  <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
    <div className="flex items-center justify-between mb-3">
      <span className="text-gray-400 text-sm">{title}</span>
      <div className={`p-2 rounded-lg ${color}`}>
        <Icon size={18} className="text-white" />
      </div>
    </div>
    <p className="text-2xl font-bold text-white">₹{Number(value || 0).toLocaleString()}</p>
    {sub && <p className="text-xs text-gray-500 mt-1">{sub}</p>}
  </div>
);

const Dashboard = () => {
  const { user } = useAuth();
  const [daily, setDaily] = useState(null);
  const [monthly, setMonthly] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [dailyRes, monthlyRes] = await Promise.all([
          API.get('/api/analytics/daily'),
          API.get(`/api/analytics/monthly?month=${new Date().getMonth() + 1}&year=${new Date().getFullYear()}`)
        ]);
        setDaily(dailyRes.data);
        setMonthly(monthlyRes.data);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <Loader className="animate-spin text-green-400" size={32} />
    </div>
  );

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">
            Good {new Date().getHours() < 12 ? 'Morning' : new Date().getHours() < 17 ? 'Afternoon' : 'Evening'}, {user?.name?.split(' ')[0]} 👋
          </h1>
          <p className="text-gray-400 text-sm mt-1">
            {new Date().toLocaleDateString('en-IN', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
          </p>
        </div>
        <Link
          to="/add-expense"
          className="flex items-center gap-2 bg-green-500 hover:bg-green-600 text-white px-4 py-2 rounded-xl text-sm font-medium transition-all"
        >
          <PlusCircle size={16} />
          Add Expense
        </Link>
      </div>

      {/* Monthly Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard
          title="Monthly Spent"
          value={monthly?.totalSpent}
          icon={Wallet}
          color="bg-blue-500"
          sub={`${monthly?.totalTransactions} transactions`}
        />
        <StatCard
          title="Today's Spent"
          value={daily?.totalSpent}
          icon={Calendar}
          color="bg-purple-500"
          sub={`${daily?.totalTransactions} transactions`}
        />
        <StatCard
          title="Wasteful Spending"
          value={monthly?.wastefulAmount}
          icon={AlertTriangle}
          color="bg-red-500"
          sub={`${monthly?.wastefulPercentage?.toFixed(1)}% of total`}
        />
        <StatCard
          title="Savings This Month"
          value={monthly?.savingsAmount}
          icon={TrendingDown}
          color="bg-green-500"
          sub={`${monthly?.savingsPercentage?.toFixed(1)}% savings rate`}
        />
      </div>

      {/* AI Summary */}
      {monthly?.aiSummary && (
        <div className="bg-gray-900 border border-green-500/20 rounded-2xl p-5">
          <div className="flex items-center gap-2 mb-2">
            <div className="w-2 h-2 bg-green-400 rounded-full animate-pulse" />
            <span className="text-green-400 text-sm font-medium">AI Insight</span>
          </div>
          <p className="text-gray-300 text-sm leading-relaxed">{monthly.aiSummary}</p>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Spending by Category */}
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
          <h3 className="text-white font-semibold mb-4">Spending by Category</h3>
          {monthly?.spendingByCategory && Object.keys(monthly.spendingByCategory).length > 0 ? (
            <div className="space-y-3">
              {Object.entries(monthly.spendingByCategory)
                .slice(0, 5)
                .map(([cat, amount]) => {
                  const pct = ((amount / monthly.totalSpent) * 100).toFixed(1);
                  return (
                    <div key={cat}>
                      <div className="flex justify-between text-sm mb-1">
                        <span className="text-gray-300">{cat}</span>
                        <span className="text-white font-medium">₹{Number(amount).toLocaleString()}</span>
                      </div>
                      <div className="w-full bg-gray-800 rounded-full h-1.5">
                        <div
                          className="bg-green-500 h-1.5 rounded-full"
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
            </div>
          ) : (
            <p className="text-gray-500 text-sm">No expenses this month yet.</p>
          )}
        </div>

        {/* Today's Expenses */}
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-white font-semibold">Today's Expenses</h3>
            <Link to="/reports" className="text-green-400 text-xs flex items-center gap-1 hover:underline">
              View all <ArrowRight size={12} />
            </Link>
          </div>
          {daily?.expenses && daily.expenses.length > 0 ? (
            <div className="space-y-3">
              {daily.expenses.slice(0, 5).map((exp) => (
                <div key={exp.id} className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className={`w-2 h-2 rounded-full ${exp.flaggedWasteful ? 'bg-red-400' : 'bg-green-400'}`} />
                    <div>
                      <p className="text-white text-sm">{exp.description}</p>
                      <p className="text-gray-500 text-xs">{exp.category}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-white text-sm font-medium">₹{Number(exp.amount).toLocaleString()}</p>
                    {exp.flaggedWasteful && (
                      <span className="text-red-400 text-xs">wasteful</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-6">
              <p className="text-gray-500 text-sm">No expenses today yet.</p>
              <Link to="/add-expense" className="text-green-400 text-sm hover:underline mt-2 block">
                Add your first expense
              </Link>
            </div>
          )}
        </div>
      </div>

      {/* AI Tips */}
      {monthly?.aiTips && monthly.aiTips.length > 0 && (
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
          <h3 className="text-white font-semibold mb-4">💡 AI Tips for You</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {monthly.aiTips.map((tip, i) => (
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

export default Dashboard;
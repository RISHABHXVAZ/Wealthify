import { useState, useEffect } from 'react';
import API from '../api/axios';
import { useAuth } from '../context/AuthContext';
import {
  Wallet, TrendingDown, AlertTriangle, Calendar,
  Loader, PlusCircle, ArrowRight, Bot
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { useBudget } from '../context/BudgetContext';

const StatCard = ({ title, value, icon: Icon, color, sub }) => (
  <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
    <div className="flex items-center justify-between mb-3">
      <span className="text-gray-400 text-sm">{title}</span>
      <div className={`p-2 rounded-lg ${color}`}>
        <Icon size={18} className="text-white" />
      </div>
    </div>
    <p className="text-2xl font-bold text-white">
      ₹{Number(value || 0).toLocaleString()}
    </p>
    {sub && <p className="text-xs text-gray-500 mt-1">{sub}</p>}
  </div>
);

const Dashboard = () => {
  const { user } = useAuth();
  const [daily, setDaily] = useState(null);
  const [monthly, setMonthly] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchData = async () => {
      try {
        const now = new Date();
        const month = now.getMonth() + 1;
        const year = now.getFullYear();

        const [dailyRes, monthlyRes] = await Promise.all([
          API.get('/api/analytics/daily'),
          API.get(`/api/analytics/monthly?month=${month}&year=${year}`)
        ]);

        console.log('Daily data:', dailyRes.data);
        console.log('Monthly data:', monthlyRes.data);

        setDaily(dailyRes.data);
        setMonthly(monthlyRes.data);
      } catch (err) {
        console.error('Dashboard fetch error:', err);
        setError('Failed to load dashboard data.');
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

  if (error) return (
    <div className="flex items-center justify-center h-64">
      <p className="text-red-400">{error}</p>
    </div>
  );

  const greeting = () => {
    const h = new Date().getHours();
    if (h < 12) return 'Good Morning';
    if (h < 17) return 'Good Afternoon';
    return 'Good Evening';
  };
  const { budget } = useBudget();

  return (
    <div className="space-y-6">

      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">
            {greeting()}, {user?.name?.split(' ')[0]} 👋
          </h1>
          <p className="text-gray-400 text-sm mt-1">
            {new Date().toLocaleDateString('en-IN', {
              weekday: 'long', year: 'numeric',
              month: 'long', day: 'numeric'
            })}
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

      {/* Monthly Stat Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard
          title="Monthly Spent"
          value={monthly?.totalSpent}
          icon={Wallet}
          color="bg-blue-500"
          sub={`${monthly?.totalTransactions || 0} transactions`}
        />
        <StatCard
          title="Today's Spent"
          value={daily?.totalSpent}
          icon={Calendar}
          color="bg-purple-500"
          sub={`${daily?.totalTransactions || 0} transactions`}
        />
        <StatCard
          title="Wasteful Spending"
          value={monthly?.wastefulAmount}
          icon={AlertTriangle}
          color="bg-red-500"
          sub={`${monthly?.wastefulPercentage?.toFixed(1) || 0}% of total`}
        />
        <StatCard
          title="Savings This Month"
          value={monthly?.savingsAmount}
          icon={TrendingDown}
          color="bg-green-500"
          sub={`${monthly?.savingsPercentage?.toFixed(1) || 0}% savings rate`}
        />
      </div>
      {/* Budget Health Bar */}
      {budget && (
        <div className={`border rounded-2xl p-5 ${budget.overBudget
            ? 'bg-red-500/5 border-red-500/30'
            : budget.budgetUsedPercentage > 80
              ? 'bg-yellow-500/5 border-yellow-500/30'
              : 'bg-green-500/5 border-green-500/30'
          }`}>
          <div className="flex items-center justify-between mb-3">
            <span className="text-white font-semibold text-sm">
              Budget Health This Month
            </span>
            <span className={`text-sm font-bold ${budget.overBudget ? 'text-red-400'
                : budget.budgetUsedPercentage > 80 ? 'text-yellow-400'
                  : 'text-green-400'
              }`}>
              {budget.overBudget
                ? '🔴 Over Budget'
                : budget.budgetUsedPercentage > 80
                  ? '🟡 Almost Full'
                  : '🟢 On Track'}
            </span>
          </div>

          <div className="w-full bg-gray-800 rounded-full h-3 mb-3">
            <div
              className={`h-3 rounded-full transition-all ${budget.overBudget ? 'bg-red-500'
                  : budget.budgetUsedPercentage > 80 ? 'bg-yellow-500'
                    : 'bg-green-500'
                }`}
              style={{
                width: `${Math.min(budget.budgetUsedPercentage || 0, 100)}%`
              }}
            />
          </div>

          <div className="grid grid-cols-4 gap-3 text-center">
            {[
              {
                label: 'Budget',
                value: budget.availableForExpenditure,
                color: 'text-white'
              },
              {
                label: 'Spent',
                value: budget.spentThisMonth,
                color: 'text-red-400'
              },
              {
                label: 'Remaining',
                value: budget.remainingBudget,
                color: budget.overBudget ? 'text-red-400' : 'text-green-400'
              },
              {
                label: 'Saving',
                value: budget.monthlySavingAmount,
                color: 'text-blue-400'
              },
            ].map(({ label, value, color }) => (
              <div key={label}>
                <p className="text-gray-500 text-xs">{label}</p>
                <p className={`font-bold text-sm ${color}`}>
                  ₹{Number(value || 0).toLocaleString()}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* AI Monthly Summary */}
      {monthly?.aiSummary && (
        <div className="bg-gray-900 border border-green-500/20 rounded-2xl p-5">
          <div className="flex items-center gap-2 mb-3">
            <Bot size={18} className="text-green-400" />
            <span className="text-green-400 text-sm font-semibold">
              AI Monthly Summary
            </span>
          </div>
          <p className="text-gray-300 text-sm leading-relaxed">
            {monthly.aiSummary}
          </p>
        </div>
      )}

      {/* AI Daily Summary */}
      {daily?.aiSummary && (
        <div className="bg-gray-900 border border-purple-500/20 rounded-2xl p-5">
          <div className="flex items-center gap-2 mb-3">
            <Bot size={18} className="text-purple-400" />
            <span className="text-purple-400 text-sm font-semibold">
              Today's AI Summary
            </span>
          </div>
          <p className="text-gray-300 text-sm leading-relaxed">
            {daily.aiSummary}
          </p>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

        {/* Spending by Category */}
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
          <h3 className="text-white font-semibold mb-4">
            Monthly Spending by Category
          </h3>
          {monthly?.spendingByCategory &&
            Object.keys(monthly.spendingByCategory).length > 0 ? (
            <div className="space-y-3">
              {Object.entries(monthly.spendingByCategory)
                .slice(0, 6)
                .map(([cat, amount]) => {
                  const pct = monthly.totalSpent > 0
                    ? ((amount / monthly.totalSpent) * 100).toFixed(1)
                    : 0;
                  return (
                    <div key={cat}>
                      <div className="flex justify-between text-sm mb-1">
                        <span className="text-gray-300">{cat}</span>
                        <span className="text-white font-medium">
                          ₹{Number(amount).toLocaleString()}
                          <span className="text-gray-500 text-xs ml-1">
                            ({pct}%)
                          </span>
                        </span>
                      </div>
                      <div className="w-full bg-gray-800 rounded-full h-1.5">
                        <div
                          className="bg-green-500 h-1.5 rounded-full transition-all"
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
            </div>
          ) : (
            <div className="text-center py-8">
              <p className="text-gray-500 text-sm">No expenses this month yet.</p>
              <Link
                to="/add-expense"
                className="text-green-400 text-sm hover:underline mt-2 block"
              >
                Add your first expense
              </Link>
            </div>
          )}
        </div>

        {/* Today's Expenses */}
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-white font-semibold">Today's Expenses</h3>
            <Link
              to="/reports"
              className="text-green-400 text-xs flex items-center gap-1 hover:underline"
            >
              View all <ArrowRight size={12} />
            </Link>
          </div>

          {daily?.expenses && daily.expenses.length > 0 ? (
            <div className="space-y-3">
              {daily.expenses.slice(0, 6).map((exp) => (
                <div
                  key={exp.id}
                  className="flex items-center justify-between py-2 border-b border-gray-800 last:border-0"
                >
                  <div className="flex items-center gap-3">
                    <div className={`w-2 h-2 rounded-full shrink-0 ${exp.flaggedWasteful || exp.isFlaggedWasteful
                        ? 'bg-red-400'
                        : 'bg-green-400'
                      }`} />
                    <div>
                      <p className="text-white text-sm">{exp.description}</p>
                      <p className="text-gray-500 text-xs">{exp.category}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-white text-sm font-medium">
                      ₹{Number(exp.amount).toLocaleString()}
                    </p>
                    {(exp.flaggedWasteful || exp.isFlaggedWasteful) && (
                      <span className="text-red-400 text-xs">wasteful</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-8">
              <p className="text-gray-500 text-sm">No expenses today yet.</p>
              <Link
                to="/add-expense"
                className="text-green-400 text-sm hover:underline mt-2 block"
              >
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

import { useState, useEffect } from 'react';
import API from '../api/axios';
import { useBudget } from '../context/BudgetContext';
import {
  Target as TargetIcon,
  Plus,
  Loader,
  Trash2,
  CheckCircle,
  AlertTriangle,
  Calendar,
  Bot,
  TrendingUp,
  X,
  PiggyBank
} from 'lucide-react';

// ─── GoalCard Component ───────────────────────────────────────────
const GoalCard = ({ goal, onDelete, onAddSaving, monthlySavingAmount, savingPercentage }) => {
  const [adding, setAdding] = useState(false);
  const [amount, setAmount] = useState('');
  const [saving, setSaving] = useState(false);

  const handleAddSaving = async () => {
    if (!amount) return;
    setSaving(true);
    try {
      await onAddSaving(goal.id, parseFloat(amount));
      setAmount('');
      setAdding(false);
    } finally {
      setSaving(false);
    }
  };

  const progressColor = goal.progressPercentage >= 100
    ? 'bg-green-500'
    : goal.progressPercentage >= 60
    ? 'bg-blue-500'
    : goal.progressPercentage >= 30
    ? 'bg-yellow-500'
    : 'bg-red-500';

  return (
    <div className={`bg-gray-900 border rounded-2xl p-5 space-y-4 ${
      goal.status === 'ACHIEVED'
        ? 'border-green-500/40'
        : goal.achievable
        ? 'border-gray-800'
        : 'border-orange-500/30'
    }`}>

      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-2">
            <TargetIcon size={18} className="text-green-400" />
            <h3 className="text-white font-bold">{goal.itemName}</h3>
            {goal.status === 'ACHIEVED' && (
              <span className="text-xs bg-green-500/20 text-green-400 px-2 py-0.5 rounded-full">
                Achieved! 🎉
              </span>
            )}
          </div>
          <div className="flex items-center gap-3 mt-1">
            <span className="text-gray-400 text-sm">
              Target: ₹{Number(goal.targetAmount).toLocaleString()}
            </span>
            <span className="text-gray-500 text-xs flex items-center gap-1">
              <Calendar size={12} />
              {new Date(goal.targetDate).toLocaleDateString('en-IN', {
                month: 'short', year: 'numeric'
              })}
            </span>
          </div>
        </div>
        <button
          onClick={() => onDelete(goal.id)}
          className="text-gray-600 hover:text-red-400 transition-colors"
        >
          <Trash2 size={16} />
        </button>
      </div>

      {/* Progress Bar */}
      <div>
        <div className="flex justify-between text-sm mb-2">
          <span className="text-gray-400">
            Saved: ₹{Number(goal.currentSaved).toLocaleString()}
          </span>
          <span className="text-white font-medium">
            {(goal.progressPercentage || 0).toFixed(1)}%
          </span>
        </div>
        <div className="w-full bg-gray-800 rounded-full h-3">
          <div
            className={`${progressColor} h-3 rounded-full transition-all`}
            style={{ width: `${Math.min(goal.progressPercentage || 0, 100)}%` }}
          />
        </div>
        <div className="flex justify-between text-xs text-gray-500 mt-1">
          <span>₹0</span>
          <span>₹{Number(goal.targetAmount).toLocaleString()}</span>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-3">
        <div className="bg-gray-800 rounded-xl p-3 text-center">
          <p className="text-gray-500 text-xs">Months Left</p>
          <p className="text-white font-bold">{goal.monthsRemaining}</p>
        </div>
        <div className="bg-gray-800 rounded-xl p-3 text-center">
          <p className="text-gray-500 text-xs">Need/Month</p>
          <p className="text-blue-400 font-bold text-sm">
            ₹{Number(goal.requiredMonthlySaving).toLocaleString()}
          </p>
        </div>
        <div className="bg-gray-800 rounded-xl p-3 text-center">
          <p className="text-gray-500 text-xs">Status</p>
          <p className={`font-bold text-xs ${
            goal.achievable ? 'text-green-400' : 'text-orange-400'
          }`}>
            {goal.achievable ? '✅ On Track' : '⚠️ Tough'}
          </p>
        </div>
      </div>

      {/* Budget-linked saving capacity — passed as prop, no hook needed */}
      {monthlySavingAmount > 0 && (
        <div className="bg-gray-800 border border-blue-500/20 rounded-xl p-3 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <PiggyBank size={16} className="text-blue-400" />
            <div>
              <p className="text-white text-xs font-medium">
                Monthly Saving Capacity
              </p>
              <p className="text-gray-500 text-xs">
                {Number(savingPercentage)}% of your income
              </p>
            </div>
          </div>
          <div className="text-right">
            <p className="text-blue-400 font-bold">
              ₹{Number(monthlySavingAmount).toLocaleString()}
            </p>
            <p className="text-gray-500 text-xs">per month</p>
          </div>
        </div>
      )}

      {/* Achievability */}
      <div className={`flex items-start gap-2 rounded-xl px-3 py-2 ${
        goal.achievable ? 'bg-green-500/10' : 'bg-orange-500/10'
      }`}>
        {goal.achievable
          ? <CheckCircle size={14} className="text-green-400 shrink-0 mt-0.5" />
          : <AlertTriangle size={14} className="text-orange-400 shrink-0 mt-0.5" />
        }
        <p className={`text-xs ${
          goal.achievable ? 'text-green-400' : 'text-orange-400'
        }`}>
          {goal.achievabilityReason}
        </p>
      </div>

      {/* AI Plan */}
      {goal.aiPlan && (
        <div className="bg-gray-800 rounded-xl p-3">
          <div className="flex items-center gap-1 mb-1">
            <Bot size={13} className="text-purple-400" />
            <span className="text-purple-400 text-xs font-medium">AI Plan</span>
          </div>
          <p className="text-gray-300 text-xs leading-relaxed">{goal.aiPlan}</p>
        </div>
      )}

      {/* Add Saving */}
      {goal.status !== 'ACHIEVED' && (
        <div>
          {!adding ? (
            <button
              onClick={() => setAdding(true)}
              className="w-full bg-green-500/10 hover:bg-green-500/20 text-green-400 border border-green-500/20 rounded-xl py-2 text-sm font-medium transition-all flex items-center justify-center gap-2"
            >
              <Plus size={14} />
              Add Saving
            </button>
          ) : (
            <div className="flex gap-2">
              <input
                type="number"
                value={amount}
                onChange={e => setAmount(e.target.value)}
                className="flex-1 bg-gray-800 text-white rounded-xl px-3 py-2 border border-gray-700 focus:border-green-500 focus:outline-none text-sm"
                placeholder="Amount saved (₹)"
                autoFocus
              />
              <button
                onClick={handleAddSaving}
                disabled={saving}
                className="bg-green-500 hover:bg-green-600 text-white px-4 rounded-xl text-sm font-medium transition-all"
              >
                {saving
                  ? <Loader size={14} className="animate-spin" />
                  : 'Add'}
              </button>
              <button
                onClick={() => { setAdding(false); setAmount(''); }}
                className="bg-gray-700 hover:bg-gray-600 text-gray-300 px-3 rounded-xl transition-all"
              >
                <X size={14} />
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

// ─── Goals Page ───────────────────────────────────────────────────
const Goals = () => {
  const { budget } = useBudget(); // ← hook called here at top level only
  const [goals, setGoals] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({
    itemName: '',
    targetAmount: '',
    targetDate: '',
  });
  const [error, setError] = useState('');

  useEffect(() => {
    fetchGoals();
  }, []);

  const fetchGoals = async () => {
    setLoading(true);
    try {
      const res = await API.get('/api/goals');
      setGoals(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    setCreating(true);
    setError('');
    try {
      await API.post('/api/goals', {
        itemName: form.itemName,
        targetAmount: parseFloat(form.targetAmount),
        targetDate: form.targetDate,
      });
      setForm({ itemName: '', targetAmount: '', targetDate: '' });
      setShowForm(false);
      await fetchGoals();
    } catch (err) {
      setError('Failed to create goal. Please try again.');
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (goalId) => {
    try {
      await API.delete(`/api/goals/${goalId}`);
      setGoals(prev => prev.filter(g => g.id !== goalId));
    } catch (err) {
      console.error(err);
    }
  };

  const handleAddSaving = async (goalId, amount) => {
    try {
      const res = await API.patch(
        `/api/goals/${goalId}/save?amount=${amount}`
      );
      setGoals(prev => prev.map(g => g.id === goalId ? res.data : g));
    } catch (err) {
      console.error(err);
    }
  };

  const activeGoals = goals.filter(g => g.status === 'ACTIVE');
  const achievedGoals = goals.filter(g => g.status === 'ACHIEVED');

  // Extract budget values once here and pass as props
  const monthlySavingAmount = budget?.monthlySavingAmount || 0;
  const savingPercentage = budget?.savingPercentage || 0;

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
          <h1 className="text-2xl font-bold text-white">Financial Goals</h1>
          <p className="text-gray-400 text-sm mt-1">
            AI-powered savings plan for your wishlist
          </p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className="flex items-center gap-2 bg-green-500 hover:bg-green-600 text-white px-4 py-2 rounded-xl text-sm font-medium transition-all"
        >
          <Plus size={16} />
          New Goal
        </button>
      </div>

      {/* Budget saving capacity banner */}
      {budget && (
        <div className="bg-gray-900 border border-blue-500/20 rounded-2xl p-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <PiggyBank size={20} className="text-blue-400" />
            <div>
              <p className="text-white text-sm font-medium">
                Monthly Saving Capacity from Budget
              </p>
              <p className="text-gray-400 text-xs">
                Based on your {Number(savingPercentage)}% saving target
              </p>
            </div>
          </div>
          <div className="text-right">
            <p className="text-blue-400 font-bold text-xl">
              ₹{Number(monthlySavingAmount).toLocaleString()}
            </p>
            <p className="text-gray-500 text-xs">per month available</p>
          </div>
        </div>
      )}

      {/* Create Goal Form */}
      {showForm && (
        <div className="bg-gray-900 border border-green-500/20 rounded-2xl p-6">
          <h3 className="text-white font-semibold mb-4 flex items-center gap-2">
            <TargetIcon size={18} className="text-green-400" />
            Create New Goal
          </h3>
          <form onSubmit={handleCreate} className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div>
                <label className="text-sm text-gray-400 mb-1 block">
                  What do you want to buy?
                </label>
                <input
                  type="text"
                  value={form.itemName}
                  onChange={e => setForm({ ...form, itemName: e.target.value })}
                  className="w-full bg-gray-800 text-white rounded-xl px-4 py-3 border border-gray-700 focus:border-green-500 focus:outline-none"
                  placeholder="e.g. iPhone 15, Laptop, Bike..."
                  required
                />
              </div>
              <div>
                <label className="text-sm text-gray-400 mb-1 block">
                  Target Amount (₹)
                </label>
                <input
                  type="number"
                  value={form.targetAmount}
                  onChange={e => setForm({ ...form, targetAmount: e.target.value })}
                  className="w-full bg-gray-800 text-white rounded-xl px-4 py-3 border border-gray-700 focus:border-green-500 focus:outline-none"
                  placeholder="e.g. 80000"
                  required
                />
              </div>
              <div>
                <label className="text-sm text-gray-400 mb-1 block">
                  Target Date
                </label>
                <input
                  type="date"
                  value={form.targetDate}
                  onChange={e => setForm({ ...form, targetDate: e.target.value })}
                  className="w-full bg-gray-800 text-white rounded-xl px-4 py-3 border border-gray-700 focus:border-green-500 focus:outline-none"
                  min={new Date().toISOString().split('T')[0]}
                  required
                />
              </div>
            </div>

            <div className="bg-purple-500/10 border border-purple-500/20 rounded-xl px-4 py-3 flex items-center gap-2">
              <Bot size={16} className="text-purple-400 shrink-0" />
              <p className="text-purple-400 text-sm">
                AI will analyze your spending history and create a personalized savings plan
              </p>
            </div>

            {error && (
              <div className="bg-red-500/10 border border-red-500/20 rounded-xl px-4 py-3">
                <p className="text-red-400 text-sm">{error}</p>
              </div>
            )}

            <div className="flex gap-3">
              <button
                type="submit"
                disabled={creating}
                className="flex-1 bg-green-500 hover:bg-green-600 text-white font-semibold py-3 rounded-xl transition-all flex items-center justify-center gap-2"
              >
                {creating
                  ? <><Loader size={16} className="animate-spin" /> AI is creating your plan...</>
                  : <><TrendingUp size={16} /> Create Goal with AI Plan</>
                }
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="px-6 bg-gray-800 hover:bg-gray-700 text-gray-300 rounded-xl transition-all"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Stats Bar */}
      <div className="grid grid-cols-3 gap-4">
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-4 text-center">
          <p className="text-gray-400 text-xs mb-1">Active Goals</p>
          <p className="text-2xl font-bold text-white">{activeGoals.length}</p>
        </div>
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-4 text-center">
          <p className="text-gray-400 text-xs mb-1">Achieved</p>
          <p className="text-2xl font-bold text-green-400">{achievedGoals.length}</p>
        </div>
        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-4 text-center">
          <p className="text-gray-400 text-xs mb-1">Total Saved</p>
          <p className="text-2xl font-bold text-blue-400">
            ₹{goals.reduce((sum, g) =>
              sum + Number(g.currentSaved || 0), 0
            ).toLocaleString()}
          </p>
        </div>
      </div>

      {/* Active Goals */}
      {activeGoals.length > 0 && (
        <div>
          <h2 className="text-white font-semibold mb-4 flex items-center gap-2">
            <TargetIcon size={18} className="text-green-400" />
            Active Goals
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {activeGoals.map(goal => (
              <GoalCard
                key={goal.id}
                goal={goal}
                onDelete={handleDelete}
                onAddSaving={handleAddSaving}
                monthlySavingAmount={monthlySavingAmount}
                savingPercentage={savingPercentage}
              />
            ))}
          </div>
        </div>
      )}

      {/* Achieved Goals */}
      {achievedGoals.length > 0 && (
        <div>
          <h2 className="text-white font-semibold mb-4 flex items-center gap-2">
            <CheckCircle size={18} className="text-green-400" />
            Achieved Goals 🎉
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {achievedGoals.map(goal => (
              <GoalCard
                key={goal.id}
                goal={goal}
                onDelete={handleDelete}
                onAddSaving={handleAddSaving}
                monthlySavingAmount={monthlySavingAmount}
                savingPercentage={savingPercentage}
              />
            ))}
          </div>
        </div>
      )}

      {/* Empty State */}
      {goals.length === 0 && (
        <div className="text-center py-16 bg-gray-900 border border-gray-800 rounded-2xl">
          <TargetIcon size={48} className="text-gray-700 mx-auto mb-4" />
          <h3 className="text-white font-semibold text-lg mb-2">
            No goals yet
          </h3>
          <p className="text-gray-500 text-sm mb-4">
            Set a financial goal and let AI create a personalized savings plan for you
          </p>
          <button
            onClick={() => setShowForm(true)}
            className="bg-green-500 hover:bg-green-600 text-white px-6 py-2 rounded-xl text-sm font-medium transition-all"
          >
            Create Your First Goal
          </button>
        </div>
      )}
    </div>
  );
};

export default Goals;
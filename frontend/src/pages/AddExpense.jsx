import { useState, useEffect } from 'react';
import API from '../api/axios';
import { PlusCircle, Loader, CheckCircle, AlertTriangle, Sparkles } from 'lucide-react';
import { useBudget } from '../context/BudgetContext';

const AddExpense = () => {
    const [form, setForm] = useState({ amount: '', description: '', expenseDate: '', splitCount: '' });
    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(false);
    const [result, setResult] = useState(null);
    const [error, setError] = useState('');
    const { budget, fetchBudget } = useBudget();

    useEffect(() => {
        API.get('/api/categories').then(res => setCategories(res.data));
    }, []);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError('');
        setResult(null);
        try {
            const payload = {
                amount: parseFloat(form.amount),
                description: form.description,
                expenseDate: form.expenseDate || undefined,
                splitCount: form.splitCount ? parseInt(form.splitCount) : undefined, // ← add this
            };
            const res = await API.post('/api/expenses', payload);
            setResult(res.data);
            await fetchBudget();
            setForm({ amount: '', description: '', expenseDate: '', splitCount: '' });
        } catch (err) {
            setError('Failed to add expense. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="max-w-2xl mx-auto space-y-6">
            <div>
                <h1 className="text-2xl font-bold text-white">Add Expense</h1>
                <p className="text-gray-400 text-sm mt-1">AI will automatically categorize your expense</p>
            </div>

            <div className="bg-gray-900 border border-gray-800 rounded-2xl p-6">
                <form onSubmit={handleSubmit} className="space-y-5">
                    <div>
                        <label className="text-sm text-gray-400 mb-1 block">Amount (₹)</label>
                        <input
                            type="number"
                            value={form.amount}
                            onChange={e => setForm({ ...form, amount: e.target.value })}
                            className="w-full bg-gray-800 text-white rounded-xl px-4 py-3 border border-gray-700 focus:border-green-500 focus:outline-none text-lg font-semibold"
                            placeholder="0.00"
                            required
                            min="0"
                            step="0.01"
                        />
                    </div>

                    <div>
                        <label className="text-sm text-gray-400 mb-1 block">Description</label>
                        <input
                            type="text"
                            value={form.description}
                            onChange={e => setForm({ ...form, description: e.target.value })}
                            className="w-full bg-gray-800 text-white rounded-xl px-4 py-3 border border-gray-700 focus:border-green-500 focus:outline-none"
                            placeholder="e.g. Lunch at restaurant, Uber ride, Netflix..."
                            required
                        />
                    </div>

                    <div>
                        <label className="text-sm text-gray-400 mb-1 block">Date (optional)</label>
                        <input
                            type="date"
                            value={form.expenseDate}
                            onChange={e => setForm({ ...form, expenseDate: e.target.value })}
                            className="w-full bg-gray-800 text-white rounded-xl px-4 py-3 border border-gray-700 focus:border-green-500 focus:outline-none"
                        />
                    </div>
                    <div>
                        <label className="text-sm text-gray-400 mb-1 block">
                            Split between (optional)
                        </label>
                        <input
                            type="number"
                            value={form.splitCount}
                            onChange={e => setForm({ ...form, splitCount: e.target.value })}
                            className="w-full bg-gray-800 text-white rounded-xl px-4 py-3 border border-gray-700 focus:border-green-500 focus:outline-none"
                            placeholder="e.g. 4 (if splitting with 3 friends)"
                            min="2"
                        />
                        {form.splitCount > 1 && form.amount && (
                            <p className="text-green-400 text-xs mt-1">
                                Your share: ₹{(parseFloat(form.amount) / parseInt(form.splitCount)).toFixed(2)}
                            </p>
                        )}
                    </div>

                    <div className="flex items-center gap-2 bg-green-500/10 border border-green-500/20 rounded-xl px-4 py-3">
                        <Sparkles size={16} className="text-green-400" />
                        <p className="text-green-400 text-sm">AI will auto-categorize based on your description</p>
                    </div>

                    {error && (
                        <div className="bg-red-500/10 border border-red-500/30 text-red-400 rounded-xl px-4 py-3 text-sm">
                            {error}
                        </div>
                    )}

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full bg-green-500 hover:bg-green-600 text-white font-semibold py-3 rounded-xl transition-all flex items-center justify-center gap-2"
                    >
                        {loading ? (
                            <>
                                <Loader size={18} className="animate-spin" />
                                AI is categorizing...
                            </>
                        ) : (
                            <>
                                <PlusCircle size={18} />
                                Add Expense
                            </>
                        )}
                    </button>
                </form>
            </div>
            {/* Budget Warning */}
            {budget && (
                <div className={`rounded-xl px-4 py-3 text-sm flex items-center gap-2 ${budget.overBudget
                        ? 'bg-red-500/10 border border-red-500/20'
                        : budget.budgetUsedPercentage > 80
                            ? 'bg-yellow-500/10 border border-yellow-500/20'
                            : 'bg-gray-800'
                    }`}>
                    {budget.overBudget ? (
                        <>
                            <AlertTriangle size={16} className="text-red-400 shrink-0" />
                            <span className="text-red-400">
                                ⚠️ You are over budget! Remaining: -₹{Math.abs(Number(budget.remainingBudget)).toLocaleString()}
                            </span>
                        </>
                    ) : budget.budgetUsedPercentage > 80 ? (
                        <>
                            <AlertTriangle size={16} className="text-yellow-400 shrink-0" />
                            <span className="text-yellow-400">
                                ⚠️ {budget.budgetUsedPercentage.toFixed(0)}% of budget used. Remaining: ₹{Number(budget.remainingBudget).toLocaleString()}
                            </span>
                        </>
                    ) : (
                        <span className="text-gray-400">
                            💰 Remaining budget: ₹{Number(budget.remainingBudget || 0).toLocaleString()}
                        </span>
                    )}
                </div>
            )}

            {/* AI Result Card */}
            {result && (
                <div className={`border rounded-2xl p-6 ${result.flaggedWasteful ? 'bg-red-500/5 border-red-500/30' : 'bg-green-500/5 border-green-500/30'}`}>
                    <div className="flex items-center gap-2 mb-4">
                        {result.flaggedWasteful
                            ? <AlertTriangle size={20} className="text-red-400" />
                            : <CheckCircle size={20} className="text-green-400" />
                        }
                        <span className={`font-semibold ${result.flaggedWasteful ? 'text-red-400' : 'text-green-400'}`}>
                            {result.flaggedWasteful ? 'Wasteful Expense Detected' : 'Expense Added Successfully'}
                        </span>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <p className="text-gray-500 text-xs">Amount</p>
                            <p className="text-white font-bold text-lg">₹{Number(result.amount).toLocaleString()}</p>
                        </div>
                        <div>
                            <p className="text-gray-500 text-xs">Category</p>
                            <p className="text-white font-semibold">{result.category?.name || 'Uncategorized'}</p>
                        </div>
                        <div>
                            <p className="text-gray-500 text-xs">Type</p>
                            <span className={`text-xs px-2 py-1 rounded-full font-medium
                ${result.category?.type === 'NEED' ? 'bg-blue-500/20 text-blue-400' :
                                    result.category?.type === 'INVESTMENT' ? 'bg-purple-500/20 text-purple-400' :
                                        'bg-orange-500/20 text-orange-400'}`}>
                                {result.category?.type}
                            </span>
                        </div>
                        <div>
                            <p className="text-gray-500 text-xs">AI Confidence</p>
                            <p className="text-white font-semibold">
                                {((result.aiCategoryConfidence || 0) * 100).toFixed(0)}%
                            </p>
                        </div>
                    </div>

                    {result.aiReason && (
                        <div className="mt-4 bg-gray-800 rounded-xl px-4 py-3">
                            <p className="text-gray-500 text-xs mb-1">AI Reason</p>
                            <p className="text-gray-300 text-sm">{result.aiReason}</p>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

export default AddExpense;
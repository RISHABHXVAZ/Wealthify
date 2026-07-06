import { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import API from '../api/axios';
import { TrendingUp, Lock, Loader, Eye, EyeOff } from 'lucide-react';

const ResetPassword = () => {
  const [searchParams] = useSearchParams();
  const [token, setToken] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    const tokenParam = searchParams.get('token');
    if (tokenParam) {
      setToken(tokenParam);
    } else {
      setError('Invalid link. Missing recovery token.');
    }
  }, [searchParams]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!token) return setError('Missing validation token.');
    setLoading(true);
    setError('');
    
    try {
      const res = await API.post('/api/auth/reset-password', { token, newPassword });
      setSuccess(res.data.message || 'Password updated! Redirecting to login...');
      setTimeout(() => navigate('/login'), 2500);
    } catch (err) {
      setError(err.response?.data?.message || 'Token is invalid or has expired.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="flex items-center justify-center gap-2 mb-2">
            <TrendingUp className="text-green-400" size={36} />
            <h1 className="text-3xl font-bold text-green-400">Wealthify</h1>
          </div>
          <p className="text-gray-400">Secure Account Recovery</p>
        </div>

        <div className="bg-gray-900 rounded-2xl p-8 shadow-2xl border border-gray-800">
          <h2 className="text-xl font-semibold text-white mb-6">Create New Password</h2>

          {error && <div className="bg-red-500/10 border border-red-500/30 text-red-400 rounded-lg px-4 py-3 mb-4 text-sm">{error}</div>}
          {success && <div className="bg-green-500/10 border border-green-500/30 text-green-400 rounded-lg px-4 py-3 mb-4 text-sm">{success}</div>}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="text-sm text-gray-400 mb-1 block">New Password</label>
              <div className="relative">
                <Lock className="absolute left-3 top-3 text-gray-500" size={16} />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={newPassword}
                  onChange={e => setNewPassword(e.target.value)}
                  className="w-full bg-gray-800 text-white rounded-lg pl-9 pr-10 py-2.5 border border-gray-700 focus:border-green-500 focus:outline-none text-sm"
                  placeholder="••••••••"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-3 text-gray-500 hover:text-gray-300"
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading || !token}
              className="w-full bg-green-500 hover:bg-green-600 text-white font-semibold py-2.5 rounded-lg transition-all flex items-center justify-center gap-2 mt-2 disabled:opacity-50"
            >
              {loading && <Loader size={16} className="animate-spin" />}
              Update Password
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default ResetPassword;
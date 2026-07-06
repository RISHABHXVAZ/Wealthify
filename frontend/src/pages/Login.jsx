import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import API from '../api/axios';
import { TrendingUp, Mail, Lock, Loader, Eye, EyeOff } from 'lucide-react';

const Login = () => {
  const [form, setForm] = useState({ email: '', password: '' });
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  
  const [isForgotPassword, setIsForgotPassword] = useState(false);
  const [forgotEmail, setForgotEmail] = useState('');

  const { login } = useAuth();
  const navigate = useNavigate();

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccess('');
    try {
      const res = await API.post('/api/auth/login', form);
      login({ name: res.data.name, email: res.data.email }, res.data.token);
      navigate('/dashboard');
    } catch (err) {
      setError('Invalid email or password');
    } finally {
      setLoading(false);
    }
  };

  const handleForgotSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccess('');
    try {
      const res = await API.post('/api/auth/forgot-password', { email: forgotEmail });
      setSuccess(res.data.message);
    } catch (err) {
      setError('Something went wrong. Please check your network connection.');
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
          <p className="text-gray-400">AI-Powered Expense Tracker</p>
        </div>

        <div className="bg-gray-900 rounded-2xl p-8 shadow-2xl border border-gray-800">
          {!isForgotPassword ? (
            <>
              <h2 className="text-xl font-semibold text-white mb-6">Welcome back</h2>

              {error && (
                <div className="bg-red-500/10 border border-red-500/30 text-red-400 rounded-lg px-4 py-3 mb-4 text-sm">
                  {error}
                </div>
              )}

              <form onSubmit={handleLoginSubmit} className="space-y-4">
                <div>
                  <label className="text-sm text-gray-400 mb-1 block">Email</label>
                  <div className="relative">
                    <Mail className="absolute left-3 top-3 text-gray-500" size={16} />
                    <input
                      type="email"
                      value={form.email}
                      onChange={e => setForm({ ...form, email: e.target.value })}
                      className="w-full bg-gray-800 text-white rounded-lg pl-9 pr-4 py-2.5 border border-gray-700 focus:border-green-500 focus:outline-none text-sm"
                      placeholder="you@example.com"
                      required
                    />
                  </div>
                </div>

                <div>
                  <div className="flex justify-between items-center mb-1">
                    <label className="text-sm text-gray-400 block">Password</label>
                    <button 
                      type="button" 
                      onClick={() => setIsForgotPassword(true)}
                      className="text-xs text-green-400 hover:underline focus:outline-none"
                    >
                      Forgot Password?
                    </button>
                  </div>
                  <div className="relative">
                    <Lock className="absolute left-3 top-3 text-gray-500" size={16} />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      value={form.password}
                      onChange={e => setForm({ ...form, password: e.target.value })}
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
                  disabled={loading}
                  className="w-full bg-green-500 hover:bg-green-600 text-white font-semibold py-2.5 rounded-lg transition-all flex items-center justify-center gap-2 mt-2"
                >
                  {loading ? <Loader size={16} className="animate-spin" /> : null}
                  {loading ? 'Signing in...' : 'Sign In'}
                </button>
              </form>
            </>
          ) : (
            <>
              <h2 className="text-xl font-semibold text-white mb-2">Reset Password</h2>
              <p className="text-xs text-gray-400 mb-6">
                Enter your account email. If valid, we will email you a secure confirmation recovery link.
              </p>

              {error && <div className="bg-red-500/10 border border-red-500/30 text-red-400 rounded-lg px-4 py-3 mb-4 text-sm">{error}</div>}
              {success && <div className="bg-green-500/10 border border-green-500/30 text-green-400 rounded-lg px-4 py-3 mb-4 text-sm">{success}</div>}

              <form onSubmit={handleForgotSubmit} className="space-y-4">
                <div>
                  <label className="text-sm text-gray-400 mb-1 block">Account Email</label>
                  <div className="relative">
                    <Mail className="absolute left-3 top-3 text-gray-500" size={16} />
                    <input
                      type="email"
                      value={forgotEmail}
                      onChange={e => setForgotEmail(e.target.value)}
                      className="w-full bg-gray-800 text-white rounded-lg pl-9 pr-4 py-2.5 border border-gray-700 focus:border-green-500 focus:outline-none text-sm"
                      placeholder="you@example.com"
                      required
                    />
                  </div>
                </div>
                <button
                  type="submit"
                  disabled={loading}
                  className="w-full bg-green-500 hover:bg-green-600 text-white font-semibold py-2.5 rounded-lg transition-all flex items-center justify-center gap-2"
                >
                  {loading ? <Loader size={16} className="animate-spin" /> : null}
                  {loading ? 'Sending link...' : 'Send Recovery Email'}
                </button>
              </form>

              <button
                type="button"
                onClick={() => { setIsForgotPassword(false); setSuccess(''); setError(''); }}
                className="w-full text-center text-sm text-gray-400 hover:text-white mt-4 block transition-colors"
              >
                Back to Sign In
              </button>
            </>
          )}

          {!isForgotPassword && (
            <p className="text-center text-gray-400 text-sm mt-6">
              Don't have an account?{' '}
              <Link to="/register" className="text-green-400 hover:underline">
                Register
              </Link>
            </p>
          )}
        </div>
      </div>
    </div>
  );
};

export default Login;
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  LayoutDashboard, PlusCircle, BarChart2,
  AlertTriangle, TrendingUp, LogOut
} from 'lucide-react';

const Navbar = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const navItems = [
    { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { path: '/add-expense', label: 'Add Expense', icon: PlusCircle },
    { path: '/reports', label: 'Reports', icon: BarChart2 },
    { path: '/insights', label: 'Insights', icon: AlertTriangle },
    { path: '/stocks', label: 'Stocks', icon: TrendingUp },
  ];

  return (
    <nav className="bg-gray-900 text-white shadow-lg">
      <div className="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <TrendingUp className="text-green-400" size={24} />
          <span className="text-xl font-bold text-green-400">Wealthify</span>
        </div>

        <div className="flex items-center gap-1">
          {navItems.map(({ path, label, icon: Icon }) => (
            <Link
              key={path}
              to={path}
              className={`flex items-center gap-1 px-3 py-2 rounded-lg text-sm font-medium transition-all
                ${location.pathname === path
                  ? 'bg-green-500 text-white'
                  : 'text-gray-300 hover:bg-gray-700'}`}
            >
              <Icon size={16} />
              <span className="hidden md:inline">{label}</span>
            </Link>
          ))}
        </div>

        <div className="flex items-center gap-3">
          <span className="text-sm text-gray-300 hidden md:block">
            Hi, {user?.name?.split(' ')[0]}
          </span>
          <button
            onClick={handleLogout}
            className="flex items-center gap-1 px-3 py-2 rounded-lg text-sm text-red-400 hover:bg-gray-700 transition-all"
          >
            <LogOut size={16} />
          </button>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
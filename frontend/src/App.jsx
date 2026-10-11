import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import Navbar from './components/Navbar';
import { Loader } from 'lucide-react';

const Login = lazy(() => import('./pages/Login'));
const Register = lazy(() => import('./pages/Register'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const AddExpense = lazy(() => import('./pages/AddExpense'));
const Reports = lazy(() => import('./pages/Reports'));
const Insights = lazy(() => import('./pages/Insights'));
const StockAdvisor = lazy(() => import('./pages/StockAdvisor'));
const Budget = lazy(() => import('./pages/Budget'));
const ResetPassword = lazy(() => import('./pages/ResetPassword'));
const Goals = lazy(() => import('./pages/Goals'));

const PageLoader = () => (
  <div className="flex items-center justify-center min-h-[50vh]">
    <Loader className="animate-spin text-green-400" size={32} />
  </div>
);

const Layout = ({ children }) => (
  <div className="min-h-screen bg-gray-950">
    <Navbar />
    <main className="max-w-7xl mx-auto px-4 py-6">
      <Suspense fallback={<PageLoader />}>
        {children}
      </Suspense>
    </main>
  </div>
);

function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<PageLoader />}>
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/dashboard" element={
            <ProtectedRoute><Layout><Dashboard /></Layout></ProtectedRoute>
          } />
          <Route path="/add-expense" element={
            <ProtectedRoute><Layout><AddExpense /></Layout></ProtectedRoute>
          } />
          <Route path="/reports" element={
            <ProtectedRoute><Layout><Reports /></Layout></ProtectedRoute>
          } />
          <Route path="/insights" element={
            <ProtectedRoute><Layout><Insights /></Layout></ProtectedRoute>
          } />
          <Route path="/budget" element={
            <ProtectedRoute><Layout><Budget /></Layout></ProtectedRoute>
          } />
          <Route path="/goals" element={
            <ProtectedRoute><Layout><Goals /></Layout></ProtectedRoute>
          } />
          <Route path="/stocks" element={
            <ProtectedRoute><Layout><StockAdvisor /></Layout></ProtectedRoute>
          } />

          <Route path="/reset-password" element={<ResetPassword />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;

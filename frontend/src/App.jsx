import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import Navbar from './components/Navbar';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import AddExpense from './pages/AddExpense';
import Reports from './pages/Reports';
import Insights from './pages/Insights';
import StockAdvisor from './pages/StockAdvisor';
import Budget from './pages/Budget';
import Goals from './pages/Goals';

const Layout = ({ children }) => (
  <div className="min-h-screen bg-gray-950">
    <Navbar />
    <main className="max-w-7xl mx-auto px-4 py-6">{children}</main>
  </div>
);

function App() {
  return (
    <BrowserRouter>
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
      </Routes>
    </BrowserRouter>
  );
}

export default App;
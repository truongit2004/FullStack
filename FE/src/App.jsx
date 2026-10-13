import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import Navbar from './components/Navbar';
import Login from './pages/Login';
import Register from './pages/Register';
import ForgotPassword from './pages/ForgotPassword';
import Products from './pages/Products';
import ProductDetail from './pages/ProductDetail';
import Cart from './pages/Cart';
import Checkout from './pages/Checkout';
import OrderHistory from './pages/OrderHistory';
import Admin from './pages/Admin';
import AdminChat from './pages/AdminChat';
import AdminCoupons from './pages/AdminCoupons';
import Success from './pages/Success';
import Profile from './pages/Profile';
import Points from './pages/Points';
import ChatWidget from './components/ChatWidget';
import { useAuth } from './context/AuthContext';

function App() {
  const { token, user, loading } = useAuth();

  // If loading user state initially
  if (loading) return <div className="loading-screen">Authenticating...</div>;

  return (
    <>
      <Navbar />
      <Routes>
        <Route path="/" element={<Navigate to="/products" />} />
        <Route path="/products" element={<Products />} />
        <Route path="/products/:id" element={<ProductDetail />} />
        
        <Route path="/cart" element={token ? <Cart /> : <Navigate to="/login" />} />
        <Route path="/checkout" element={token ? <Checkout /> : <Navigate to="/login" />} />
        <Route path="/orders" element={token ? <OrderHistory /> : <Navigate to="/login" />} />
        <Route path="/profile" element={token ? <Profile /> : <Navigate to="/login" />} />
        <Route path="/points" element={token ? <Points /> : <Navigate to="/login" />} />
        
        {/* Protected Admin Route */}
        <Route 
          path="/admin" 
          element={token && user?.role === 'ADMIN' ? <Admin /> : <Navigate to="/products" />} 
        />

        <Route 
          path="/admin/chat" 
          element={token && user?.role === 'ADMIN' ? <AdminChat /> : <Navigate to="/products" />} 
        />

        <Route
          path="/admin/coupons"
          element={token && user?.role === 'ADMIN' ? <AdminCoupons /> : <Navigate to="/products" />}
        />

        <Route path="/success" element={token ? <Success /> : <Navigate to="/login" />} />
        <Route path="/login" element={token ? <Navigate to="/products" /> : <Login />} />
        <Route path="/register" element={token ? <Navigate to="/products" /> : <Register />} />
        <Route path="/forgot-password" element={token ? <Navigate to="/products" /> : <ForgotPassword />} />
      </Routes>
      <ChatWidget />
    </>
  );
}

export default App;

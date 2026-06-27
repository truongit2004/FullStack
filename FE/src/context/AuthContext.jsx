import React, { createContext, useContext, useState, useEffect } from 'react';
import axios from 'axios';
import authService from '../services/AuthService';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(localStorage.getItem('token'));
  const [loading, setLoading] = useState(true);

  // Hàm giải mã JWT token đơn giản
  const decodeToken = (t) => {
    try {
      const base64Url = t.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      }).join(''));
      return JSON.parse(jsonPayload);
    } catch (e) {
      return null;
    }
  };

  useEffect(() => {
    if (token) {
      axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
      const decoded = decodeToken(token);
      if (decoded) {
        setUser({
          id: decoded.userId || decoded.id, 
          username: decoded.sub || decoded.username,
          role: decoded.role 
        });
      }
      validateToken(token);
    } else {
      setLoading(false);
    }
  }, [token]);

  const validateToken = async (t) => {
    setLoading(true);
    try {
      await authService.validateToken(t);
      setLoading(false);
    } catch (error) {
      console.error("Token validation failed", error);
      if (error.response && (error.response.status === 401 || error.response.status === 403)) {
        logout();
      }
      setLoading(false);
    }
  };

  const login = async (username, password) => {
    try {
      const response = await authService.login(username, password);
      const newToken = response.data.token;

      axios.defaults.headers.common['Authorization'] = `Bearer ${newToken}`;
      localStorage.setItem('token', newToken);
      setToken(newToken);
      
      const decoded = decodeToken(newToken);
      if (decoded) {
        setUser({ 
          id: decoded.userId || decoded.id, 
          username: decoded.sub || decoded.username, 
          role: decoded.role 
        });
      }

      return { success: true };
    } catch (error) {
      return { success: false, message: error.response?.data?.message || "Login failed" };
    }
  };

  const register = async (userData) => {
    try {
      await authService.register(userData);
      return { success: true };
    } catch (error) {
      return { success: false, message: error.response?.data?.message || "Registration failed" };
    }
  };

  const logout = async () => {
    const currentToken = token;

    setToken(null);
    setUser(null);
    localStorage.removeItem('token');
    delete axios.defaults.headers.common['Authorization'];

    if (currentToken) {
      try {
        authService.logout(currentToken).catch(err => console.log("Server logout failed, session cleared locally"));
      } catch (e) {
        console.error("Logout from server failed");
      }
    }
  };

  return (
    <AuthContext.Provider value={{ user, token, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);

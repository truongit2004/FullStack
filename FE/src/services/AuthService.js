import api from '../api/axios';
import axios from 'axios';

// AuthService dùng baseURL từ API Gateway (port 8085)
const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8085';

class AuthService {
    async login(username, password) {
        return await axios.post(`${BASE}/auth/login`, { username, password });
    }

    async register(userData) {
        return await axios.post(`${BASE}/auth/register`, userData);
    }

    async validateToken(token) {
        return await axios.get(`${BASE}/auth/validate?token=${token}`);
    }

    async logout(token) {
        return await axios.post(`${BASE}/auth/logout`, {}, {
            headers: { Authorization: `Bearer ${token}` }
        });
    }
}

export default new AuthService();

import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate, Link } from 'react-router-dom';

const Login = () => {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loadingMsg, setLoadingMsg] = useState(false);
    const { login, token } = useAuth();
    const navigate = useNavigate();

    // Tự động chuyển hướng ngay khi nhận thấy token thay đổi

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setLoadingMsg(true);
        const res = await login(username, password);
        if (res.success) {
            navigate('/success');
        } else {
            setError(res.message);
            setLoadingMsg(false);
        }
    };

    return (
        <div className="auth-container">
            <div className="auth-form glass-card fade-in">
                <h1>Welcome Back</h1>
                <p style={{ textAlign: 'center', color: 'rgba(255,255,255,0.6)', marginBottom: '2rem' }}>Sign in to continue to your account</p>
                {error && <div className="error-message">{error}</div>}
                <form onSubmit={handleSubmit}>
                    <div className="input-group">
                        <label>Username</label>
                        <input
                            type="text"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            required
                            placeholder="Type your username"
                        />
                    </div>
                    <div className="input-group">
                        <label>Password</label>
                        <input
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            required
                            placeholder="Enter your password"
                        />
                    </div>
                    <button type="submit" disabled={loadingMsg}>
                        {loadingMsg ? 'Đang xác thực...' : 'Sign In'}
                    </button>
                </form>
                <div className="auth-toggle">
                    Don't have an account? <Link to="/register">Create one</Link>
                </div>
            </div>
        </div>
    );
};

export default Login;

import React from 'react';
import { useNavigate } from 'react-router-dom';

const Success = () => {
    const navigate = useNavigate();

    return (
        <div className="auth-container">
            <div className="auth-form glass-card fade-in" style={{ textAlign: 'center', maxWidth: '500px' }}>
                <div style={{ marginBottom: '2rem', fontSize: '5rem' }}>
                    ✅
                </div>
                <h1 style={{ background: 'linear-gradient(to right, #10b981, #3b82f6)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                    Chúc mừng!
                </h1>
                <p style={{ fontSize: '1.2rem', color: 'rgba(255,255,255,0.8)', margin: '1.5rem 0' }}>
                    Bạn đã đăng nhập thành công vào hệ thống.
                </p>
                <button onClick={() => navigate('/')}>Đi đến trang chủ</button>
            </div>
        </div>
    );
};

export default Success;

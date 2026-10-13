import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import authService from '../services/AuthService';

const ForgotPassword = () => {
    const navigate = useNavigate();
    const [step, setStep] = useState(1); // 1: request OTP, 2: reset password
    const [email, setEmail] = useState('');
    const [otp, setOtp] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [error, setError] = useState('');
    const [info, setInfo] = useState('');
    const [loading, setLoading] = useState(false);

    const handleRequestOtp = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            await authService.forgotPassword(email);
            setInfo('Nếu email tồn tại trong hệ thống, mã xác nhận đã được gửi tới hộp thư của bạn.');
            setStep(2);
        } catch (err) {
            setError(err.response?.data?.message || 'Gửi mã xác nhận thất bại. Vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    };

    const handleResetPassword = async (e) => {
        e.preventDefault();
        setError('');
        if (newPassword !== confirmPassword) {
            setError('Mật khẩu xác nhận không khớp.');
            return;
        }
        setLoading(true);
        try {
            await authService.resetPassword(email, otp, newPassword);
            alert('Đặt lại mật khẩu thành công! Vui lòng đăng nhập lại.');
            navigate('/login');
        } catch (err) {
            setError(err.response?.data?.message || 'Mã xác nhận không đúng hoặc đã hết hạn.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="auth-container">
            <div className="auth-form glass-card fade-in">
                <h1>{step === 1 ? 'Quên mật khẩu' : 'Đặt lại mật khẩu'}</h1>
                <p style={{ textAlign: 'center', color: 'rgba(255,255,255,0.6)', marginBottom: '2rem' }}>
                    {step === 1
                        ? 'Nhập email của bạn để nhận mã xác nhận đặt lại mật khẩu'
                        : `Nhập mã xác nhận đã gửi tới ${email} và mật khẩu mới`}
                </p>
                {error && <div className="error-message">{error}</div>}
                {info && step === 2 && <div className="error-message" style={{ background: 'rgba(16,185,129,0.15)', color: '#34d399' }}>{info}</div>}

                {step === 1 ? (
                    <form onSubmit={handleRequestOtp}>
                        <div className="input-group">
                            <label>Email</label>
                            <input
                                type="email"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                required
                                placeholder="you@example.com"
                            />
                        </div>
                        <button type="submit" disabled={loading}>
                            {loading ? 'Đang gửi...' : 'Gửi mã xác nhận'}
                        </button>
                    </form>
                ) : (
                    <form onSubmit={handleResetPassword}>
                        <div className="input-group">
                            <label>Mã xác nhận (OTP)</label>
                            <input
                                type="text"
                                value={otp}
                                onChange={(e) => setOtp(e.target.value)}
                                required
                                placeholder="6 chữ số"
                                maxLength={6}
                            />
                        </div>
                        <div className="input-group">
                            <label>Mật khẩu mới</label>
                            <input
                                type="password"
                                value={newPassword}
                                onChange={(e) => setNewPassword(e.target.value)}
                                required
                                placeholder="Mật khẩu mới"
                            />
                        </div>
                        <div className="input-group">
                            <label>Xác nhận mật khẩu mới</label>
                            <input
                                type="password"
                                value={confirmPassword}
                                onChange={(e) => setConfirmPassword(e.target.value)}
                                required
                                placeholder="Nhập lại mật khẩu mới"
                            />
                        </div>
                        <button type="submit" disabled={loading}>
                            {loading ? 'Đang xử lý...' : 'Đặt lại mật khẩu'}
                        </button>
                    </form>
                )}

                <div className="auth-toggle">
                    <Link to="/login">Quay lại đăng nhập</Link>
                </div>
            </div>
        </div>
    );
};

export default ForgotPassword;

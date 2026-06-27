import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate, Link } from 'react-router-dom';

const Register = () => {
    const [formData, setFormData] = useState({
        username: '',
        email: '',
        password: '',
        firstName: '',
        lastName: '',
        role: 'USER'
    });
    const [error, setError] = useState('');
    const { register } = useAuth();
    const navigate = useNavigate();

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        const res = await register(formData);
        if (res.success) {
            navigate('/login');
        } else {
            setError(res.message);
        }
    };

    return (
        <div className="auth-container">
            <div className="auth-form glass-card fade-in">
                <h1>Create Account</h1>
                <p style={{textAlign: 'center', color: 'rgba(255,255,255,0.6)', marginBottom: '2rem'}}>Join our community today</p>
                {error && <div className="error-message">{error}</div>}
                <form onSubmit={handleSubmit}>
                    <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem'}}>
                        <div className="input-group">
                            <label>First Name</label>
                            <input name="firstName" type="text" onChange={handleChange} required placeholder="First" />
                        </div>
                        <div className="input-group">
                            <label>Last Name</label>
                            <input name="lastName" type="text" onChange={handleChange} required placeholder="Last" />
                        </div>
                    </div>
                    <div className="input-group">
                        <label>Username</label>
                        <input name="username" type="text" onChange={handleChange} required placeholder="Username" />
                    </div>
                    <div className="input-group">
                        <label>Email Address</label>
                        <input name="email" type="email" onChange={handleChange} required placeholder="Email" />
                    </div>
                    <div className="input-group">
                        <label>Password</label>
                        <input name="password" type="password" onChange={handleChange} required placeholder="Password" />
                    </div>
                    <button type="submit">Sign Up</button>
                </form>
                <div className="auth-toggle">
                    Already have an account? <Link to="/login">Sign In</Link>
                </div>
            </div>
        </div>
    );
};

export default Register;

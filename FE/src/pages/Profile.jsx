import React, { useState, useEffect } from 'react';
import userService from '../services/UserService';
import authService from '../services/AuthService';
import { useAuth } from '../context/AuthContext';
import pointService from '../services/PointService';
import { Link } from 'react-router-dom';
import { User, Mail, MapPin, Calendar, Star, Package, Coins } from 'lucide-react';

const Profile = () => {
    const { user, token } = useAuth();
    const [profile, setProfile] = useState(null);
    const [loading, setLoading] = useState(true);
    const [pointBalance, setPointBalance] = useState(0);
    const [showEditModal, setShowEditModal] = useState(false);
    const [profileForm, setProfileForm] = useState({ firstName: '', lastName: '', email: '' });
    const [passwordForm, setPasswordForm] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' });
    const [saving, setSaving] = useState(false);
    const [formError, setFormError] = useState('');
    const [formSuccess, setFormSuccess] = useState('');

    const fetchProfile = async () => {
        try {
            // Fetch full integrated profile (User + Orders + Reviews)
            const data = await userService.getFullProfile(user.id);
            setProfile(data);
            setProfileForm({
                firstName: data.userDTO.firstName || '',
                lastName: data.userDTO.lastName || '',
                email: data.userDTO.email || ''
            });
        } catch (err) {
            console.error("Failed to load profile", err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (!user) return;
        fetchProfile();
        pointService.getBalance(user.id)
            .then(res => setPointBalance(Number(res.balance || 0)))
            .catch(err => console.error("Failed to load point balance", err));
    }, [user]);

    const handleProfileSave = async (e) => {
        e.preventDefault();
        setFormError('');
        setFormSuccess('');
        setSaving(true);
        try {
            await userService.updateProfile(user.id, profileForm);
            setFormSuccess('Cập nhật hồ sơ thành công!');
            await fetchProfile();
        } catch (err) {
            setFormError(err.response?.data?.message || 'Cập nhật hồ sơ thất bại.');
        } finally {
            setSaving(false);
        }
    };

    const handlePasswordSave = async (e) => {
        e.preventDefault();
        setFormError('');
        setFormSuccess('');
        if (passwordForm.newPassword !== passwordForm.confirmPassword) {
            setFormError('Mật khẩu xác nhận không khớp.');
            return;
        }
        setSaving(true);
        try {
            await authService.changePassword(token, passwordForm.oldPassword, passwordForm.newPassword);
            setFormSuccess('Đổi mật khẩu thành công!');
            setPasswordForm({ oldPassword: '', newPassword: '', confirmPassword: '' });
        } catch (err) {
            setFormError(err.response?.data?.message || 'Đổi mật khẩu thất bại. Kiểm tra lại mật khẩu hiện tại.');
        } finally {
            setSaving(false);
        }
    };

    if (loading) return <div className="loading-screen">Loading your identity...</div>;
    if (!profile) return <div className="page-container">User not found</div>;

    const u = profile.userDTO;

    return (
        <div className="page-container">
            <h1 className="page-title">My Profile</h1>
            
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '2rem' }}>
                {/* User Info Card */}
                <div className="glass-panel" style={{ padding: '2rem' }}>
                    <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
                        <div style={{ width: '120px', height: '120px', borderRadius: '50%', background: 'var(--primary)', margin: '0 auto 1.5rem', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '3rem', fontWeight: 800, color: 'white' }}>
                            {u.username?.[0].toUpperCase()}
                        </div>
                        <h2 style={{ marginBottom: '0.5rem' }}>{u.username}</h2>
                        <span style={{ padding: '0.2rem 0.8rem', background: 'rgba(16, 185, 129, 0.2)', color: '#10b981', borderRadius: '20px', fontSize: '0.8rem', fontWeight: 700 }}>VERIFIED</span>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.2rem' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                            <Mail size={18} color="var(--primary)" />
                            <div>
                                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Email</div>
                                <div>{u.email}</div>
                            </div>
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                            <Calendar size={18} color="var(--primary)" />
                            <div>
                                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Member Since</div>
                                <div>Dec 2024</div>
                            </div>
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                            <MapPin size={18} color="var(--primary)" />
                            <div>
                                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Location</div>
                                <div>Viet Nam</div>
                            </div>
                        </div>
                    </div>

                    <button
                        className="btn btn-primary"
                        style={{ width: '100%', marginTop: '2rem', padding: '0.8rem' }}
                        onClick={() => { setFormError(''); setFormSuccess(''); setShowEditModal(true); }}
                    >
                        Edit Profile
                    </button>
                </div>

                {/* Activity Summary */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
                    <div className="glass-panel" style={{ padding: '2rem' }}>
                        <h3>Activity Summary</h3>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '1.5rem', marginTop: '1.5rem' }}>
                            <div style={{ padding: '1.5rem', background: 'rgba(59, 130, 246, 0.05)', borderRadius: '12px', border: '1px solid var(--glass-border)' }}>
                                <Package size={32} color="var(--primary)" style={{ marginBottom: '1rem' }} />
                                <div style={{ fontSize: '2rem', fontWeight: 800 }}>{profile.orderCount || 0}</div>
                                <div style={{ color: 'var(--text-muted)' }}>Total Orders</div>
                            </div>
                            <div style={{ padding: '1.5rem', background: 'rgba(251, 191, 36, 0.05)', borderRadius: '12px', border: '1px solid var(--glass-border)' }}>
                                <Star size={32} color="#fbbf24" style={{ marginBottom: '1rem' }} />
                                <div style={{ fontSize: '2rem', fontWeight: 800 }}>{profile.reviewCount || 0}</div>
                                <div style={{ color: 'var(--text-muted)' }}>Reviews Left</div>
                            </div>
                            <Link to="/points" style={{ padding: '1.5rem', background: 'rgba(251, 191, 36, 0.05)', borderRadius: '12px', border: '1px solid var(--glass-border)', color: 'inherit' }}>
                                <Coins size={32} color="#fbbf24" style={{ marginBottom: '1rem' }} />
                                <div style={{ fontSize: '2rem', fontWeight: 800 }}>{pointBalance.toLocaleString('vi-VN')}</div>
                                <div style={{ color: 'var(--text-muted)' }}>Reward Points</div>
                            </Link>
                        </div>
                    </div>

                    <div className="glass-panel" style={{ padding: '2rem' }}>
                        <h3>Recent Reviews</h3>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1.5rem' }}>
                            {profile.recentReviews?.slice(0, 3).map(rev => (
                                <div key={rev.id} style={{ padding: '1rem', background: 'rgba(255,255,255,0.02)', borderRadius: '8px' }}>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                                        <span style={{ fontWeight: 600 }}>{rev.productName || 'Product'}</span>
                                        <span style={{ color: '#fbbf24' }}>{'★'.repeat(rev.rating)}</span>
                                    </div>
                                    <p style={{ margin: 0, fontSize: '0.9rem', color: 'var(--text-muted)' }}>{rev.comment}</p>
                                </div>
                            ))}
                            {(!profile.recentReviews || profile.recentReviews.length === 0) && (
                                <div style={{ color: 'var(--text-muted)', textAlign: 'center' }}>No reviews yet</div>
                            )}
                        </div>
                    </div>
                </div>
            </div>

            {showEditModal && (
                <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.8)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem' }}>
                    <div className="glass-panel" style={{ width: '100%', maxWidth: '500px', padding: '2rem', maxHeight: '90vh', overflowY: 'auto' }}>
                        <h2>Edit Profile</h2>
                        {formError && <div className="error-message" style={{ marginTop: '1rem' }}>{formError}</div>}
                        {formSuccess && <div className="error-message" style={{ marginTop: '1rem', background: 'rgba(16,185,129,0.15)', color: '#34d399' }}>{formSuccess}</div>}

                        <form onSubmit={handleProfileSave} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1.5rem' }}>
                            <div className="form-group">
                                <label>First Name</label>
                                <input type="text" className="form-input" value={profileForm.firstName} onChange={e => setProfileForm({ ...profileForm, firstName: e.target.value })} />
                            </div>
                            <div className="form-group">
                                <label>Last Name</label>
                                <input type="text" className="form-input" value={profileForm.lastName} onChange={e => setProfileForm({ ...profileForm, lastName: e.target.value })} />
                            </div>
                            <div className="form-group">
                                <label>Email</label>
                                <input type="email" className="form-input" value={profileForm.email} onChange={e => setProfileForm({ ...profileForm, email: e.target.value })} />
                            </div>
                            <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Saving...' : 'Save Changes'}</button>
                        </form>

                        <hr style={{ margin: '2rem 0', borderColor: 'var(--glass-border)' }} />

                        <h2>Change Password</h2>
                        <form onSubmit={handlePasswordSave} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1.5rem' }}>
                            <div className="form-group">
                                <label>Current Password</label>
                                <input type="password" className="form-input" value={passwordForm.oldPassword} onChange={e => setPasswordForm({ ...passwordForm, oldPassword: e.target.value })} required />
                            </div>
                            <div className="form-group">
                                <label>New Password</label>
                                <input type="password" className="form-input" value={passwordForm.newPassword} onChange={e => setPasswordForm({ ...passwordForm, newPassword: e.target.value })} required />
                            </div>
                            <div className="form-group">
                                <label>Confirm New Password</label>
                                <input type="password" className="form-input" value={passwordForm.confirmPassword} onChange={e => setPasswordForm({ ...passwordForm, confirmPassword: e.target.value })} required />
                            </div>
                            <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Saving...' : 'Change Password'}</button>
                        </form>

                        <button type="button" className="btn" onClick={() => setShowEditModal(false)} style={{ width: '100%', marginTop: '1.5rem', background: 'rgba(255,255,255,0.1)' }}>Close</button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Profile;

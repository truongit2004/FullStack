import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Ticket, Plus, Pencil, Power } from 'lucide-react';
import couponService from '../services/CouponService';

const EMPTY_FORM = {
    code: '', description: '', type: 'PERCENT', value: '', maxDiscount: '', minOrderAmount: '',
    startAt: '', endAt: '', usageLimit: '', perUserLimit: '1', active: true,
};

const money = (value) => `$${Number(value).toLocaleString('en-US', { maximumFractionDigits: 2 })}`;
const formatDate = (value) => (value ? new Date(value).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' }) : '—');
// <input type="datetime-local"> cần dạng "YYYY-MM-DDTHH:mm"
const toInputDate = (value) => (value ? String(value).slice(0, 16) : '');

// Trạng thái hiển thị của một mã tại thời điểm hiện tại
const statusOf = (coupon) => {
    const now = new Date();
    if (!coupon.active) return { label: 'Tạm ngừng', color: '#94a3b8', bg: 'rgba(255,255,255,0.08)' };
    if (coupon.endAt && new Date(coupon.endAt) < now) return { label: 'Hết hạn', color: '#f87171', bg: 'rgba(239,68,68,0.15)' };
    if (coupon.usageLimit != null && coupon.usedCount >= coupon.usageLimit) return { label: 'Hết lượt', color: '#f87171', bg: 'rgba(239,68,68,0.15)' };
    if (coupon.startAt && new Date(coupon.startAt) > now) return { label: 'Chưa bắt đầu', color: '#fbbf24', bg: 'rgba(251,191,36,0.15)' };
    return { label: 'Đang chạy', color: '#10b981', bg: 'rgba(16,185,129,0.15)' };
};

const inputStyle = { width: '100%' };

const AdminCoupons = () => {
    const [coupons, setCoupons] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [showModal, setShowModal] = useState(false);
    const [editing, setEditing] = useState(null);     // mã đang sửa, null = tạo mới
    const [form, setForm] = useState(EMPTY_FORM);
    const [formError, setFormError] = useState('');
    const [saving, setSaving] = useState(false);

    const load = async () => {
        setLoading(true);
        setError('');
        try {
            setCoupons(await couponService.getAll() || []);
        } catch (err) {
            setError(err.response?.data?.message || 'Không tải được danh sách mã giảm giá.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { load(); }, []);

    const openCreate = () => {
        setEditing(null);
        setForm(EMPTY_FORM);
        setFormError('');
        setShowModal(true);
    };

    const openEdit = (coupon) => {
        setEditing(coupon);
        setForm({
            code: coupon.code,
            description: coupon.description || '',
            type: coupon.type,
            value: coupon.value ?? '',
            maxDiscount: coupon.maxDiscount ?? '',
            minOrderAmount: coupon.minOrderAmount ?? '',
            startAt: toInputDate(coupon.startAt),
            endAt: toInputDate(coupon.endAt),
            usageLimit: coupon.usageLimit ?? '',
            perUserLimit: coupon.perUserLimit ?? '1',
            active: coupon.active,
        });
        setFormError('');
        setShowModal(true);
    };

    const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

    const handleSave = async (e) => {
        e.preventDefault();
        setFormError('');
        setSaving(true);
        // Ô để trống nghĩa là "không giới hạn" -> gửi null
        const numberOrNull = (v) => (v === '' || v === null ? null : Number(v));
        const payload = {
            code: form.code.trim().toUpperCase(),
            description: form.description.trim() || null,
            type: form.type,
            value: Number(form.value),
            maxDiscount: form.type === 'PERCENT' ? numberOrNull(form.maxDiscount) : null,
            minOrderAmount: numberOrNull(form.minOrderAmount),
            startAt: form.startAt ? `${form.startAt}:00` : null,
            endAt: form.endAt ? `${form.endAt}:00` : null,
            usageLimit: numberOrNull(form.usageLimit),
            perUserLimit: numberOrNull(form.perUserLimit) ?? 1,
            active: form.active,
        };
        try {
            if (editing) await couponService.update(editing.id, payload);
            else await couponService.create(payload);
            setShowModal(false);
            await load();
        } catch (err) {
            setFormError(err.response?.data?.message || 'Lưu mã giảm giá thất bại.');
        } finally {
            setSaving(false);
        }
    };

    const toggleActive = async (coupon) => {
        try {
            await couponService.setActive(coupon.id, !coupon.active);
            await load();
        } catch (err) {
            setError(err.response?.data?.message || 'Không đổi được trạng thái mã.');
        }
    };

    const used = Boolean(editing && editing.usedCount > 0);

    return (
        <div className="page-container">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
                <h1 className="page-title" style={{ marginBottom: '1rem' }}>Mã giảm giá</h1>
                <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                    <Link to="/admin" style={{ color: 'var(--primary)', fontSize: '0.85rem' }}>← Trang quản trị</Link>
                    <button className="btn btn-primary" onClick={openCreate}><Plus size={18} /> Tạo mã</button>
                </div>
            </div>

            {error && <div className="error-message">{error}</div>}

            <div className="glass-panel admin-table-container" style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: '860px' }}>
                    <thead>
                        <tr style={{ textAlign: 'left', color: 'var(--text-muted)', fontSize: '0.8rem' }}>
                            {['Mã', 'Giảm', 'Đơn tối thiểu', 'Thời gian', 'Đã dùng', 'Trạng thái', ''].map(h => (
                                <th key={h} style={{ padding: '1rem', borderBottom: '1px solid var(--glass-border)', fontWeight: 600 }}>{h}</th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {loading && <tr><td colSpan={7} style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>Đang tải...</td></tr>}
                        {!loading && coupons.length === 0 && (
                            <tr><td colSpan={7} style={{ padding: '3rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                                <Ticket size={48} strokeWidth={1} style={{ marginBottom: '0.75rem' }} />
                                <div>Chưa có mã giảm giá nào. Bấm "Tạo mã" để bắt đầu.</div>
                            </td></tr>
                        )}
                        {coupons.map(coupon => {
                            const status = statusOf(coupon);
                            return (
                                <tr key={coupon.id} style={{ borderBottom: '1px solid var(--glass-border)' }}>
                                    <td style={{ padding: '1rem' }}>
                                        <div style={{ fontWeight: 700, fontFamily: 'monospace' }}>{coupon.code}</div>
                                        {coupon.description && <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{coupon.description}</div>}
                                    </td>
                                    <td style={{ padding: '1rem' }}>
                                        {coupon.type === 'PERCENT' ? `${Number(coupon.value)}%` : money(coupon.value)}
                                        {coupon.type === 'PERCENT' && coupon.maxDiscount != null && (
                                            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>tối đa {money(coupon.maxDiscount)}</div>
                                        )}
                                    </td>
                                    <td style={{ padding: '1rem' }}>{coupon.minOrderAmount != null ? money(coupon.minOrderAmount) : '—'}</td>
                                    <td style={{ padding: '1rem', fontSize: '0.85rem' }}>
                                        <div>{formatDate(coupon.startAt)}</div>
                                        <div style={{ color: 'var(--text-muted)' }}>→ {formatDate(coupon.endAt)}</div>
                                    </td>
                                    <td style={{ padding: '1rem' }}>
                                        {coupon.usedCount} / {coupon.usageLimit ?? '∞'}
                                        <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{coupon.perUserLimit} lần/khách</div>
                                    </td>
                                    <td style={{ padding: '1rem' }}>
                                        <span style={{ padding: '0.25rem 0.7rem', borderRadius: '20px', fontSize: '0.75rem', fontWeight: 700, background: status.bg, color: status.color }}>
                                            {status.label}
                                        </span>
                                    </td>
                                    <td style={{ padding: '1rem' }}>
                                        <div style={{ display: 'flex', gap: '0.3rem' }}>
                                            <button className="quantity-btn" title="Sửa" onClick={() => openEdit(coupon)}><Pencil size={16} /></button>
                                            <button className="quantity-btn" title={coupon.active ? 'Tạm ngừng' : 'Bật lại'} onClick={() => toggleActive(coupon)}
                                                style={{ color: coupon.active ? 'var(--danger)' : 'var(--accent)' }}>
                                                <Power size={16} />
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>

            {showModal && (
                <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.8)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem' }}>
                    <div className="glass-panel" style={{ width: '100%', maxWidth: '560px', padding: '2rem', maxHeight: '90vh', overflowY: 'auto' }}>
                        <h2>{editing ? `Sửa mã ${editing.code}` : 'Tạo mã giảm giá'}</h2>
                        {formError && <div className="error-message" style={{ marginTop: '1rem' }}>{formError}</div>}
                        {used && (
                            <div style={{ marginTop: '1rem', fontSize: '0.85rem', color: '#fbbf24' }}>
                                Mã này đã có người dùng nên không đổi được chuỗi mã, kiểu giảm và giá trị giảm. Bạn vẫn sửa được thời hạn, số lượt và đơn tối thiểu.
                            </div>
                        )}

                        <form onSubmit={handleSave} style={{ marginTop: '1.5rem' }}>
                            <div className="form-group">
                                <label>Mã (chữ, số, _ hoặc -; 3-30 ký tự)</label>
                                <input className="form-input" style={inputStyle} value={form.code} disabled={used} required
                                    onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} placeholder="SALE10" />
                            </div>
                            <div className="form-group">
                                <label>Mô tả (khách nhìn thấy)</label>
                                <input className="form-input" style={inputStyle} value={form.description} onChange={set('description')} placeholder="Giảm 10% cho đơn từ $50" />
                            </div>

                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                                <div className="form-group">
                                    <label>Kiểu giảm</label>
                                    <select className="form-input sort-select" style={inputStyle} value={form.type} disabled={used} onChange={set('type')}>
                                        <option value="PERCENT">Theo phần trăm (%)</option>
                                        <option value="FIXED">Số tiền cố định ($)</option>
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label>{form.type === 'PERCENT' ? 'Phần trăm giảm (1-100)' : 'Số tiền giảm ($)'}</label>
                                    <input className="form-input" style={inputStyle} type="number" step="0.01" min="0.01"
                                        max={form.type === 'PERCENT' ? 100 : undefined}
                                        value={form.value} disabled={used} required onChange={set('value')} />
                                </div>
                                {form.type === 'PERCENT' && (
                                    <div className="form-group">
                                        <label>Giảm tối đa ($) — trống = không giới hạn</label>
                                        <input className="form-input" style={inputStyle} type="number" step="0.01" min="0.01" value={form.maxDiscount} onChange={set('maxDiscount')} />
                                    </div>
                                )}
                                <div className="form-group">
                                    <label>Đơn tối thiểu ($) — trống = không yêu cầu</label>
                                    <input className="form-input" style={inputStyle} type="number" step="0.01" min="0" value={form.minOrderAmount} onChange={set('minOrderAmount')} />
                                </div>
                                <div className="form-group">
                                    <label>Bắt đầu — trống = ngay bây giờ</label>
                                    <input className="form-input" style={inputStyle} type="datetime-local" value={form.startAt} onChange={set('startAt')} />
                                </div>
                                <div className="form-group">
                                    <label>Kết thúc — trống = không hết hạn</label>
                                    <input className="form-input" style={inputStyle} type="datetime-local" value={form.endAt} onChange={set('endAt')} />
                                </div>
                                <div className="form-group">
                                    <label>Tổng lượt dùng — trống = không giới hạn</label>
                                    <input className="form-input" style={inputStyle} type="number" step="1" min="1" value={form.usageLimit} onChange={set('usageLimit')} />
                                </div>
                                <div className="form-group">
                                    <label>Số lần mỗi khách</label>
                                    <input className="form-input" style={inputStyle} type="number" step="1" min="1" value={form.perUserLimit} required onChange={set('perUserLimit')} />
                                </div>
                            </div>

                            <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1rem' }}>
                                <button type="button" className="btn" onClick={() => setShowModal(false)} style={{ flex: 1, background: 'rgba(255,255,255,0.1)' }}>Hủy</button>
                                <button type="submit" className="btn btn-primary" disabled={saving} style={{ flex: 1 }}>{saving ? 'Đang lưu...' : 'Lưu'}</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default AdminCoupons;

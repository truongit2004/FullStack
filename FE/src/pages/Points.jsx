import React, { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Coins, Gift, ShoppingBag, RotateCcw, Undo2, Clock, Info } from 'lucide-react';
import pointService from '../services/PointService';
import { useAuth } from '../context/AuthContext';

// "points" trong sổ cái luôn dương, chiều cộng/trừ do "type" quyết định.
const TYPE_META = {
    EARN:           { label: 'Tích điểm từ đơn hàng',       sign: 1,  color: '#10b981', bg: 'rgba(16,185,129,0.15)',  icon: Gift },
    REDEEM:         { label: 'Dùng điểm giảm giá',           sign: -1, color: '#f87171', bg: 'rgba(239,68,68,0.15)',   icon: ShoppingBag },
    REVERSE_EARN:   { label: 'Thu hồi điểm (đơn hủy/trả)',   sign: -1, color: '#fbbf24', bg: 'rgba(251,191,36,0.15)',  icon: RotateCcw },
    REVERSE_REDEEM: { label: 'Hoàn điểm đã dùng (đơn hủy/trả)', sign: 1, color: '#60a5fa', bg: 'rgba(59,130,246,0.15)', icon: Undo2 },
};

const FILTERS = [
    { key: 'ALL',     label: 'Tất cả',          types: null },
    { key: 'EARN',    label: 'Tích điểm',       types: ['EARN'] },
    { key: 'REDEEM',  label: 'Dùng điểm',       types: ['REDEEM'] },
    { key: 'REVERSE', label: 'Hoàn / Thu hồi',  types: ['REVERSE_EARN', 'REVERSE_REDEEM'] },
];

const formatPoints = (value) => Number(value || 0).toLocaleString('vi-VN');

const Points = () => {
    const { user } = useAuth();
    const [searchParams] = useSearchParams();
    const [balance, setBalance] = useState(0);
    const [history, setHistory] = useState([]);
    const [config, setConfig] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [filter, setFilter] = useState('ALL');

    // ADMIN có thể xem điểm của user khác qua /points?userId=...
    const requestedUserId = searchParams.get('userId');
    const isViewingOther = user?.role === 'ADMIN' && requestedUserId && String(requestedUserId) !== String(user.id);
    const targetUserId = isViewingOther ? requestedUserId : user?.id;

    useEffect(() => {
        if (!targetUserId) return;
        const fetchPoints = async () => {
            setLoading(true);
            setError('');
            try {
                const [balanceRes, historyRes, configRes] = await Promise.all([
                    pointService.getBalance(targetUserId),
                    pointService.getHistory(targetUserId),
                    pointService.getConfig(),
                ]);
                setBalance(balanceRes.balance || 0);
                setHistory(historyRes || []);
                setConfig(configRes);
            } catch (err) {
                console.error('Failed to load points', err);
                setError(err.response?.data?.message || 'Không tải được thông tin điểm thưởng. Vui lòng thử lại sau.');
            } finally {
                setLoading(false);
            }
        };
        fetchPoints();
    }, [targetUserId]);

    const totals = useMemo(() => {
        const sum = (type) => history
            .filter(tx => tx.type === type)
            .reduce((acc, tx) => acc + Number(tx.points || 0), 0);
        return {
            earned: sum('EARN') - sum('REVERSE_EARN'),
            redeemed: sum('REDEEM') - sum('REVERSE_REDEEM'),
        };
    }, [history]);

    const visibleHistory = useMemo(() => {
        const types = FILTERS.find(f => f.key === filter)?.types;
        return types ? history.filter(tx => types.includes(tx.type)) : history;
    }, [history, filter]);

    if (loading) return (
        <div className="loading-screen">
            <div className="spinner" />
        </div>
    );

    return (
        <div className="page-container">
            <h1 className="page-title">{isViewingOther ? 'Điểm thưởng của khách hàng' : 'Điểm thưởng của tôi'}</h1>
            {isViewingOther && (
                <div style={{ color: 'var(--text-muted)', marginTop: '-1rem', marginBottom: '1.5rem', fontSize: '0.9rem' }}>
                    User ID: <span style={{ fontFamily: 'monospace' }}>{targetUserId}</span>
                </div>
            )}

            {error && <div className="error-message">{error}</div>}

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1.5rem', marginBottom: '2rem' }}>
                <div className="glass-panel fade-in" style={{ padding: '2rem', background: 'linear-gradient(135deg, rgba(251,191,36,0.18), rgba(59,130,246,0.12))' }}>
                    <Coins size={32} color="#fbbf24" style={{ marginBottom: '1rem' }} />
                    <div style={{ fontSize: '2.5rem', fontWeight: 800 }}>{formatPoints(balance)}</div>
                    <div style={{ color: 'var(--text-muted)' }}>Điểm hiện có</div>
                </div>
                <div className="glass-panel fade-in" style={{ padding: '2rem' }}>
                    <Gift size={32} color="#10b981" style={{ marginBottom: '1rem' }} />
                    <div style={{ fontSize: '2rem', fontWeight: 800 }}>{formatPoints(totals.earned)}</div>
                    <div style={{ color: 'var(--text-muted)' }}>Tổng điểm đã tích</div>
                </div>
                <div className="glass-panel fade-in" style={{ padding: '2rem' }}>
                    <ShoppingBag size={32} color="#f87171" style={{ marginBottom: '1rem' }} />
                    <div style={{ fontSize: '2rem', fontWeight: 800 }}>{formatPoints(totals.redeemed)}</div>
                    <div style={{ color: 'var(--text-muted)' }}>Tổng điểm đã dùng</div>
                </div>
            </div>

            {config && (
                <div className="glass-panel" style={{ padding: '1.5rem', marginBottom: '2rem' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 700, marginBottom: '0.75rem' }}>
                        <Info size={18} color="var(--primary)" /> Quy tắc điểm thưởng
                    </div>
                    <ul style={{ margin: 0, paddingLeft: '1.25rem', color: 'var(--text-muted)', fontSize: '0.9rem', lineHeight: 1.8 }}>
                        <li>Nhận điểm bằng <b>{Math.round(config.earnRate * 100)}%</b> giá trị gốc của đơn hàng (tiền hàng, làm tròn xuống) khi đơn được <b>giao thành công</b>.</li>
                        <li><b>1 điểm = $1</b> giảm giá, dùng khi thanh toán đơn tiếp theo.</li>
                        <li>Mỗi đơn đổi điểm tối đa bằng <b>{Math.round(config.maxRedeemRatio * 100)}%</b> tiền hàng, nên không có đơn 0đ.</li>
                        <li>Điểm không dùng chung với mã giảm giá, và đơn có dùng mã giảm giá <b>không được tích điểm</b>.</li>
                        <li>Đơn bị hủy/trả hàng: điểm đã dùng được hoàn lại, điểm đã tích từ đơn đó bị thu hồi.</li>
                    </ul>
                </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem', marginBottom: '1rem' }}>
                <h2 style={{ margin: 0, fontSize: '1.3rem' }}>Lịch sử giao dịch điểm</h2>
                <div className="category-chips" style={{ marginBottom: 0 }}>
                    {FILTERS.map(f => (
                        <button
                            key={f.key}
                            className={`chip ${filter === f.key ? 'chip-active' : ''}`}
                            onClick={() => setFilter(f.key)}
                        >
                            {f.label}
                        </button>
                    ))}
                </div>
            </div>

            {visibleHistory.length === 0 ? (
                <div className="empty-state fade-in">
                    <Coins size={64} strokeWidth={1} />
                    <h2>{history.length === 0 ? 'Chưa có giao dịch điểm nào' : 'Không có giao dịch phù hợp'}</h2>
                    {history.length === 0 && !isViewingOther && (
                        <>
                            <p>Mua sắm và nhận hàng thành công để bắt đầu tích điểm!</p>
                            <Link to="/products" className="btn btn-primary" style={{ marginTop: '1rem' }}>
                                Xem sản phẩm
                            </Link>
                        </>
                    )}
                </div>
            ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                    {visibleHistory.map(tx => {
                        const meta = TYPE_META[tx.type] || { label: tx.type, sign: 1, color: 'var(--text-muted)', bg: 'rgba(255,255,255,0.08)', icon: Coins };
                        const Icon = meta.icon;
                        return (
                            <div key={tx.id} className="glass-panel fade-in" style={{ display: 'flex', alignItems: 'center', gap: '1rem', padding: '1rem 1.5rem', flexWrap: 'wrap' }}>
                                <div style={{ width: '42px', height: '42px', borderRadius: '50%', background: meta.bg, color: meta.color, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                                    <Icon size={20} />
                                </div>
                                <div style={{ flexGrow: 1, minWidth: '180px' }}>
                                    <div style={{ fontWeight: 600 }}>{meta.label}</div>
                                    <div style={{ color: 'var(--text-muted)', fontSize: '0.8rem', display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
                                        {tx.orderId && <span>Đơn #{tx.orderId.substring(0, 8).toUpperCase()}</span>}
                                        {tx.createdAt && (
                                            <span style={{ display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                                                <Clock size={12} />
                                                {new Date(tx.createdAt).toLocaleString('vi-VN')}
                                            </span>
                                        )}
                                    </div>
                                </div>
                                <div style={{ textAlign: 'right' }}>
                                    <div style={{ fontSize: '1.2rem', fontWeight: 800, color: meta.color }}>
                                        {meta.sign > 0 ? '+' : '-'}{formatPoints(tx.points)}
                                    </div>
                                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                                        Số dư: {formatPoints(tx.balanceAfter)}
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
};

export default Points;

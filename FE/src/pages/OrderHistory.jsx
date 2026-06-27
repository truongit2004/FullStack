import React, { useEffect, useState } from 'react';
import orderService from '../services/OrderService';
import { useAuth } from '../context/AuthContext';
import { Package, Clock, ChevronDown, ChevronUp, Truck, RotateCcw, XCircle } from 'lucide-react';
import { Link } from 'react-router-dom';
import api from '../api/axios';

const STATUS_COLORS = {
    PENDING:   { bg: 'rgba(251,191,36,0.15)',  color: '#fbbf24' },
    CONFIRMED: { bg: 'rgba(59,130,246,0.15)',   color: '#60a5fa' },
    PAID:      { bg: 'rgba(139,92,246,0.15)',   color: '#a78bfa' },
    SHIPPING:  { bg: 'rgba(16,185,129,0.15)',   color: '#34d399' },
    DELIVERED: { bg: 'rgba(16,185,129,0.25)',   color: '#10b981' },
    CANCELLED: { bg: 'rgba(239,68,68,0.15)',    color: '#f87171' },
    REFUNDED:  { bg: 'rgba(255,255,255,0.1)',   color: '#94a3b8' },
    RETURNED:  { bg: 'rgba(255,255,255,0.08)',  color: '#94a3b8' },
};

const STATUS_LABELS = {
    PENDING:   'Chờ xác nhận',
    CONFIRMED: 'Đã xác nhận',
    PAID:      'Đã thanh toán',
    SHIPPING:  'Đang giao hàng',
    DELIVERED: 'Đã giao hàng',
    CANCELLED: 'Đã hủy',
    REFUNDED:  'Đã hoàn tiền',
    RETURNED:  'Đã trả hàng',
};

const OrderHistory = () => {
    const { user } = useAuth();
    const [orders, setOrders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [expandedOrder, setExpandedOrder] = useState(null);
    const [cancelling, setCancelling] = useState(null);

    const fetchOrders = async (p = 0) => {
        if (!user) return;
        setLoading(true);
        try {
            const data = await orderService.getMyOrders(user.id, p, 10);
            setOrders(data.content || []);
            setTotalPages(data.totalPages || 1);
        } catch (err) {
            console.error('Failed to load orders', err);
            setOrders([]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchOrders(page);
    }, [user, page]);

    const handleCancel = async (orderId) => {
        if (!window.confirm('Bạn có chắc muốn hủy đơn hàng này?')) return;
        setCancelling(orderId);
        try {
            await orderService.cancelOrder(orderId);
            fetchOrders(page);
        } catch (err) {
            alert(err.response?.data?.message || 'Hủy đơn hàng thất bại');
        } finally {
            setCancelling(null);
        }
    };

    if (loading) return (
        <div className="loading-screen">
            <div className="spinner" />
        </div>
    );

    return (
        <div className="page-container">
            <h1 className="page-title">Đơn hàng của tôi</h1>

            {orders.length === 0 ? (
                <div className="empty-state fade-in">
                    <Package size={64} strokeWidth={1} />
                    <h2>Chưa có đơn hàng nào</h2>
                    <p>Hãy khám phá và mua sắm ngay!</p>
                    <Link to="/products" className="btn btn-primary" style={{ marginTop: '1rem' }}>
                        Xem sản phẩm
                    </Link>
                </div>
            ) : (
                <>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
                        {orders.map(order => {
                            const statusStyle = STATUS_COLORS[order.status] || STATUS_COLORS.PENDING;
                            const isExpanded = expandedOrder === order.id;
                            const canCancel = order.status === 'PENDING' || order.status === 'CONFIRMED';

                            return (
                                <div key={order.id} className="glass-panel fade-in" style={{ overflow: 'hidden' }}>
                                    {/* Header Row */}
                                    <div
                                        style={{
                                            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                                            padding: '1.25rem 1.5rem', cursor: 'pointer',
                                            borderBottom: isExpanded ? '1px solid var(--glass-border)' : 'none'
                                        }}
                                        onClick={() => setExpandedOrder(isExpanded ? null : order.id)}
                                    >
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
                                            <Package size={20} style={{ color: 'var(--primary)', flexShrink: 0 }} />
                                            <div>
                                                <div style={{ fontWeight: 700, fontSize: '1rem' }}>
                                                    Đơn #{order.id.substring(0, 8).toUpperCase()}
                                                </div>
                                                <div style={{ color: 'var(--text-muted)', fontSize: '0.8rem', display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                                                    <Clock size={12} />
                                                    {new Date(order.createdAt).toLocaleString('vi-VN')}
                                                </div>
                                            </div>
                                            <span style={{
                                                padding: '0.3rem 0.8rem', borderRadius: '20px', fontSize: '0.8rem', fontWeight: 700,
                                                background: statusStyle.bg, color: statusStyle.color
                                            }}>
                                                {STATUS_LABELS[order.status] || order.status}
                                            </span>
                                        </div>

                                        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                                            <div style={{ textAlign: 'right' }}>
                                                <div style={{ fontSize: '1.2rem', fontWeight: 800, color: 'var(--primary)' }}>
                                                    {Number(order.finalAmount || order.totalAmount || 0).toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}
                                                </div>
                                                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                                                    {order.items?.length || 0} sản phẩm
                                                </div>
                                            </div>
                                            {isExpanded ? <ChevronUp size={18} style={{ color: 'var(--text-muted)' }} /> : <ChevronDown size={18} style={{ color: 'var(--text-muted)' }} />}
                                        </div>
                                    </div>

                                    {/* Expanded Content */}
                                    {isExpanded && (
                                        <div style={{ padding: '1.5rem' }}>
                                            {/* Items */}
                                            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: '0.75rem', marginBottom: '1.5rem' }}>
                                                {(order.items || []).map(item => (
                                                    <Link
                                                        to={`/products/${item.productId}`}
                                                        key={item.id || item.productId}
                                                        style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', background: 'rgba(255,255,255,0.03)', padding: '0.75rem', borderRadius: '10px', transition: 'background 0.2s' }}
                                                        onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.07)'}
                                                        onMouseLeave={e => e.currentTarget.style.background = 'rgba(255,255,255,0.03)'}
                                                    >
                                                        <img
                                                            src={item.productImage ? `/api/products/files/${item.productImage}` : `https://placehold.co/50x50/1e293b/60a5fa?text=P`}
                                                            alt={item.productName}
                                                            style={{ width: '50px', height: '50px', borderRadius: '8px', objectFit: 'cover', flexShrink: 0 }}
                                                            onError={(e) => { e.target.src = 'https://placehold.co/50x50/1e293b/60a5fa?text=P'; }}
                                                        />
                                                        <div>
                                                            <div style={{ fontSize: '0.9rem', fontWeight: 600, marginBottom: '0.2rem' }}>{item.productName}</div>
                                                            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                                                                x{item.quantity} · {Number(item.price || 0).toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}
                                                            </div>
                                                        </div>
                                                    </Link>
                                                ))}
                                            </div>

                                            {/* Order Details */}
                                            <div style={{ background: 'rgba(255,255,255,0.03)', borderRadius: '10px', padding: '1rem', marginBottom: '1rem' }}>
                                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem', fontSize: '0.85rem' }}>
                                                    {order.shippingAddress && (
                                                        <>
                                                            <span style={{ color: 'var(--text-muted)' }}>Địa chỉ giao hàng:</span>
                                                            <span>{order.shippingAddress}</span>
                                                        </>
                                                    )}
                                                    {order.shippingFee !== null && order.shippingFee !== undefined && (
                                                        <>
                                                            <span style={{ color: 'var(--text-muted)' }}>Phí vận chuyển:</span>
                                                            <span>{Number(order.shippingFee).toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}</span>
                                                        </>
                                                    )}
                                                    {order.discountAmount > 0 && (
                                                        <>
                                                            <span style={{ color: 'var(--text-muted)' }}>Giảm giá:</span>
                                                            <span style={{ color: 'var(--accent)' }}>-{Number(order.discountAmount).toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}</span>
                                                        </>
                                                    )}
                                                    {order.note && (
                                                        <>
                                                            <span style={{ color: 'var(--text-muted)' }}>Ghi chú:</span>
                                                            <span>{order.note}</span>
                                                        </>
                                                    )}
                                                </div>
                                            </div>

                                            {/* Shipping Info */}
                                            {(order.status === 'SHIPPING' || order.status === 'DELIVERED') && (
                                                <ShippingInfo orderId={order.id} />
                                            )}

                                            {/* Actions */}
                                            <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end', marginTop: '1rem' }}>
                                                {canCancel && (
                                                    <button
                                                        className="btn"
                                                        style={{ background: 'rgba(239,68,68,0.1)', color: 'var(--danger)', border: '1px solid rgba(239,68,68,0.3)' }}
                                                        onClick={() => handleCancel(order.id)}
                                                        disabled={cancelling === order.id}
                                                    >
                                                        <XCircle size={16} />
                                                        {cancelling === order.id ? 'Đang hủy...' : 'Hủy đơn'}
                                                    </button>
                                                )}
                                                {order.status === 'PENDING' && (
                                                    <Link
                                                        to={`/checkout?reorder=${order.id}`}
                                                        className="btn btn-primary"
                                                    >
                                                        Thanh toán ngay
                                                    </Link>
                                                )}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>

                    {/* Pagination */}
                    {totalPages > 1 && (
                        <div style={{ display: 'flex', justifyContent: 'center', gap: '0.75rem', marginTop: '2rem' }}>
                            <button className="btn" disabled={page === 0} onClick={() => setPage(p => p - 1)} style={{ padding: '0.5rem 1rem' }}>
                                ← Trước
                            </button>
                            <span style={{ display: 'flex', alignItems: 'center', color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                                Trang {page + 1} / {totalPages}
                            </span>
                            <button className="btn" disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)} style={{ padding: '0.5rem 1rem' }}>
                                Tiếp →
                            </button>
                        </div>
                    )}
                </>
            )}
        </div>
    );
};

const ShippingInfo = ({ orderId }) => {
    const [shipping, setShipping] = useState(null);

    useEffect(() => {
        const fetchShipping = async () => {
            try {
                const res = await api.get(`/api/shipping/order/${orderId}`);
                setShipping(res.data);
            } catch {
                // Chưa có bản ghi shipping
            }
        };
        fetchShipping();
    }, [orderId]);

    if (!shipping) return null;

    return (
        <div style={{ marginTop: '1rem', marginBottom: '1rem', padding: '1rem', background: 'rgba(16,185,129,0.08)', borderRadius: '10px', border: '1px dashed rgba(16,185,129,0.4)' }}>
            <div style={{ fontSize: '0.85rem', fontWeight: 700, color: 'var(--accent)', marginBottom: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Truck size={16} /> Thông tin vận chuyển
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem', fontSize: '0.85rem' }}>
                <span style={{ color: 'var(--text-muted)' }}>Đơn vị vận chuyển:</span>
                <span>{shipping.carrier}</span>
                <span style={{ color: 'var(--text-muted)' }}>Mã vận đơn:</span>
                <span style={{ fontFamily: 'monospace', color: 'var(--primary)' }}>{shipping.trackingNumber}</span>
                <span style={{ color: 'var(--text-muted)' }}>Trạng thái:</span>
                <span style={{ color: '#fbbf24', fontWeight: 600 }}>{shipping.status}</span>
                {shipping.estimatedDeliveryDate && (
                    <>
                        <span style={{ color: 'var(--text-muted)' }}>Dự kiến giao:</span>
                        <span>{new Date(shipping.estimatedDeliveryDate).toLocaleDateString('vi-VN')}</span>
                    </>
                )}
            </div>
        </div>
    );
};

export default OrderHistory;

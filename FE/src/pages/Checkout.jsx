import React, { useEffect, useState } from 'react';
import cartService from '../services/CartService';
import orderService from '../services/OrderService';
import paymentService from '../services/PaymentService';
import shippingService from '../services/ShippingService';
import pointService from '../services/PointService';
import couponService from '../services/CouponService';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import { Truck, Coins, Ticket, X } from 'lucide-react';

const ZONE_LABELS = {
    SAME_CITY: 'Nội thành TP.HCM',
    NEARBY_PROVINCE: 'Tỉnh lân cận',
    FAR_PROVINCE: 'Tỉnh/thành khác',
    UNKNOWN: 'Chưa xác định (nhập địa chỉ để tính phí chính xác)'
};

const Checkout = () => {
    const navigate = useNavigate();
    const { user } = useAuth();
    const [loading, setLoading] = useState(false);
    const [feeLoading, setFeeLoading] = useState(false);
    const [shippingZone, setShippingZone] = useState(null);
    const [formData, setFormData] = useState({
        shippingAddress: '',
        note: '',
        shippingFee: 8.00,
        discountAmount: 0
    });
    const [subtotal, setSubtotal] = useState(0);
    const [pointBalance, setPointBalance] = useState(null);
    const [pointConfig, setPointConfig] = useState(null);
    const [pointsInput, setPointsInput] = useState('');
    const [couponInput, setCouponInput] = useState('');
    const [coupon, setCoupon] = useState(null);           // mã đã áp dụng: { code, discountAmount, description }
    const [couponError, setCouponError] = useState('');
    const [couponLoading, setCouponLoading] = useState(false);
    const [availableCoupons, setAvailableCoupons] = useState([]);

    useEffect(() => {
        if (!user) return;
        cartService.getCart()
            .then(cart => setSubtotal(Number(cart?.totalAmount || 0)))
            .catch(err => console.error('Failed to load cart', err));
        // Cần cả số dư lẫn quy tắc điểm mới hiện phần dùng điểm
        Promise.all([pointService.getBalance(user.id), pointService.getConfig()])
            .then(([balanceRes, configRes]) => {
                setPointConfig({
                    earnRate: Number(configRes.earnRate),
                    maxRedeemRatio: Number(configRes.maxRedeemRatio)
                });
                setPointBalance(Number(balanceRes.balance || 0));
            })
            .catch(err => console.error('Failed to load reward points', err));
        couponService.getAvailable()
            .then(list => setAvailableCoupons(list || []))
            .catch(err => console.error('Failed to load coupons', err));
    }, [user]);

    // 1 điểm = $1; mỗi đơn chỉ được dùng điểm tối đa maxRedeemRatio x tiền hàng (tránh đơn 0đ)
    const maxRedeemPercent = pointConfig ? Math.round(pointConfig.maxRedeemRatio * 100) : 0;
    const earnPercent = pointConfig ? Math.round(pointConfig.earnRate * 100) : 0;
    const maxRedeemable = pointConfig
        ? Math.max(0, Math.floor(Math.min(pointBalance || 0, subtotal * pointConfig.maxRedeemRatio)))
        : 0;
    // Mã giảm giá và điểm thưởng KHÔNG dùng chung: đã áp mã thì bỏ qua điểm, và ngược lại
    const couponDiscount = coupon ? Number(coupon.discountAmount || 0) : 0;
    const pointsToRedeem = coupon ? 0 : Math.max(0, Math.floor(Number(pointsInput) || 0));
    const pointsError = pointsToRedeem > maxRedeemable
        ? `Bạn chỉ có thể dùng tối đa ${maxRedeemable} điểm cho đơn này.`
        : '';
    const estimatedTotal = subtotal + Number(formData.shippingFee || 0) - couponDiscount - (pointsError ? 0 : pointsToRedeem);
    // Điểm tích tính trên giá trị gốc (tiền hàng). Đơn có dùng mã giảm giá thì không được tích điểm.
    const estimatedEarn = pointConfig && !coupon ? Math.floor(subtotal * pointConfig.earnRate) : 0;

    const applyCoupon = async (code) => {
        const value = (code ?? couponInput).trim();
        if (!value) return;
        setCouponLoading(true);
        setCouponError('');
        try {
            // Chỉ xem trước: lượt dùng mã chỉ bị trừ khi đặt hàng thành công
            const result = await couponService.preview(value, subtotal);
            setCoupon(result);
            setCouponInput(result.code);
            setPointsInput('');
        } catch (err) {
            setCoupon(null);
            setCouponError(err.response?.data?.message || 'Không kiểm tra được mã giảm giá. Vui lòng thử lại.');
        } finally {
            setCouponLoading(false);
        }
    };

    const removeCoupon = () => {
        setCoupon(null);
        setCouponInput('');
        setCouponError('');
    };

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    // Phí ship chỉ là giá trị xem trước; Order Service sẽ tính lại phí chính
    // thức dựa trên địa chỉ khi tạo đơn (xem ShippingFeeCalculator).
    const handleAddressBlur = async () => {
        if (!formData.shippingAddress || !formData.shippingAddress.trim()) return;
        setFeeLoading(true);
        try {
            const res = await shippingService.getFee(formData.shippingAddress);
            setFormData(prev => ({ ...prev, shippingFee: res.fee }));
            setShippingZone(res.zone);
        } catch (err) {
            console.error('Failed to calculate shipping fee, using default', err);
        } finally {
            setFeeLoading(false);
        }
    };

    const handleCheckout = async (e) => {
        e.preventDefault();
        if (pointsError) return;
        setLoading(true);
        try {
            // Bước 1: Chốt Cart thành Order (kèm số điểm muốn dùng, nếu có)
            const res = await cartService.checkout({
                ...formData,
                pointsToRedeem: pointsToRedeem > 0 ? pointsToRedeem : null,
                couponCode: coupon ? coupon.code : null
            });
            const orderId = res.orderId;

            alert(`🎉 Order Created: ${orderId}. Redirecting to secure VNPay Gateway...`);

            // Bước 2: Lấy thông tin đơn hàng vừa tạo để biết tổng tiền (USD)
            const orderRes = await orderService.getOrderById(orderId);
            const finalAmount = orderRes.finalAmount || 20; 
            
            // Đổi hối đoái USD sang VND để truyền vào VNPay (giả lập tỉ giá 25k)
            const amountInVND = Math.floor(finalAmount * 25000);

            // Bước 3: Lấy Link VNPay và Chuyển hướng
            const payRes = await paymentService.createVNPayUrl(amountInVND, orderId);
            if (payRes && payRes.url) {
                window.location.href = payRes.url;
            } else {
                navigate('/orders');
            }

        } catch (err) {
            alert(err.response?.data?.message || 'Checkout failed');
            setLoading(false);
        }
    };


    return (
        <div className="page-container" style={{ maxWidth: '800px' }}>
            <h1 className="page-title">Checkout</h1>
            <div className="glass-panel" style={{ padding: '2.5rem' }}>
                <form onSubmit={handleCheckout} style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
                    
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                        <label style={{ fontWeight: 600 }}>Shipping Address*</label>
                        <input
                            type="text"
                            name="shippingAddress"
                            required
                            placeholder="Số nhà, đường, quận/huyện, tỉnh/thành phố"
                            onChange={handleChange}
                            onBlur={handleAddressBlur}
                            style={{ padding: '1rem', borderRadius: '8px', border: '1px solid var(--glass-border)', background: 'rgba(255,255,255,0.05)', color: 'white', fontSize: '1rem' }}
                        />
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                        <label style={{ fontWeight: 600 }}>Order Note (Optional)</label>
                        <textarea 
                            name="note" 
                            placeholder="Leave at the front door..."
                            onChange={handleChange}
                            style={{ padding: '1rem', borderRadius: '8px', border: '1px solid var(--glass-border)', background: 'rgba(255,255,255,0.05)', color: 'white', fontSize: '1rem', minHeight: '100px', resize: 'vertical' }}
                        />
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '1.5rem', background: 'rgba(59, 130, 246, 0.1)', borderRadius: '8px', marginTop: '1rem' }}>
                        <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                            <Truck size={20}/>
                            <span>
                                Standard Shipping
                                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 400 }}>
                                    {shippingZone ? ZONE_LABELS[shippingZone] : 'Nhập địa chỉ để tính phí ship'}
                                </div>
                            </span>
                        </span>
                        <span style={{ fontWeight: 700 }}>
                            {feeLoading ? '...' : `$${Number(formData.shippingFee).toFixed(2)}`}
                        </span>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', padding: '1.5rem', background: 'rgba(16, 185, 129, 0.08)', borderRadius: '8px', border: '1px solid rgba(16, 185, 129, 0.25)' }}>
                        <label style={{ fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                            <Ticket size={20} color="var(--accent)" /> Mã giảm giá
                        </label>
                        {coupon ? (
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '0.75rem', padding: '0.75rem 1rem', borderRadius: '8px', background: 'rgba(16, 185, 129, 0.15)' }}>
                                <div>
                                    <div style={{ fontWeight: 700, color: 'var(--accent)' }}>{coupon.code} · -${couponDiscount.toFixed(2)}</div>
                                    {coupon.description && <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{coupon.description}</div>}
                                </div>
                                <button type="button" className="quantity-btn" onClick={removeCoupon} title="Bỏ mã">
                                    <X size={18} />
                                </button>
                            </div>
                        ) : (
                            <>
                                <div style={{ display: 'flex', gap: '0.75rem' }}>
                                    <input
                                        type="text"
                                        value={couponInput}
                                        maxLength={30}
                                        disabled={pointsToRedeem > 0}
                                        placeholder={pointsToRedeem > 0 ? 'Bỏ điểm thưởng để dùng mã' : 'Nhập mã giảm giá'}
                                        onChange={(e) => { setCouponInput(e.target.value.toUpperCase()); setCouponError(''); }}
                                        onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); applyCoupon(); } }}
                                        style={{ flexGrow: 1, padding: '1rem', borderRadius: '8px', border: '1px solid var(--glass-border)', background: 'rgba(255,255,255,0.05)', color: 'white', fontSize: '1rem' }}
                                    />
                                    <button type="button" className="btn" disabled={couponLoading || !couponInput.trim() || pointsToRedeem > 0} onClick={() => applyCoupon()} style={{ background: 'rgba(16, 185, 129, 0.15)', color: 'var(--accent)', whiteSpace: 'nowrap' }}>
                                        {couponLoading ? '...' : 'Áp dụng'}
                                    </button>
                                </div>
                                {couponError && <div style={{ fontSize: '0.8rem', color: 'var(--danger)' }}>{couponError}</div>}
                                {availableCoupons.length > 0 && pointsToRedeem === 0 && (
                                    <div className="category-chips" style={{ marginBottom: 0 }}>
                                        {availableCoupons.slice(0, 6).map(c => (
                                            <button type="button" key={c.id} className="chip" onClick={() => applyCoupon(c.code)}
                                                title={c.description || ''}>
                                                {c.code} · {c.type === 'PERCENT' ? `-${Number(c.value)}%` : `-$${Number(c.value)}`}
                                            </button>
                                        ))}
                                    </div>
                                )}
                            </>
                        )}
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                            Mã giảm giá không dùng chung với điểm thưởng. Đơn có dùng mã không được tích điểm.
                        </div>
                    </div>

                    {pointBalance !== null && (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', padding: '1.5rem', background: 'rgba(251, 191, 36, 0.08)', borderRadius: '8px', border: '1px solid rgba(251, 191, 36, 0.25)' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
                                <label style={{ fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                                    <Coins size={20} color="#fbbf24" /> Reward Points
                                </label>
                                <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                                    Hiện có: <b style={{ color: '#fbbf24' }}>{pointBalance.toLocaleString('vi-VN')}</b> điểm
                                </span>
                            </div>
                            {coupon ? (
                                <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                                    Đang dùng mã giảm giá {coupon.code}. Bỏ mã nếu bạn muốn dùng điểm thưởng thay thế.
                                </div>
                            ) : maxRedeemable > 0 ? (
                                <>
                                    <div style={{ display: 'flex', gap: '0.75rem' }}>
                                        <input
                                            type="number"
                                            min="0"
                                            max={maxRedeemable}
                                            step="1"
                                            value={pointsInput}
                                            placeholder="Số điểm muốn dùng"
                                            onChange={(e) => setPointsInput(e.target.value)}
                                            style={{ flexGrow: 1, padding: '1rem', borderRadius: '8px', border: '1px solid var(--glass-border)', background: 'rgba(255,255,255,0.05)', color: 'white', fontSize: '1rem' }}
                                        />
                                        <button type="button" className="btn" onClick={() => setPointsInput(String(maxRedeemable))} style={{ background: 'rgba(251, 191, 36, 0.15)', color: '#fbbf24', whiteSpace: 'nowrap' }}>
                                            Dùng tối đa
                                        </button>
                                    </div>
                                    <div style={{ fontSize: '0.75rem', color: pointsError ? 'var(--danger)' : 'var(--text-muted)' }}>
                                        {pointsError || `1 điểm = $1. Tối đa ${maxRedeemable} điểm cho đơn này (${maxRedeemPercent}% tiền hàng).`}
                                    </div>
                                </>
                            ) : (
                                <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                                    Bạn chưa có điểm để dùng cho đơn này. Điểm được cộng ({earnPercent}% tiền hàng) sau khi đơn giao thành công.
                                </div>
                            )}
                        </div>
                    )}

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem', padding: '1.5rem', background: 'rgba(255,255,255,0.03)', borderRadius: '8px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--text-muted)' }}>
                            <span>Subtotal</span>
                            <span>${subtotal.toFixed(2)}</span>
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--text-muted)' }}>
                            <span>Shipping</span>
                            <span>${Number(formData.shippingFee).toFixed(2)}</span>
                        </div>
                        {coupon && (
                            <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--accent)' }}>
                                <span>Mã giảm giá ({coupon.code})</span>
                                <span>-${couponDiscount.toFixed(2)}</span>
                            </div>
                        )}
                        {pointsToRedeem > 0 && !pointsError && (
                            <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--accent)' }}>
                                <span>Points discount ({pointsToRedeem} điểm)</span>
                                <span>-${pointsToRedeem.toFixed(2)}</span>
                            </div>
                        )}
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '1.3rem', fontWeight: 700, borderTop: '1px solid var(--glass-border)', paddingTop: '0.75rem' }}>
                            <span>Total</span>
                            <span style={{ color: 'var(--primary)' }}>${estimatedTotal.toFixed(2)}</span>
                        </div>
                        {estimatedEarn > 0 && (
                            <div style={{ fontSize: '0.8rem', color: '#fbbf24' }}>
                                Bạn sẽ nhận khoảng +{estimatedEarn} điểm khi đơn hàng được giao thành công.
                            </div>
                        )}
                    </div>

                    <button type="submit" disabled={loading || !!pointsError} className="btn btn-accent" style={{ padding: '1rem', fontSize: '1.2rem', marginTop: '1rem' }}>
                        {loading ? 'Processing...' : 'Place Order & Pay with VNPay'}
                    </button>
                </form>
            </div>
        </div>
    );
};

export default Checkout;

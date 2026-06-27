import React, { useState } from 'react';
import cartService from '../services/CartService';
import orderService from '../services/OrderService';
import paymentService from '../services/PaymentService';
import { useNavigate } from 'react-router-dom';
import { Truck } from 'lucide-react';

const Checkout = () => {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [formData, setFormData] = useState({
        shippingAddress: '',
        note: '',
        shippingFee: 15.00,
        discountAmount: 0
    });

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleCheckout = async (e) => {
        e.preventDefault();
        setLoading(true);
        try {
            // Bước 1: Chốt Cart thành Order
            const res = await cartService.checkout(formData);
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
                            placeholder="123 Silicon Valley, CA"
                            onChange={handleChange}
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

                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '1.5rem', background: 'rgba(59, 130, 246, 0.1)', borderRadius: '8px', marginTop: '1rem' }}>
                        <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}><Truck size={20}/> Standard Shipping</span>
                        <span style={{ fontWeight: 700 }}>$15.00</span>
                    </div>

                    <button type="submit" disabled={loading} className="btn btn-accent" style={{ padding: '1rem', fontSize: '1.2rem', marginTop: '1rem' }}>
                        {loading ? 'Processing...' : 'Place Order & Pay with VNPay'}
                    </button>
                </form>
            </div>
        </div>
    );
};

export default Checkout;

import axios from '../api/axios';

// Dùng mã / hoàn mã là API nội bộ: Order Service tự gọi khi tạo, hủy, trả đơn — FE không gọi trực tiếp.
class CouponService {
    // ── Khách hàng ───────────────────────────────────────────────────────────

    // Kiểm tra mã và xem được giảm bao nhiêu. Chưa trừ lượt dùng.
    async preview(code, orderAmount) {
        const res = await axios.get('/api/coupons/preview', { params: { code, orderAmount } });
        return res.data;
    }

    // Các mã đang dùng được (đang chạy, còn hạn, còn lượt)
    async getAvailable() {
        const res = await axios.get('/api/coupons/available');
        return res.data;
    }

    // ── Quản trị ─────────────────────────────────────────────────────────────

    async getAll() {
        const res = await axios.get('/api/coupons');
        return res.data;
    }

    async create(coupon) {
        const res = await axios.post('/api/coupons', coupon);
        return res.data;
    }

    async update(id, coupon) {
        const res = await axios.put(`/api/coupons/${id}`, coupon);
        return res.data;
    }

    async setActive(id, active) {
        const res = await axios.put(`/api/coupons/${id}/active`, null, { params: { value: active } });
        return res.data;
    }
}

export default new CouponService();

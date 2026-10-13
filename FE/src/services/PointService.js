import axios from '../api/axios';

// Chỉ expose 2 API tra cứu. Các API earn/redeem/reverse của Point Service là
// API nội bộ: cộng điểm đi qua Kafka khi đơn DELIVERED, dùng/hoàn điểm do
// Order Service gọi khi tạo/hủy/trả đơn — FE không gọi trực tiếp.
class PointService {
    // Quy tắc điểm đang áp dụng ({ earnRate, maxRedeemRatio }) — lấy từ backend,
    // FE không tự hard-code tỉ lệ.
    async getConfig() {
        const res = await axios.get('/api/points/config');
        return res.data;
    }

    async getBalance(userId) {
        const res = await axios.get(`/api/points/${userId}`);
        return res.data;
    }

    async getHistory(userId) {
        const res = await axios.get(`/api/points/${userId}/history`);
        return res.data;
    }
}

export default new PointService();

import axios from '../api/axios';

class PaymentService {
    async createVNPayUrl(amount, orderId) {
        const res = await axios.get(`/api/payment/create-vnpay-url?amount=${amount}&orderId=${orderId}`);
        return res.data;
    }

    async getPaymentHistory() {
        // Placeholder nếu cần
        const res = await axios.get('/api/payment/history');
        return res.data;
    }
}

export default new PaymentService();

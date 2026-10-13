import axios from '../api/axios';

class ShippingService {
    async getFee(address) {
        const res = await axios.get(`/api/shipping/fee?address=${encodeURIComponent(address)}`);
        return res.data;
    }

    async getByOrderId(orderId) {
        const res = await axios.get(`/api/shipping/order/${orderId}`);
        return res.data;
    }
}

export default new ShippingService();

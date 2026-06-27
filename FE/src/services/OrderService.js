import axios from '../api/axios';

class OrderService {
    async createOrder(orderData) {
        const res = await axios.post('/api/orders', orderData);
        return res.data;
    }

    async getMyOrders(userId, page = 0, size = 10) {
        const res = await axios.get(`/api/orders/my-orders/${userId}?page=${page}&size=${size}`);
        return res.data;
    }

    async getOrdersByUser(userId, page = 0, size = 10) {
        const res = await axios.get(`/api/orders/user/${userId}?page=${page}&size=${size}`);
        return res.data;
    }

    async getOrderById(id) {
        const res = await axios.get(`/api/orders/${id}`);
        return res.data;
    }

    async getAllOrders(page = 0, size = 10) {
        const res = await axios.get(`/api/orders?page=${page}&size=${size}`);
        return res.data;
    }

    async filterOrders({ userId, status, fromDate, toDate, minTotal, maxTotal } = {}, page = 0, size = 10) {
        const params = new URLSearchParams({ page, size });
        if (userId)   params.append('userId', userId);
        if (status)   params.append('status', status);
        if (fromDate) params.append('fromDate', fromDate);
        if (toDate)   params.append('toDate', toDate);
        if (minTotal) params.append('minTotal', minTotal);
        if (maxTotal) params.append('maxTotal', maxTotal);
        const res = await axios.get(`/api/orders/filter?${params.toString()}`);
        return res.data;
    }

    async updateOrderStatus(orderId, action) {
        const res = await axios.put(`/api/orders/${orderId}/${action}`);
        return res.data;
    }

    async confirmOrder(id) { return this.updateOrderStatus(id, 'confirm'); }
    async shipOrder(id)    { return this.updateOrderStatus(id, 'ship'); }
    async deliverOrder(id) { return this.updateOrderStatus(id, 'deliver'); }
    async cancelOrder(id)  { return this.updateOrderStatus(id, 'cancel'); }
    async refundOrder(id)  { return this.updateOrderStatus(id, 'refund'); }
    async returnOrder(id)  { return this.updateOrderStatus(id, 'return'); }

    async deleteOrder(id) {
        const res = await axios.delete(`/api/orders/${id}`);
        return res.data;
    }

    async getOrderStats() {
        const res = await axios.get('/api/orders/stats');
        return res.data;
    }

    async getRevenue(fromDate, toDate) {
        const params = new URLSearchParams();
        if (fromDate) params.append('fromDate', fromDate);
        if (toDate)   params.append('toDate', toDate);
        const res = await axios.get(`/api/orders/stats/revenue?${params.toString()}`);
        return res.data;
    }

    async getBestSelling(top = 5) {
        const res = await axios.get(`/api/orders/stats/best-selling?top=${top}`);
        return res.data;
    }

    async checkPurchased(userId, productId) {
        const res = await axios.get(`/api/orders/check-purchased?userId=${userId}&productId=${productId}`);
        return res.data;
    }
}

export default new OrderService();

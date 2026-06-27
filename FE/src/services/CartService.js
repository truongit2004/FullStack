import axios from '../api/axios';

class CartService {
    async getCart() {
        const res = await axios.get('/api/cart');
        return res.data;
    }

    async addItem(productId, quantity = 1) {
        const res = await axios.post('/api/cart/items', { productId, quantity });
        return res.data;
    }

    async updateQuantity(productId, quantity) {
        const res = await axios.put(`/api/cart/items/${productId}`, { quantity });
        return res.data;
    }

    async removeItem(productId) {
        const res = await axios.delete(`/api/cart/items/${productId}`);
        return res.data;
    }

    async removeItems(productIds) {
        const params = productIds.map(id => `productIds=${id}`).join('&');
        const res = await axios.delete(`/api/cart/items?${params}`);
        return res.data;
    }

    async clearCart() {
        const res = await axios.delete('/api/cart');
        return res.data;
    }

    async checkout(checkoutData) {
        const res = await axios.post('/api/cart/checkout', checkoutData);
        return res.data;
    }
}

export default new CartService();

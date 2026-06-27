import axios from '../api/axios';

class ReviewService {
    async getReviewsByProduct(productId) {
        const res = await axios.get(`/api/reviews/product/${productId}`);
        return res.data;
    }

    async createReview(reviewData) {
        const res = await axios.post('/api/reviews', reviewData);
        return res.data;
    }

    async deleteReview(id) {
        const res = await axios.delete(`/api/reviews/${id}`);
        return res.data;
    }

    async getReviewsByUser(userId) {
        const res = await axios.get(`/api/reviews/user/${userId}`);
        return res.data;
    }
}

export default new ReviewService();

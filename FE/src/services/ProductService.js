import axios from '../api/axios';

class ProductService {
    async getProducts(page = 0, size = 50) {
        const res = await axios.get(`/api/products/filter?page=${page}&size=${size}`);
        return res.data;
    }

    async filterProducts({ name, categoryId, minPrice, maxPrice, status } = {}, page = 0, size = 50) {
        const params = new URLSearchParams({ page, size });
        if (name)       params.append('name', name);
        if (categoryId) params.append('categoryId', categoryId);
        if (minPrice)   params.append('minPrice', minPrice);
        if (maxPrice)   params.append('maxPrice', maxPrice);
        if (status)     params.append('status', status);
        const res = await axios.get(`/api/products/filter?${params.toString()}`);
        return res.data;
    }

    async getProductById(id) {
        const res = await axios.get(`/api/products/${id}`);
        return res.data;
    }

    async searchProducts(query) {
        // Tìm qua filter endpoint
        const res = await axios.get(`/api/products/filter?name=${encodeURIComponent(query)}&page=0&size=50`);
        return res.data.content || [];
    }

    async createProduct(formData) {
        const res = await axios.post('/api/products', formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
        });
        return res.data;
    }

    async updateProduct(id, formData) {
        const res = await axios.put(`/api/products/${id}`, formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
        });
        return res.data;
    }

    async softDelete(id) {
        const res = await axios.delete(`/api/products/soft/${id}`);
        return res.data;
    }

    async hardDelete(id) {
        const res = await axios.delete(`/api/products/hard/${id}`);
        return res.data;
    }

    // Alias
    async deleteProduct(id) {
        return this.softDelete(id);
    }

    async getCategories() {
        const res = await axios.get('/api/categories');
        return res.data;
    }

    async createCategory(data) {
        const res = await axios.post('/api/categories', data);
        return res.data;
    }

    async updateCategory(id, data) {
        const res = await axios.put(`/api/categories/${id}`, data);
        return res.data;
    }

    async deleteCategory(id) {
        const res = await axios.delete(`/api/categories/${id}`);
        return res.data;
    }

    async getAllIncludeDeleted() {
        const res = await axios.get('/api/products/all');
        return res.data;
    }

    async getDeletedProducts() {
        const res = await axios.get('/api/products/deleted');
        return res.data;
    }

    async increaseStock(id, quantity) {
        const res = await axios.put(`/api/products/${id}/stock/increase?quantity=${quantity}`);
        return res.data;
    }

    async decreaseStock(id, quantity) {
        const res = await axios.put(`/api/products/${id}/stock/decrease?quantity=${quantity}`);
        return res.data;
    }
}

export default new ProductService();

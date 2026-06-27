import axios from '../api/axios';

class UserService {
    async getUserById(id) {
        const res = await axios.get(`/api/users/${id}`);
        return res.data;
    }

    async getFullProfile(id) {
        const res = await axios.get(`/api/users/${id}/profile-full`);
        return res.data;
    }

    async getAllUsers() {
        const res = await axios.get('/api/users');
        return res.data;
    }

    async deleteUser(id) {
        const res = await axios.delete(`/api/users/${id}`);
        return res.data;
    }
}

export default new UserService();

import axios from '../api/axios';

class ChatService {
    async getChatHistory(senderId, recipientId) {
        const res = await axios.get(`/api/chat/messages/${senderId}/${recipientId}`);
        return res.data;
    }

    async getMessages(userId) {
        const res = await axios.get(`/api/chat/messages/${userId}`);
        return res.data;
    }
}

export default new ChatService();

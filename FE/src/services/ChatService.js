import axios from '../api/axios';
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

// Id đại diện cho bộ phận hỗ trợ — phải khớp ChatService.SUPPORT_ID ở backend
export const SUPPORT_ID = 'admin-support';

const USER_QUEUE = '/user/queue/messages';   // tin gửi riêng cho người đang đăng nhập
const SUPPORT_TOPIC = '/topic/support';      // mọi tin của mọi hội thoại, chỉ ADMIN nghe được
const RECONNECT_DELAYS = [2000, 5000, 10000, 30000];

class ChatService {
    async getChatHistory(customerId) {
        const res = await axios.get(`/api/chat/messages/${customerId}/${SUPPORT_ID}`);
        return res.data;
    }

    async getConversations() {
        const res = await axios.get('/api/chat/conversations');
        return res.data;
    }

    async markRead(customerId) {
        await axios.put(`/api/chat/conversations/${customerId}/read`);
    }

    async getUnread() {
        const res = await axios.get('/api/chat/unread');
        return res.data.unread || 0;
    }

    /**
     * Mở kết nối STOMP (qua SockJS) tới Chat Service, tự kết nối lại khi rớt mạng.
     * JWT được gửi trong frame CONNECT vì trình duyệt không gắn được header khi bắt tay WebSocket.
     *
     * @param token      JWT của người đang đăng nhập
     * @param asSupport  true = ADMIN nghe kênh hỗ trợ chung; false = khách nghe hàng đợi riêng
     * @param onMessage  gọi với mỗi tin nhắn nhận được
     * @param onStatus   gọi với true/false khi kết nối lên/xuống
     * @returns {{ send: (content: string, recipientId?: string) => boolean, disconnect: () => void }}
     */
    connect({ token, asSupport = false, onMessage, onStatus }) {
        let client = null;
        let closed = false;
        let attempt = 0;
        let retryTimer = null;

        const open = () => {
            if (closed) return;
            client = Stomp.over(new SockJS(`${import.meta.env.VITE_API_URL}/ws`));
            client.debug = null;

            client.connect({ Authorization: `Bearer ${token}` }, () => {
                attempt = 0;
                onStatus?.(true);
                client.subscribe(asSupport ? SUPPORT_TOPIC : USER_QUEUE, (frame) => {
                    onMessage?.(JSON.parse(frame.body));
                });
            }, () => {
                // Được gọi cả khi server từ chối (token sai) lẫn khi mất kết nối
                onStatus?.(false);
                if (closed) return;
                const delay = RECONNECT_DELAYS[Math.min(attempt, RECONNECT_DELAYS.length - 1)];
                attempt += 1;
                retryTimer = setTimeout(open, delay);
            });
        };

        open();

        return {
            send: (content, recipientId) => {
                if (!client || !client.connected) return false;
                client.send('/app/chat.sendMessage', {}, JSON.stringify({ content, recipientId }));
                return true;
            },
            disconnect: () => {
                closed = true;
                clearTimeout(retryTimer);
                try {
                    if (client && client.connected) client.disconnect();
                    else client?.ws?.close();
                } catch (e) {
                    // kết nối đã đóng sẵn
                }
                onStatus?.(false);
            }
        };
    }
}

export default new ChatService();

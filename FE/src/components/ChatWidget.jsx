import React, { useState, useEffect, useRef } from 'react';
import { MessageCircle, Send, X, User } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import chatService, { SUPPORT_ID } from '../services/ChatService';

// Khung chat của KHÁCH HÀNG với bộ phận hỗ trợ. ADMIN trả lời ở trang /admin/chat.
const ChatWidget = () => {
    const { user, token } = useAuth();
    const [isOpen, setIsOpen] = useState(false);
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [connected, setConnected] = useState(false);
    const [unread, setUnread] = useState(0);
    const connection = useRef(null);
    const scrollRef = useRef(null);
    const isOpenRef = useRef(false);

    const isCustomer = user && user.role !== 'ADMIN';
    const userId = user ? String(user.id) : null;

    const appendMessage = (msg) => {
        setMessages(prev => (prev.some(m => m.id === msg.id) ? prev : [...prev, msg]));
    };

    // Giữ kết nối suốt lúc đăng nhập để huy hiệu "tin mới" cập nhật ngay cả khi đang đóng khung chat
    useEffect(() => {
        if (!isCustomer || !token) return;

        chatService.getUnread().then(setUnread).catch(() => {});

        connection.current = chatService.connect({
            token,
            onStatus: setConnected,
            onMessage: (msg) => {
                appendMessage(msg);
                if (msg.senderId !== SUPPORT_ID) return;
                if (isOpenRef.current) {
                    chatService.markRead(userId).catch(() => {});
                } else {
                    setUnread(n => n + 1);
                }
            }
        });

        return () => {
            connection.current?.disconnect();
            connection.current = null;
            setMessages([]);
            setUnread(0);
        };
    }, [isCustomer, token, userId]);

    // Mở khung chat: tải lịch sử và đánh dấu đã đọc
    useEffect(() => {
        isOpenRef.current = isOpen;
        if (!isOpen || !isCustomer) return;

        chatService.getChatHistory(userId)
            .then(history => setMessages(history || []))
            .catch(err => console.warn('History fetch failed', err));
        chatService.markRead(userId).then(() => setUnread(0)).catch(() => {});
    }, [isOpen, isCustomer, userId]);

    useEffect(() => {
        if (scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [messages, isOpen]);

    const handleSend = (e) => {
        e.preventDefault();
        const content = input.trim();
        if (!content || !connected) return;
        // Server gửi lại chính tin này qua hàng đợi riêng, nên không tự thêm vào danh sách ở đây
        if (connection.current?.send(content)) {
            setInput('');
        }
    };

    if (!isCustomer) return null;

    return (
        <div className="chat-container">
            {!isOpen && (
                <button
                    onClick={() => setIsOpen(true)}
                    className="btn btn-primary chat-launcher"
                >
                    <MessageCircle size={28} />
                    {unread > 0 && <span className="chat-badge">{unread > 9 ? '9+' : unread}</span>}
                </button>
            )}

            {isOpen && (
                <div className="glass-panel chat-window">
                    {/* Header */}
                    <div className="chat-header">
                        <div className="chat-header-user">
                            <div className="chat-avatar">
                                <User size={18} />
                            </div>
                            <div>
                                <div style={{ fontWeight: 700, fontSize: '0.9rem' }}>Hỗ trợ trực tuyến</div>
                                <div style={{ fontSize: '0.7rem', display: 'flex', alignItems: 'center', gap: '4px' }}>
                                    <span className={`status-dot ${connected ? 'status-online' : 'status-offline'}`}></span>
                                    {connected ? 'Sẵn sàng' : 'Đang kết nối...'}
                                </div>
                            </div>
                        </div>
                        <X size={20} style={{ cursor: 'pointer' }} onClick={() => setIsOpen(false)} />
                    </div>

                    {/* Message List */}
                    <div ref={scrollRef} className="chat-messages">
                        {messages.length === 0 && (
                            <div style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.85rem', marginTop: '2rem' }}>
                                Chào bạn! Chúng tôi có thể giúp gì được cho bạn?
                            </div>
                        )}
                        {messages.map((msg) => {
                            const isMe = msg.senderId === userId;
                            return (
                                <div
                                    key={msg.id}
                                    className={`message-bubble ${isMe ? 'message-sent' : 'message-received'}`}
                                >
                                    <div className="message-content">{msg.content}</div>
                                    <div className="message-time">
                                        {new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                    </div>
                                </div>
                            )
                        })}
                    </div>

                    {/* Footer / Input */}
                    <form onSubmit={handleSend} className="chat-footer">
                        <input
                            type="text"
                            disabled={!connected}
                            value={input}
                            maxLength={2000}
                            onChange={(e) => setInput(e.target.value)}
                            placeholder="Nhập tin nhắn..."
                            className="search-input chat-input"
                        />
                        <button
                            type="submit"
                            className="quantity-btn"
                            style={{ color: 'var(--primary)' }}
                            disabled={!input.trim() || !connected}
                        >
                            <Send size={18} />
                        </button>
                    </form>
                </div>
            )}
        </div>
    );
};

export default ChatWidget;

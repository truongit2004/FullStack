import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { MessageCircle, Send, User, Search, ArrowLeft } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import chatService, { SUPPORT_ID } from '../services/ChatService';

const formatTime = (value) => {
    if (!value) return '';
    const date = new Date(value);
    const sameDay = date.toDateString() === new Date().toDateString();
    return sameDay
        ? date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
        : date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' });
};

// Màn hình hỗ trợ: ADMIN xem mọi hội thoại và trả lời khách dưới danh nghĩa bộ phận hỗ trợ.
const AdminChat = () => {
    const { token } = useAuth();
    const [conversations, setConversations] = useState([]);
    const [selectedId, setSelectedId] = useState(null);
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [keyword, setKeyword] = useState('');
    const [connected, setConnected] = useState(false);
    const [loading, setLoading] = useState(true);
    const connection = useRef(null);
    const selectedRef = useRef(null);
    const scrollRef = useRef(null);

    // Tin mới (của khách hoặc của một nhân viên khác) -> cập nhật danh sách và khung đang mở
    const handleIncoming = (msg) => {
        const fromCustomer = msg.senderId !== SUPPORT_ID;
        const customerId = fromCustomer ? msg.senderId : msg.recipientId;
        const isOpenConversation = selectedRef.current === customerId;

        setConversations(prev => {
            const existing = prev.find(c => c.customerId === customerId);
            const updated = {
                customerId,
                customerName: fromCustomer ? msg.senderName : existing?.customerName,
                lastMessage: msg.content,
                lastSenderId: msg.senderId,
                lastMessageAt: msg.timestamp,
                unreadForSupport: fromCustomer && !isOpenConversation ? (existing?.unreadForSupport || 0) + 1 : 0,
            };
            return [updated, ...prev.filter(c => c.customerId !== customerId)];
        });

        if (isOpenConversation) {
            setMessages(prev => (prev.some(m => m.id === msg.id) ? prev : [...prev, msg]));
            if (fromCustomer) chatService.markRead(customerId).catch(() => {});
        }
    };

    useEffect(() => {
        if (!token) return;

        chatService.getConversations()
            .then(data => setConversations(data || []))
            .catch(err => console.error('Failed to load conversations', err))
            .finally(() => setLoading(false));

        connection.current = chatService.connect({
            token,
            asSupport: true,
            onStatus: setConnected,
            onMessage: handleIncoming,
        });

        return () => {
            connection.current?.disconnect();
            connection.current = null;
        };
    }, [token]);

    useEffect(() => {
        if (scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [messages]);

    const openConversation = async (customerId) => {
        selectedRef.current = customerId;
        setSelectedId(customerId);
        setMessages([]);
        setInput('');
        setConversations(prev => prev.map(c => (c.customerId === customerId ? { ...c, unreadForSupport: 0 } : c)));
        try {
            const history = await chatService.getChatHistory(customerId);
            // Bỏ qua kết quả nếu admin đã chuyển sang hội thoại khác trong lúc chờ
            if (selectedRef.current === customerId) setMessages(history || []);
            await chatService.markRead(customerId);
        } catch (err) {
            console.error('Failed to load chat history', err);
        }
    };

    const closeConversation = () => {
        selectedRef.current = null;
        setSelectedId(null);
    };

    const handleSend = (e) => {
        e.preventDefault();
        const content = input.trim();
        if (!content || !connected || !selectedId) return;
        // Server phát lại tin này trên kênh hỗ trợ, nên không tự thêm vào danh sách ở đây
        if (connection.current?.send(content, selectedId)) {
            setInput('');
        }
    };

    const visibleConversations = useMemo(() => {
        const q = keyword.trim().toLowerCase();
        if (!q) return conversations;
        return conversations.filter(c =>
            (c.customerName || '').toLowerCase().includes(q) || String(c.customerId).includes(q));
    }, [conversations, keyword]);

    const selected = conversations.find(c => c.customerId === selectedId);
    const totalUnread = conversations.reduce((sum, c) => sum + (c.unreadForSupport || 0), 0);

    return (
        <div className="page-container">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
                <h1 className="page-title" style={{ marginBottom: '1rem' }}>
                    Hỗ trợ khách hàng
                    {totalUnread > 0 && <span className="chat-count" style={{ marginLeft: '0.75rem', verticalAlign: 'middle' }}>{totalUnread}</span>}
                </h1>
                <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span className={`status-dot ${connected ? 'status-online' : 'status-offline'}`}></span>
                        {connected ? 'Đang trực tuyến' : 'Đang kết nối...'}
                    </span>
                    <Link to="/admin" style={{ color: 'var(--primary)' }}>← Trang quản trị</Link>
                </div>
            </div>

            <div className={`glass-panel support-layout ${selectedId ? 'support-layout-open' : ''}`}>
                {/* Danh sách hội thoại */}
                <aside className="support-list">
                    <div style={{ padding: '1rem', borderBottom: '1px solid var(--glass-border)', position: 'relative' }}>
                        <Search size={16} style={{ position: 'absolute', left: '1.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
                        <input
                            type="text"
                            value={keyword}
                            onChange={(e) => setKeyword(e.target.value)}
                            placeholder="Tìm theo tên hoặc mã khách..."
                            className="form-input"
                            style={{ width: '100%', paddingLeft: '2.25rem' }}
                        />
                    </div>
                    <div style={{ overflowY: 'auto', flexGrow: 1 }}>
                        {loading && <div className="support-empty">Đang tải...</div>}
                        {!loading && visibleConversations.length === 0 && (
                            <div className="support-empty">
                                {conversations.length === 0 ? 'Chưa có khách nào nhắn tin.' : 'Không tìm thấy hội thoại.'}
                            </div>
                        )}
                        {visibleConversations.map(c => (
                            <button
                                key={c.customerId}
                                className={`support-item ${c.customerId === selectedId ? 'support-item-active' : ''}`}
                                onClick={() => openConversation(c.customerId)}
                            >
                                <div className="chat-avatar" style={{ flexShrink: 0 }}><User size={18} /></div>
                                <div style={{ flexGrow: 1, minWidth: 0 }}>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: '0.5rem' }}>
                                        <span style={{ fontWeight: c.unreadForSupport > 0 ? 800 : 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                            {c.customerName || `Khách #${c.customerId}`}
                                        </span>
                                        <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', flexShrink: 0 }}>{formatTime(c.lastMessageAt)}</span>
                                    </div>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: '0.5rem', alignItems: 'center' }}>
                                        <span style={{ fontSize: '0.8rem', color: c.unreadForSupport > 0 ? 'var(--text-main)' : 'var(--text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                            {c.lastSenderId === SUPPORT_ID ? 'Bạn: ' : ''}{c.lastMessage}
                                        </span>
                                        {c.unreadForSupport > 0 && <span className="chat-count">{c.unreadForSupport}</span>}
                                    </div>
                                </div>
                            </button>
                        ))}
                    </div>
                </aside>

                {/* Khung hội thoại */}
                <section className="support-thread">
                    {!selectedId ? (
                        <div className="support-empty" style={{ margin: 'auto' }}>
                            <MessageCircle size={56} strokeWidth={1} style={{ marginBottom: '1rem' }} />
                            <div>Chọn một hội thoại để bắt đầu trả lời.</div>
                        </div>
                    ) : (
                        <>
                            <div style={{ padding: '1rem 1.25rem', borderBottom: '1px solid var(--glass-border)', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                                <button className="quantity-btn support-back" onClick={closeConversation} title="Quay lại danh sách">
                                    <ArrowLeft size={18} />
                                </button>
                                <div className="chat-avatar"><User size={18} /></div>
                                <div>
                                    <div style={{ fontWeight: 700 }}>{selected?.customerName || `Khách #${selectedId}`}</div>
                                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Mã khách hàng: {selectedId}</div>
                                </div>
                            </div>

                            <div ref={scrollRef} className="chat-messages">
                                {messages.map(msg => {
                                    const fromSupport = msg.senderId === SUPPORT_ID;
                                    return (
                                        <div key={msg.id} className={`message-bubble ${fromSupport ? 'message-sent' : 'message-received'}`}>
                                            <div className="message-content">{msg.content}</div>
                                            <div className="message-time" style={fromSupport ? { color: 'rgba(255,255,255,0.75)' } : undefined}>
                                                {fromSupport && msg.senderName ? `${msg.senderName} · ` : ''}
                                                {new Date(msg.timestamp).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' })}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>

                            <form onSubmit={handleSend} className="chat-footer">
                                <input
                                    type="text"
                                    value={input}
                                    maxLength={2000}
                                    disabled={!connected}
                                    onChange={(e) => setInput(e.target.value)}
                                    placeholder={connected ? 'Nhập câu trả lời...' : 'Đang kết nối...'}
                                    className="search-input chat-input"
                                    autoFocus
                                />
                                <button type="submit" className="btn btn-primary" disabled={!input.trim() || !connected} style={{ padding: '0.6rem 1.1rem' }}>
                                    <Send size={18} /> Gửi
                                </button>
                            </form>
                        </>
                    )}
                </section>
            </div>
        </div>
    );
};

export default AdminChat;

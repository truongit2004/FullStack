import React, { useState, useEffect, useRef } from 'react';
import { MessageCircle, Send, X, User } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import chatService from '../services/ChatService';
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

const ChatWidget = () => {
    const { user } = useAuth();
    const [isOpen, setIsOpen] = useState(false);
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [connected, setConnected] = useState(false);
    const stompClient = useRef(null);
    const scrollRef = useRef(null);

    const ADMIN_ID = 'admin-support';

    useEffect(() => {
        if (user && isOpen) {
            connect();
            fetchHistory();
        }
        return () => disconnect();
    }, [user, isOpen]);

    useEffect(() => {
        if (scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [messages]);

    const connect = () => {
        const socket = new SockJS(`${import.meta.env.VITE_API_URL}/ws`); 
        stompClient.current = Stomp.over(socket);
        stompClient.current.debug = null; 

        stompClient.current.connect({}, () => {
            setConnected(true);
            stompClient.current.subscribe(`/user/${user.id}/queue/messages`, (message) => {
                const receivedMsg = JSON.parse(message.body);
                setMessages(prev => [...prev, receivedMsg]);
            });
        }, (error) => {
            console.error("STOMP connection error", error);
        });
    };

    const disconnect = () => {
        if (stompClient.current) {
            stompClient.current.disconnect();
        }
        setConnected(false);
    };

    const fetchHistory = async () => {
        try {
            const data = await chatService.getChatHistory(user.id, ADMIN_ID);
            setMessages(data || []);
        } catch (err) {
            log.warn("History fetch failed");
        }
    };

    const handleSend = (e) => {
        e.preventDefault();
        if (!input.trim() || !connected) return;

        const chatMessage = {
            senderId: user.id,
            recipientId: ADMIN_ID,
            content: input.trim(),
            timestamp: new Date().toISOString()
        };

        stompClient.current.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
        setInput('');
    };

    if (!user) return null;

    return (
        <div className="chat-container">
            {!isOpen && (
                <button 
                    onClick={() => setIsOpen(true)}
                    className="btn btn-primary chat-launcher"
                >
                    <MessageCircle size={28} />
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
                        <X size={20} className="cursor-pointer" onClick={() => setIsOpen(false)} />
                    </div>

                    {/* Message List */}
                    <div ref={scrollRef} className="chat-messages">
                        {messages.length === 0 && (
                            <div style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.85rem', marginTop: '2rem' }}>
                                Chào bạn! Chúng tôi có thể giúp gì được cho bạn?
                            </div>
                        )}
                        {messages.map((msg, i) => {
                            const isMe = msg.senderId === user.id;
                            return (
                                <div 
                                    key={i} 
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

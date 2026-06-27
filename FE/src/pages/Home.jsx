import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import axios from 'axios';

const Home = () => {
    const { user, token, logout, loading } = useAuth();
    const [products, setProducts] = useState([]);
    const [fetching, setFetching] = useState(true);

    useEffect(() => {
        const fetchProducts = async () => {
            try {
                // Thử gọi API lấy danh sách sản phẩm qua proxy
                const res = await axios.get('/api/products?page=0&size=10');
                // Giả định backend trả về { content: [...] } từ Spring Data Page
                setProducts(res.data.content || []);
            } catch (err) {
                console.error("Failed to fetch products, using mocks");
                // Mock data nếu backend chưa có dữ liệu hoặc lỗi
                setProducts([
                    { id: 1, name: 'iPhone 15 Pro Max', price: 1200, emoji: '📱' },
                    { id: 2, name: 'MacBook Pro M3', price: 2500, emoji: '💻' },
                    { id: 3, name: 'AirPods Pro 2', price: 250, emoji: '🎧' },
                    { id: 4, name: 'Apple Watch Ultra', price: 800, emoji: '⌚' },
                ]);
            } finally {
                setFetching(false);
            }
        };

        if (token) fetchProducts();
    }, [token]);

    if (loading || fetching) return <div className="auth-container"><h1>Loading Galaxy...</h1></div>;

    return (
        <div style={{ padding: '2rem' }}>
            <nav className="nav-bar fade-in">
                <div style={{display: 'flex', alignItems: 'center', gap: '1rem'}}>
                    <div style={{
                        width: '40px', height: '40px', borderRadius: '12px', 
                        background: 'linear-gradient(135deg, #3b82f6, #8b5cf6)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold'
                    }}>
                        {user?.username?.charAt(0).toUpperCase()}
                    </div>
                    <div>
                        <div style={{fontWeight: '600'}}>{user?.username}</div>
                        <div style={{fontSize: '0.75rem', color: 'var(--primary-color)'}}>{user?.role}</div>
                    </div>
                </div>
                <h1>Store Meta</h1>
                <button onClick={logout} className="logout-btn">Log Out</button>
            </nav>

            <div className="fade-in" style={{maxWidth: '1200px', margin: '0 auto'}}>
                {user?.role === 'ADMIN' && (
                    <div className="admin-section">
                        <h2>Product Management</h2>
                        <button className="add-btn">+ Add New Product</button>
                    </div>
                )}

                <div className="product-grid">
                    {products.map(product => (
                        <div key={product.id} className="glass-card product-card">
                            <div className="product-image">
                                {product.emoji || '📦'}
                            </div>
                            <div className="product-name">{product.name}</div>
                            <div className="product-price">${product.price}</div>
                            
                            <div className="product-actions">
                                {user?.role === 'ADMIN' ? (
                                    <>
                                        <button className="edit-btn">Edit</button>
                                        <button className="delete-btn">Delete</button>
                                    </>
                                ) : (
                                    <button className="order-btn">Order Now</button>
                                )}
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
};

export default Home;

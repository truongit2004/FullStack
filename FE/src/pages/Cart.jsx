import React, { useEffect, useState } from 'react';
import cartService from '../services/CartService';
import { useAuth } from '../context/AuthContext';
import { Trash2, Plus, Minus, ShoppingBag } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';

const Cart = () => {
    const { token } = useAuth();
    const navigate = useNavigate();
    const [cart, setCart] = useState(null);
    const [loading, setLoading] = useState(true);

    const fetchCart = async () => {
        try {
            const data = await cartService.getCart();
            setCart(data);
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (!token) {
            navigate('/login');
            return;
        }
        fetchCart();
    }, [token, navigate]);

    const handleUpdateQuantity = async (productId, quantity) => {
        if (quantity < 1) return;
        try {
            await cartService.updateQuantity(productId, quantity);
            fetchCart();
        } catch (err) {
            alert(err.response?.data?.message || 'Error updating quantity');
        }
    };

    const handleRemoveItem = async (productId) => {
        try {
            await cartService.removeItem(productId);
            fetchCart();
        } catch (err) {
            alert('Error removing item');
        }
    };

    const handleClearCart = async () => {
        if (!window.confirm("Are you sure you want to clear your cart?")) return;
        try {
            await cartService.clearCart();
            fetchCart();
        } catch (err) {
            alert('Error clearing cart');
        }
    };


    if (loading) return <div className="loading-screen">Loading Cart...</div>;

    if (!cart || !cart.items || cart.items.length === 0) {
        return (
            <div className="page-container" style={{ textAlign: 'center', paddingTop: '10vh' }}>
                <ShoppingBag size={100} style={{ color: 'var(--text-muted)', margin: '0 auto 2rem auto' }} />
                <h2 className="page-title">Your cart is empty</h2>
                <Link to="/products" className="btn btn-primary">Start Shopping</Link>
            </div>
        );
    }

    return (
        <div className="page-container">
            <h1 className="page-title">Your Cart</h1>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 350px', gap: '2rem' }}>
                
                {/* Cart Items */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                    {cart.items.map(item => (
                        <div key={item.productId} className="glass-panel" style={{ display: 'flex', padding: '1rem', gap: '1.5rem', alignItems: 'center' }}>
                            <img 
                                src={`/api/products/files/${item.productImage}`} 
                                alt={item.productName} 
                                style={{ width: '80px', height: '80px', objectFit: 'cover', borderRadius: '8px' }}
                                onError={(e) => { e.target.src = 'https://placehold.co/100x100?text=No+Img' }}
                            />
                            <div style={{ flexGrow: 1 }}>
                                <h3 style={{ fontSize: '1.1rem', marginBottom: '0.5rem' }}>{item.productName}</h3>
                                <div style={{ color: 'var(--primary)', fontWeight: '700' }}>${item.price?.toFixed(2)}</div>
                            </div>
                            
                            <div className="quantity-selector">
                                <button className="quantity-btn" onClick={() => handleUpdateQuantity(item.productId, item.quantity - 1)}><Minus size={16} /></button>
                                <span style={{ width: '30px', textAlign: 'center' }}>{item.quantity}</span>
                                <button className="quantity-btn" onClick={() => handleUpdateQuantity(item.productId, item.quantity + 1)}><Plus size={16} /></button>
                            </div>

                            <div style={{ width: '80px', textAlign: 'right', fontWeight: '700' }}>
                                ${item.subtotal?.toFixed(2)}
                            </div>

                            <button onClick={() => handleRemoveItem(item.productId)} style={{ background: 'transparent', border: 'none', color: 'var(--danger)', cursor: 'pointer', padding: '0.5rem' }}>
                                <Trash2 size={20} />
                            </button>
                        </div>
                    ))}
                    <button onClick={handleClearCart} className="btn" style={{ width: 'max-content', background: 'transparent', border: '1px solid var(--danger)', color: 'var(--danger)' }}>
                        Clear Entire Cart
                    </button>
                </div>

                {/* Summary */}
                <div className="glass-panel" style={{ padding: '2rem', height: 'max-content', position: 'sticky', top: '100px' }}>
                    <h2 style={{ marginBottom: '1.5rem', borderBottom: '1px solid var(--glass-border)', paddingBottom: '1rem' }}>Order Summary</h2>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1rem', color: 'var(--text-muted)' }}>
                        <span>Total Items</span>
                        <span>{cart.totalItems}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '2rem', fontSize: '1.5rem', fontWeight: 700 }}>
                        <span>Total</span>
                        <span style={{ color: 'var(--primary)' }}>${cart.totalAmount?.toFixed(2)}</span>
                    </div>
                    <Link to="/checkout" className="btn btn-accent" style={{ wth: '100%', fontSize: '1.2rem', padding: '1rem' }}>
                        Proceed to Checkout
                    </Link>
                </div>
            </div>
        </div>
    );
};

export default Cart;

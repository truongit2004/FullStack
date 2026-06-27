import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import productService from '../services/ProductService';
import cartService from '../services/CartService';
import reviewService from '../services/ReviewService';
import orderService from '../services/OrderService';
import { ShoppingCart, ShieldCheck, Truck, Minus, Plus, AlertCircle, Star, Tag, Package } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const ProductDetail = () => {
    const { id } = useParams();
    const { token, user } = useAuth();
    const navigate = useNavigate();

    const [product, setProduct] = useState(null);
    const [loading, setLoading] = useState(true);
    const [quantity, setQuantity] = useState(1);
    const [adding, setAdding] = useState(false);
    const [addedMsg, setAddedMsg] = useState(false);

    useEffect(() => {
        const fetchProduct = async () => {
            try {
                const data = await productService.getProductById(id);
                setProduct(data);
            } catch (err) {
                console.error('Error fetching product details', err);
            } finally {
                setLoading(false);
            }
        };
        fetchProduct();
    }, [id]);

    const handleAddToCart = async () => {
        if (!token) {
            navigate('/login');
            return;
        }
        setAdding(true);
        try {
            await cartService.addItem(product.id, quantity);
            setAddedMsg(true);
            setTimeout(() => setAddedMsg(false), 2500);
        } catch (err) {
            console.error('Cart error:', err);
            alert(err.response?.data?.message || 'Thêm vào giỏ hàng thất bại');
        } finally {
            setAdding(false);
        }
    };

    if (loading) return (
        <div className="loading-screen">
            <div className="spinner" />
        </div>
    );
    if (!product) return (
        <div className="page-container">
            <div className="empty-state">
                <Package size={64} strokeWidth={1} />
                <h2>Không tìm thấy sản phẩm</h2>
            </div>
        </div>
    );

    const imageUrl = product.imageUrl
        ? `/api/products/files/${product.imageUrl.split('/').pop()}`
        : `https://placehold.co/600x600/1e293b/60a5fa?text=${encodeURIComponent(product.name)}`;

    return (
        <div className="page-container">
            <div className="glass-panel" style={{ padding: '2rem' }}>
                <div className="product-detail">
                    {/* Image */}
                    <div className="detail-image-wrap">
                        {product.isOnSale && (
                            <div className="product-badge" style={{ top: '1rem', left: '1rem', right: 'auto' }}>
                                SALE -{product.discountPercentage}%
                            </div>
                        )}
                        <img
                            src={imageUrl}
                            alt={product.name}
                            className="detail-image"
                            onError={(e) => {
                                e.target.src = `https://placehold.co/600x600/1e293b/60a5fa?text=${encodeURIComponent(product.name)}`;
                            }}
                        />
                    </div>

                    {/* Info */}
                    <div className="detail-info">
                        {product.categoryName && (
                            <span className="product-category-tag">
                                <Tag size={11} style={{ display: 'inline', marginRight: '4px' }} />
                                {product.categoryName}
                            </span>
                        )}
                        <h1 style={{ fontSize: '2.2rem', fontWeight: 800, lineHeight: 1.2 }}>{product.name}</h1>

                        {/* Rating */}
                        {product.reviewCount > 0 && (
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                                <StarRating rating={product.averageRating} />
                                <span style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                                    ({product.reviewCount} đánh giá)
                                </span>
                            </div>
                        )}

                        {/* Price */}
                        <div className="detail-price">
                            <span style={{ color: product.isOnSale ? 'var(--accent)' : 'var(--primary)' }}>
                                {Number(product.price).toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}
                            </span>
                            {product.isOnSale && product.originalPrice && (
                                <span className="price-original" style={{ marginLeft: '1rem' }}>
                                    {Number(product.originalPrice).toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })}
                                </span>
                            )}
                        </div>

                        {/* Stock */}
                        {product.stockQuantity !== undefined && (
                            <div style={{ fontSize: '0.9rem', color: product.stockQuantity > 0 ? 'var(--accent)' : 'var(--danger)' }}>
                                {product.stockQuantity > 0 ? `✓ Còn ${product.stockQuantity} sản phẩm` : '✗ Hết hàng'}
                            </div>
                        )}

                        <p className="detail-desc">
                            {product.description || 'Sản phẩm chất lượng cao, được thiết kế để đáp ứng mọi nhu cầu của bạn.'}
                        </p>

                        {/* Features */}
                        <div style={{ display: 'flex', gap: '2rem', flexWrap: 'wrap' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--accent)' }}>
                                <ShieldCheck size={20} />
                                <span style={{ fontSize: '0.9rem' }}>Bảo hành 2 năm</span>
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--primary)' }}>
                                <Truck size={20} />
                                <span style={{ fontSize: '0.9rem' }}>Giao hàng nhanh</span>
                            </div>
                        </div>

                        {/* Quantity */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                            <label style={{ fontWeight: 600, color: 'var(--text-muted)', fontSize: '0.9rem' }}>Số lượng</label>
                            <div className="quantity-selector">
                                <button
                                    className="quantity-btn"
                                    onClick={() => setQuantity(Math.max(1, quantity - 1))}
                                    disabled={quantity <= 1}
                                >
                                    <Minus size={16} />
                                </button>
                                <span style={{ fontWeight: 700, width: '40px', textAlign: 'center', fontSize: '1.1rem' }}>
                                    {quantity}
                                </span>
                                <button
                                    className="quantity-btn"
                                    onClick={() => setQuantity(q => q + 1)}
                                    disabled={product.stockQuantity !== undefined && quantity >= product.stockQuantity}
                                >
                                    <Plus size={16} />
                                </button>
                            </div>
                        </div>

                        {addedMsg && (
                            <div style={{
                                background: 'rgba(16, 185, 129, 0.15)',
                                border: '1px solid rgba(16, 185, 129, 0.4)',
                                padding: '0.75rem 1rem',
                                borderRadius: '8px',
                                color: 'var(--accent)',
                                fontWeight: 600,
                                animation: 'fadeIn 0.3s ease'
                            }}>
                                ✓ Đã thêm vào giỏ hàng!
                            </div>
                        )}

                        <button
                            className="btn btn-primary"
                            style={{ padding: '1rem', fontSize: '1.1rem', marginTop: '0.5rem' }}
                            onClick={handleAddToCart}
                            disabled={adding || (product.stockQuantity !== undefined && product.stockQuantity === 0)}
                        >
                            <ShoppingCart size={22} />
                            {adding ? 'Đang thêm...' : 'Thêm vào giỏ hàng'}
                        </button>
                    </div>
                </div>

                {/* Reviews */}
                <ProductReviews productId={product.id} />
            </div>
        </div>
    );
};

/* ──────────── Star Rating Component ──────────── */
const StarRating = ({ rating, interactive = false, onRate }) => {
    const [hovered, setHovered] = useState(0);
    const stars = [1, 2, 3, 4, 5];
    return (
        <div style={{ display: 'flex', gap: '2px' }}>
            {stars.map(n => (
                <Star
                    key={n}
                    size={16}
                    fill={(hovered || rating) >= n ? '#fbbf24' : 'transparent'}
                    color={rating >= n ? '#fbbf24' : 'rgba(255,255,255,0.3)'}
                    style={{ cursor: interactive ? 'pointer' : 'default' }}
                    onMouseEnter={() => interactive && setHovered(n)}
                    onMouseLeave={() => interactive && setHovered(0)}
                    onClick={() => interactive && onRate && onRate(n)}
                />
            ))}
        </div>
    );
};

/* ──────────── Reviews Section ──────────── */
const ProductReviews = ({ productId }) => {
    const [reviews, setReviews] = useState([]);
    const [newReview, setNewReview] = useState({ rating: 5, comment: '' });
    const [submitting, setSubmitting] = useState(false);
    const [canReview, setCanReview] = useState(false);
    const [loadingReviews, setLoadingReviews] = useState(true);
    const { user } = useAuth();

    useEffect(() => {
        fetchReviews();
        if (user) checkPurchase();
    }, [productId, user]);

    const checkPurchase = async () => {
        if (!user?.id) return;
        try {
            const purchased = await orderService.checkPurchased(user.id, productId);
            setCanReview(purchased);
        } catch (err) {
            console.error('Failed to check purchase status', err);
        }
    };

    const fetchReviews = async () => {
        setLoadingReviews(true);
        try {
            const data = await reviewService.getReviewsByProduct(productId);
            setReviews(data || []);
        } catch (err) {
            console.error('Failed to fetch reviews', err);
        } finally {
            setLoadingReviews(false);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!user) { alert('Vui lòng đăng nhập để đánh giá'); return; }
        if (!newReview.comment.trim()) { alert('Vui lòng nhập nội dung đánh giá'); return; }
        setSubmitting(true);
        try {
            await reviewService.createReview({
                productId,
                userId: user.id,
                rating: newReview.rating,
                comment: newReview.comment.trim()
            });
            setNewReview({ rating: 5, comment: '' });
            fetchReviews();
        } catch (err) {
            alert(err.response?.data?.message || 'Gửi đánh giá thất bại');
        } finally {
            setSubmitting(false);
        }
    };

    const avgRating = reviews.length > 0
        ? reviews.reduce((s, r) => s + r.rating, 0) / reviews.length
        : 0;

    return (
        <div style={{ marginTop: '4rem', borderTop: '1px solid var(--glass-border)', paddingTop: '2.5rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '2rem' }}>
                <h2 style={{ margin: 0 }}>Đánh giá sản phẩm</h2>
                {reviews.length > 0 && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', background: 'rgba(251,191,36,0.1)', padding: '0.3rem 0.8rem', borderRadius: '20px' }}>
                        <Star size={16} fill="#fbbf24" color="#fbbf24" />
                        <span style={{ fontWeight: 700, color: '#fbbf24' }}>{avgRating.toFixed(1)}</span>
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>({reviews.length})</span>
                    </div>
                )}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '2rem' }}>
                {/* Review Form */}
                <div className="glass-panel" style={{ padding: '1.5rem', height: 'fit-content' }}>
                    <h3 style={{ marginBottom: '1.25rem' }}>Viết đánh giá</h3>
                    {!user ? (
                        <div style={{ textAlign: 'center', padding: '1.5rem', background: 'rgba(255,255,255,0.03)', borderRadius: '12px' }}>
                            <p style={{ fontSize: '0.9rem', marginBottom: '1rem', color: 'var(--text-muted)' }}>
                                Đăng nhập để chia sẻ trải nghiệm của bạn
                            </p>
                            <a href="/login" className="btn btn-primary" style={{ display: 'inline-flex' }}>Đăng nhập</a>
                        </div>
                    ) : !canReview ? (
                        <div style={{ padding: '1rem', background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)', borderRadius: '10px', display: 'flex', gap: '0.75rem', alignItems: 'flex-start' }}>
                            <AlertCircle color="#f87171" size={20} style={{ flexShrink: 0, marginTop: '2px' }} />
                            <p style={{ fontSize: '0.9rem', color: '#fca5a5', margin: 0 }}>
                                Bạn cần mua sản phẩm này trước khi có thể đánh giá.
                            </p>
                        </div>
                    ) : (
                        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
                            <div>
                                <label style={{ display: 'block', marginBottom: '0.5rem', fontSize: '0.85rem', color: 'var(--text-muted)', fontWeight: 600 }}>
                                    Đánh giá của bạn
                                </label>
                                <StarRating
                                    rating={newReview.rating}
                                    interactive
                                    onRate={(n) => setNewReview(r => ({ ...r, rating: n }))}
                                />
                            </div>
                            <div>
                                <label style={{ display: 'block', marginBottom: '0.5rem', fontSize: '0.85rem', color: 'var(--text-muted)', fontWeight: 600 }}>
                                    Nhận xét
                                </label>
                                <textarea
                                    value={newReview.comment}
                                    onChange={(e) => setNewReview(r => ({ ...r, comment: e.target.value }))}
                                    className="form-input"
                                    style={{ width: '100%', minHeight: '120px', resize: 'vertical' }}
                                    placeholder="Chia sẻ trải nghiệm của bạn về sản phẩm..."
                                    required
                                />
                            </div>
                            <button type="submit" className="btn btn-primary" disabled={submitting}>
                                {submitting ? 'Đang gửi...' : 'Gửi đánh giá'}
                            </button>
                        </form>
                    )}
                </div>

                {/* Review List */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                    {loadingReviews ? (
                        [...Array(3)].map((_, i) => (
                            <div key={i} className="glass-panel skeleton" style={{ height: '100px' }} />
                        ))
                    ) : reviews.length === 0 ? (
                        <div className="empty-state" style={{ padding: '3rem' }}>
                            <Star size={40} strokeWidth={1} />
                            <p>Chưa có đánh giá nào. Hãy là người đầu tiên!</p>
                        </div>
                    ) : (
                        reviews.map(review => (
                            <div key={review.id} className="glass-panel" style={{ padding: '1.25rem' }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                                        <div style={{
                                            width: '36px', height: '36px', borderRadius: '50%',
                                            background: 'linear-gradient(135deg, var(--primary), #8b5cf6)',
                                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                                            fontWeight: 700, fontSize: '0.85rem'
                                        }}>
                                            {(review.userId || 'U').toString().charAt(0).toUpperCase()}
                                        </div>
                                        <div>
                                            <div style={{ fontWeight: 600, fontSize: '0.9rem' }}>
                                                Người dùng #{(review.userId || '').toString().substring(0, 6)}
                                            </div>
                                            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                                                {review.createdAt ? new Date(review.createdAt).toLocaleDateString('vi-VN') : 'Vừa xong'}
                                            </div>
                                        </div>
                                    </div>
                                    <StarRating rating={review.rating} />
                                </div>
                                <p style={{ margin: 0, fontSize: '0.95rem', lineHeight: 1.6, color: 'rgba(255,255,255,0.85)' }}>
                                    {review.comment}
                                </p>
                            </div>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
};

export default ProductDetail;

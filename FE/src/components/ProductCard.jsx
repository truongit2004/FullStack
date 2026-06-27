import React from 'react';
import { Link } from 'react-router-dom';
import { ShoppingCart, Star } from 'lucide-react';

const ProductCard = ({ p }) => {
    const filename = p.imageUrl ? p.imageUrl.split('/').pop() : null;
    const imgSrc = filename
        ? `/api/products/files/${filename}`
        : `https://placehold.co/400x400/1e293b/60a5fa?text=${encodeURIComponent(p.name?.charAt(0) || '?')}`;

    return (
        <Link to={`/products/${p.id}`} className="glass-panel product-card">
            <div className="product-image-wrap">
                {p.onSale && <div className="product-badge">SALE</div>}
                <img
                    src={imgSrc}
                    alt={p.name}
                    className="product-image"
                    onError={(e) => { e.target.src = 'https://placehold.co/400x400/1e293b/60a5fa?text=No+Image'; }}
                />
            </div>

            <div className="product-info">
                <h3 className="product-name">{p.name}</h3>
                {p.categoryName && (
                    <span className="product-category-tag">{p.categoryName}</span>
                )}
                <div style={{ display: 'flex', gap: '3px', color: '#fbbf24', margin: '0.5rem 0 0.75rem' }}>
                    {[1, 2, 3, 4, 5].map(s => <Star key={s} size={14} fill="currentColor" />)}
                </div>
                <div className="product-price">
                    {p.onSale ? (
                        <>
                            <span className="price-original">${p.originalPrice?.toFixed(2)}</span>
                            <span className="price-sale">${p.price?.toFixed(2)}</span>
                        </>
                    ) : (
                        <span>${p.price?.toFixed(2) || '0.00'}</span>
                    )}
                </div>
                <button
                    className="btn btn-primary product-btn"
                    onClick={(e) => { e.preventDefault(); }}
                >
                    <ShoppingCart size={16} />
                    View Details
                </button>
            </div>
        </Link>
    );
};

export default ProductCard;

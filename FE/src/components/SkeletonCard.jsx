import React from 'react';

const SkeletonCard = () => (
    <div className="glass-panel product-card skeleton-card">
        <div className="skeleton skeleton-img" />
        <div className="product-info">
            <div className="skeleton skeleton-text" style={{ width: '80%', height: '20px', marginBottom: '8px' }} />
            <div className="skeleton skeleton-text" style={{ width: '50%', height: '16px', marginBottom: '16px' }} />
            <div className="skeleton skeleton-text" style={{ width: '40%', height: '28px', marginBottom: '16px' }} />
            <div className="skeleton skeleton-text" style={{ width: '100%', height: '42px', borderRadius: '8px' }} />
        </div>
    </div>
);

export default SkeletonCard;

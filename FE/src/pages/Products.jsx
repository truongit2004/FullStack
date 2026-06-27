import React, { useEffect, useState } from 'react';
import productService from '../services/ProductService';
import { useLocation } from 'react-router-dom';
import { Search, SlidersHorizontal, PackageX } from 'lucide-react';
import ProductCard from '../components/ProductCard';
import SkeletonCard from '../components/SkeletonCard';

const Products = () => {
    const [products, setProducts] = useState([]);
    const [filtered, setFiltered] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [sortBy, setSortBy] = useState('default');
    const [categories, setCategories] = useState([]);
    const [activeCategory, setActiveCategory] = useState('all');

    const { search } = useLocation();
    const query = new URLSearchParams(search).get('search');

    /* ─── Fetch Data ────────────────────────────── */
    useEffect(() => {
        const fetchProducts = async () => {
            setLoading(true);
            try {
                let data = [];
                if (query) {
                    data = await productService.searchProducts(query);
                } else {
                    const response = await productService.getProducts(0, 50);
                    data = response.content || [];
                }
                setProducts(data);
                
                // Tự động build danh sách category từ dữ liệu trả về
                const uniqueCats = [...new Set(data.map(p => p.categoryName).filter(Boolean))];
                setCategories(uniqueCats);
            } catch (err) {
                console.error('Không thể tải sản phẩm:', err);
                setProducts([]);
            } finally {
                setLoading(false);
            }
        };
        fetchProducts();
    }, [query]);

    /* ─── Filter & Sort Logic ────────────────────── */
    useEffect(() => {
        let result = [...products];

        if (activeCategory !== 'all') {
            result = result.filter(p => p.categoryName === activeCategory);
        }
        if (searchTerm.trim()) {
            result = result.filter(p =>
                p.name?.toLowerCase().includes(searchTerm.toLowerCase())
            );
        }
        
        // Sorting
        switch (sortBy) {
            case 'price-asc': result.sort((a, b) => (a.price || 0) - (b.price || 0)); break;
            case 'price-desc': result.sort((a, b) => (b.price || 0) - (a.price || 0)); break;
            case 'name': result.sort((a, b) => a.name?.localeCompare(b.name)); break;
            case 'sale': result.sort((a, b) => (b.onSale ? 1 : 0) - (a.onSale ? 1 : 0)); break;
            default: break;
        }

        setFiltered(result);
    }, [products, searchTerm, sortBy, activeCategory]);

    return (
        <div className="page-container">
            {/* Header Section */}
            <div className="products-header fade-in">
                <h1 className="page-title">Khám Phá Sản Phẩm</h1>
                <p className="page-subtitle">
                    {loading ? 'Đang tải...' : `${filtered.length} sản phẩm hiện có`}
                </p>

                {/* Search & Sort bar */}
                <div className="products-toolbar">
                    <div className="search-box">
                        <Search size={18} className="search-icon" />
                        <input
                            type="text"
                            placeholder="Tìm kiếm sản phẩm..."
                            value={searchTerm}
                            onChange={e => setSearchTerm(e.target.value)}
                            className="search-input"
                        />
                    </div>
                    <div className="sort-box">
                        <SlidersHorizontal size={16} style={{ color: 'var(--text-muted)' }} />
                        <select
                            value={sortBy}
                            onChange={e => setSortBy(e.target.value)}
                            className="sort-select"
                        >
                            <option value="default">Mặc định</option>
                            <option value="price-asc">Giá: Thấp → Cao</option>
                            <option value="price-desc">Giá: Cao → Thấp</option>
                            <option value="name">Tên A-Z</option>
                            <option value="sale">Đang giảm giá</option>
                        </select>
                    </div>
                </div>

                {/* Category Selection */}
                {categories.length > 0 && (
                    <div className="category-chips">
                        <button
                            className={`chip ${activeCategory === 'all' ? 'chip-active' : ''}`}
                            onClick={() => setActiveCategory('all')}
                        >
                            Tất cả
                        </button>
                        {categories.map(cat => (
                            <button
                                key={cat}
                                className={`chip ${activeCategory === cat ? 'chip-active' : ''}`}
                                onClick={() => setActiveCategory(cat)}
                            >
                                {cat}
                            </button>
                        ))}
                    </div>
                )}
            </div>

            {/* Content Section */}
            {loading ? (
                <div className="product-grid">
                    {[...Array(8)].map((_, i) => <SkeletonCard key={i} />)}
                </div>
            ) : filtered.length === 0 ? (
                <div className="empty-state fade-in">
                    <PackageX size={64} strokeWidth={1} />
                    <h2>Không tìm thấy sản phẩm</h2>
                    <p>Thử thay đổi bộ lọc hoặc từ khóa tìm kiếm</p>
                </div>
            ) : (
                <div className="product-grid fade-in">
                    {filtered.map(p => <ProductCard key={p.id} p={p} />)}
                </div>
            )}
        </div>
    );
};

export default Products;

import React, { useState, useEffect } from 'react';
import productService from '../services/ProductService';
import orderService from '../services/OrderService';
import userService from '../services/UserService';
import { useAuth } from '../context/AuthContext';
import { LayoutDashboard, ShoppingBag, ListIcon, Settings, Edit, Trash2, PlusCircle, CheckCircle, Truck, PackageCheck, RotateCcw, TrendingUp, Users, DollarSign, Package, Clock } from 'lucide-react';

const ORDER_PAGE_SIZE = 20;
const ORDER_STATUSES = ['PENDING', 'CONFIRMED', 'PAID', 'SHIPPING', 'DELIVERED', 'CANCELLED', 'RETURNED', 'REFUNDED'];
const EMPTY_ORDER_FILTER = { fromDate: '', toDate: '', status: '' };

// Ngày theo giờ máy người dùng, dạng YYYY-MM-DD cho <input type="date">
const toDateInput = (date) => {
    const pad = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
};

const Admin = () => {
    const { user, token } = useAuth();
    const [activeTab, setActiveTab] = useState('dashboard');
    const [data, setData] = useState([]);
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(false);
    const [showModal, setShowModal] = useState(false);
    const [editingProduct, setEditingProduct] = useState(null);
    const [productForm, setProductForm] = useState({
        name: '', price: '', originalPrice: '', description: '', categoryId: '', stockQuantity: '', image: null
    });
    const [categories, setCategories] = useState([]);
    const [showCategoryModal, setShowCategoryModal] = useState(false);
    const [editingCategory, setEditingCategory] = useState(null);
    const [categoryForm, setCategoryForm] = useState({ name: '', description: '' });
    // Orders tab: bộ lọc đang nhập, bộ lọc đã áp dụng, và phân trang
    const [orderFilterDraft, setOrderFilterDraft] = useState(EMPTY_ORDER_FILTER);
    const [orderFilter, setOrderFilter] = useState(EMPTY_ORDER_FILTER);
    const [orderPage, setOrderPage] = useState(0);
    const [orderPageInfo, setOrderPageInfo] = useState({ totalPages: 1, totalElements: 0 });


    useEffect(() => {
        if (activeTab === 'dashboard') {
            fetchStats();
        } else {
            fetchData();
        }
    }, [activeTab, orderPage, orderFilter]);

    const fetchStats = async () => {
        setLoading(true);
        try {
            const data = await orderService.getOrderStats();
            setStats(data);
        } catch (err) {
            console.error("Failed to fetch stats", err);
        } finally {
            setLoading(false);
        }
    };

    const fetchData = async () => {
        setLoading(true);
        try {
            if (activeTab === 'products') {
                const res = await productService.getProducts(0, 100);
                setData(res.content || []);
                // Fetch categories for the form
                const catRes = await productService.getCategories();
                setCategories(catRes || []);
            } else if (activeTab === 'orders') {
                // Đơn của toàn hệ thống, mới nhất trước; lọc theo ngày đặt (tính trọn ngày) và trạng thái
                const res = await orderService.filterOrders({
                    status: orderFilter.status || undefined,
                    fromDate: orderFilter.fromDate ? `${orderFilter.fromDate}T00:00:00` : undefined,
                    toDate: orderFilter.toDate ? `${orderFilter.toDate}T23:59:59` : undefined,
                }, orderPage, ORDER_PAGE_SIZE);
                setData(res.content || []);
                setOrderPageInfo({ totalPages: res.totalPages || 1, totalElements: res.totalElements || 0 });
            } else if (activeTab === 'categories') {
                const res = await productService.getCategories();
                setData(res || []);
            } else if (activeTab === 'users') {
                const res = await userService.getAllUsers();
                setData(res.content || res || []);
            }
        } catch (err) {
            console.error("Fetch failed", err);
            setData([]);
        } finally {
            setLoading(false);
        }
    };

    const handleUpdateOrderStatus = async (orderId, action) => {
        try {
            await orderService.updateOrderStatus(orderId, action);
            alert(`Order ${action}ed successfully!`);
            fetchData();
        } catch (err) {
            alert(err.response?.data?.message || "Operation failed");
        }
    };

    const applyOrderFilter = (filter) => {
        setOrderFilterDraft(filter);
        setOrderFilter(filter);
        setOrderPage(0);
    };

    // days = 1: hôm nay; days = 7: 7 ngày gần nhất (tính cả hôm nay)
    const applyOrderPreset = (days) => {
        const to = new Date();
        const from = new Date();
        from.setDate(to.getDate() - (days - 1));
        applyOrderFilter({ ...orderFilterDraft, fromDate: toDateInput(from), toDate: toDateInput(to) });
    };

    const handleDeleteProduct = async (id) => {
        if (!window.confirm("Delete this product?")) return;
        try {
            await productService.deleteProduct(id);
            fetchData();
        } catch (err) {
            alert("Delete failed");
        }
    };

    const handleDeleteUser = async (id) => {
        if (!window.confirm("Delete this user? This action cannot be undone.")) return;
        try {
            await userService.deleteUser(id);
            fetchData();
        } catch (err) {
            alert(err.response?.data?.message || "Delete failed");
        }
    };

    const openAddCategoryModal = () => {
        setEditingCategory(null);
        setCategoryForm({ name: '', description: '' });
        setShowCategoryModal(true);
    };

    const openEditCategoryModal = (category) => {
        setEditingCategory(category);
        setCategoryForm({ name: category.name || '', description: category.description || '' });
        setShowCategoryModal(true);
    };

    const handleDeleteCategory = async (id) => {
        if (!window.confirm("Delete this category?")) return;
        try {
            await productService.deleteCategory(id);
            fetchData();
        } catch (err) {
            alert(err.response?.data?.message || "Delete failed. It may still have products assigned.");
        }
    };

    const handleCategorySubmit = async (e) => {
        e.preventDefault();
        try {
            if (editingCategory) {
                await productService.updateCategory(editingCategory.id, categoryForm);
            } else {
                await productService.createCategory(categoryForm);
            }
            setShowCategoryModal(false);
            setEditingCategory(null);
            setCategoryForm({ name: '', description: '' });
            fetchData();
        } catch (err) {
            alert(err.response?.data?.message || "Failed to save category");
        }
    };


    const handleProductSubmit = async (e) => {
        e.preventDefault();
        const formData = new FormData();
        Object.keys(productForm).forEach(key => {
            if (key === 'image' && productForm[key]) {
                formData.append('imageUrl', productForm[key]); // Sửa thành imageUrl để khớp với backend DTO
            } else if (productForm[key] !== null) {
                formData.append(key, productForm[key]);
            }
        });

        try {
            if (editingProduct) {
                await productService.updateProduct(editingProduct.id, formData);
            } else {
                await productService.createProduct(formData);
            }
            setShowModal(false);
            setEditingProduct(null);
            setProductForm({ name: '', price: '', originalPrice: '', description: '', categoryId: '', stockQuantity: '', image: null });
            fetchData();
        } catch (err) {
            alert("Failed to save product");
        }
    };

    const openEditModal = (product) => {
        setEditingProduct(product);
        setProductForm({
            name: product.name,
            price: product.price,
            originalPrice: product.originalPrice,
            description: product.description,
            categoryId: product.categoryId,
            stockQuantity: product.stockQuantity,
            image: null
        });
        setShowModal(true);
    };


    return (
        <div className="page-container" style={{ display: 'grid', gridTemplateColumns: '250px 1fr', gap: '2rem' }}>
            {/* Sidebar */}
            <div className="glass-panel" style={{ padding: '1.5rem', height: 'fit-content', position: 'sticky', top: '100px' }}>
                <h2 style={{ marginBottom: '2rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <LayoutDashboard color="var(--primary)" /> Admin Central
                </h2>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    <SidebarItem icon={<TrendingUp size={18}/>} label="Dashboard" active={activeTab === 'dashboard'} onClick={() => setActiveTab('dashboard')} />
                    <SidebarItem icon={<ShoppingBag size={18}/>} label="Products" active={activeTab === 'products'} onClick={() => setActiveTab('products')} />
                    <SidebarItem icon={<PackageCheck size={18}/>} label="Orders" active={activeTab === 'orders'} onClick={() => setActiveTab('orders')} />
                    <SidebarItem icon={<ListIcon size={18}/>} label="Categories" active={activeTab === 'categories'} onClick={() => setActiveTab('categories')} />
                    <SidebarItem icon={<Users size={18}/>} label="Users" active={activeTab === 'users'} onClick={() => setActiveTab('users')} />
                </div>
            </div>

            {/* Content Area */}
            <div className="glass-panel" style={{ padding: '2rem' }}>
                {activeTab === 'dashboard' ? (
                    <DashboardContent stats={stats} loading={loading} />
                ) : (
                    <>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
                            <h1 style={{ margin: 0, textTransform: 'capitalize' }}>{activeTab} Management</h1>
                            {activeTab === 'products' && (
                                <button className="btn btn-primary" onClick={() => { setEditingProduct(null); setShowModal(true); }}><PlusCircle size={18}/> Add Product</button>
                            )}
                            {activeTab === 'categories' && (
                                <button className="btn btn-primary" onClick={openAddCategoryModal}><PlusCircle size={18}/> Add Category</button>
                            )}
                        </div>

                        {activeTab === 'orders' && (
                            <form
                                onSubmit={(e) => { e.preventDefault(); applyOrderFilter(orderFilterDraft); }}
                                style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-end', gap: '0.75rem', marginBottom: '1.5rem' }}
                            >
                                <div className="form-group" style={{ marginBottom: 0 }}>
                                    <label>From date</label>
                                    <input type="date" className="form-input" value={orderFilterDraft.fromDate} max={orderFilterDraft.toDate || undefined}
                                        onChange={e => setOrderFilterDraft({ ...orderFilterDraft, fromDate: e.target.value })} />
                                </div>
                                <div className="form-group" style={{ marginBottom: 0 }}>
                                    <label>To date</label>
                                    <input type="date" className="form-input" value={orderFilterDraft.toDate} min={orderFilterDraft.fromDate || undefined}
                                        onChange={e => setOrderFilterDraft({ ...orderFilterDraft, toDate: e.target.value })} />
                                </div>
                                <div className="form-group" style={{ marginBottom: 0 }}>
                                    <label>Status</label>
                                    <select className="form-input sort-select" value={orderFilterDraft.status}
                                        onChange={e => setOrderFilterDraft({ ...orderFilterDraft, status: e.target.value })}>
                                        <option value="">All</option>
                                        {ORDER_STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
                                    </select>
                                </div>
                                <button type="submit" className="btn btn-primary">Apply</button>
                                <button type="button" className="btn" style={{ background: 'rgba(255,255,255,0.1)' }} onClick={() => applyOrderFilter(EMPTY_ORDER_FILTER)}>Reset</button>
                                <button type="button" className="chip" onClick={() => applyOrderPreset(1)}>Today</button>
                                <button type="button" className="chip" onClick={() => applyOrderPreset(7)}>Last 7 days</button>
                                <span style={{ marginLeft: 'auto', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                                    {orderPageInfo.totalElements} order{orderPageInfo.totalElements === 1 ? '' : 's'}
                                </span>
                            </form>
                        )}

                        {loading ? (
                            <div style={{ padding: '4rem', textAlign: 'center' }}>Loading...</div>
                        ) : activeTab === 'orders' && data.length === 0 ? (
                            <div style={{ padding: '4rem', textAlign: 'center', color: 'var(--text-muted)' }}>No orders match this filter.</div>
                        ) : (
                            <div className="admin-table-container" style={{ overflowX: 'auto' }}>
                                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                                    <thead>
                                        <tr style={{ textAlign: 'left', borderBottom: '1px solid var(--glass-border)' }}>
                                            <th style={{ padding: '1rem' }}>ID</th>
                                            <th style={{ padding: '1rem' }}>Information</th>
                                            <th style={{ padding: '1rem' }}>Status/Detail</th>
                                            <th style={{ padding: '1rem' }}>Actions</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {data.map(item => (
                                            <tr key={item.id} style={{ borderBottom: '1px solid var(--glass-border)' }}>
                                                <td style={{ padding: '1rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>{String(item.id).substring(0,8)}...</td>
                                                <td style={{ padding: '1rem' }}>
                                                    {activeTab === 'users' ? (
                                                        <>
                                                            <div style={{ fontWeight: 600 }}>{item.username}</div>
                                                            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{item.email}</div>
                                                        </>
                                                    ) : activeTab === 'categories' ? (
                                                        <>
                                                            <div style={{ fontWeight: 600 }}>{item.name}</div>
                                                            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{item.description || 'No description'}</div>
                                                        </>
                                                    ) : (
                                                        <>
                                                            <div style={{ fontWeight: 600 }}>{item.name || `Order ${String(item.id).substring(0,5)}`}</div>
                                                            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                                                                {activeTab === 'orders' ? `User ID: ${item.userId}` : `Cat ID: ${item.categoryId || 'N/A'}`}
                                                            </div>
                                                            {activeTab === 'orders' && item.createdAt && (
                                                                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                                                                    {new Date(item.createdAt).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' })}
                                                                </div>
                                                            )}
                                                        </>
                                                    )}
                                                </td>
                                                <td style={{ padding: '1rem' }}>
                                                    {activeTab === 'products' ? (
                                                        <div style={{ color: 'var(--primary)', fontWeight: 700 }}>${item.price?.toFixed(2)}</div>
                                                    ) : activeTab === 'users' ? (
                                                        <span style={{ padding: '0.2rem 0.6rem', background: item.role === 'ADMIN' ? 'rgba(251,191,36,0.15)' : 'rgba(59,130,246,0.1)', color: item.role === 'ADMIN' ? '#fbbf24' : '#60a5fa', borderRadius: '4px', fontSize: '0.8rem', fontWeight: 600 }}>
                                                            {item.role}
                                                        </span>
                                                    ) : activeTab === 'categories' ? (
                                                        <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>ID: {item.id}</span>
                                                    ) : (
                                                        <div>
                                                            <span style={{ padding: '0.2rem 0.6rem', background: 'rgba(59,130,246,0.1)', color: '#60a5fa', borderRadius: '4px', fontSize: '0.8rem', fontWeight: 600 }}>
                                                                {item.status}
                                                            </span>
                                                            <div style={{ fontSize: '0.9rem', marginTop: '0.3rem', color: 'var(--primary)', fontWeight: 700 }}>${item.finalAmount?.toFixed(2)}</div>
                                                        </div>
                                                    )}
                                                </td>
                                                <td style={{ padding: '1rem' }}>
                                                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                                                        {activeTab === 'products' ? (
                                                             <>
                                                                <button onClick={() => openEditModal(item)} className="quantity-btn"><Edit size={16}/></button>
                                                                <button onClick={() => handleDeleteProduct(item.id)} className="quantity-btn" style={{color:'var(--danger)'}}><Trash2 size={16}/></button>
                                                             </>
                                                        ) : activeTab === 'categories' ? (
                                                            <>
                                                                <button onClick={() => openEditCategoryModal(item)} className="quantity-btn"><Edit size={16}/></button>
                                                                <button onClick={() => handleDeleteCategory(item.id)} className="quantity-btn" style={{color:'var(--danger)'}}><Trash2 size={16}/></button>
                                                            </>
                                                        ) : activeTab === 'users' ? (
                                                            <button
                                                                onClick={() => handleDeleteUser(item.id)}
                                                                className="quantity-btn"
                                                                style={{color:'var(--danger)'}}
                                                                disabled={String(item.id) === String(user?.id)}
                                                                title={String(item.id) === String(user?.id) ? "You can't delete your own account" : 'Delete user'}
                                                            >
                                                                <Trash2 size={16}/>
                                                            </button>
                                                        ) : (
                                                            <div style={{ display: 'flex', gap: '0.3rem', flexWrap: 'wrap' }}>
                                                                {item.status === 'PENDING' && (
                                                                    <button onClick={() => handleUpdateOrderStatus(item.id, 'confirm')} title="Confirm Order" className="quantity-btn" style={{color:'var(--accent)'}}><CheckCircle size={16}/></button>
                                                                )}
                                                                {item.status === 'CONFIRMED' && (
                                                                    <button onClick={() => handleUpdateOrderStatus(item.id, 'ship')} title="Ship Order" className="quantity-btn" style={{color:'var(--primary)'}}><Truck size={16}/></button>
                                                                )}
                                                                {item.status === 'SHIPPING' && (
                                                                    <button onClick={() => handleUpdateOrderStatus(item.id, 'deliver')} title="Mark Delivered" className="quantity-btn" style={{color:'var(--accent)'}}><Package size={16}/></button>
                                                                )}
                                                                {item.status === 'PAID' && (
                                                                    <button onClick={() => handleUpdateOrderStatus(item.id, 'return')} title="Return & Refund" className="quantity-btn" style={{color:'#f87171'}}><RotateCcw size={16}/></button>
                                                                )}
                                                                {(item.status === 'PENDING' || item.status === 'CONFIRMED') && (
                                                                    <button onClick={() => handleUpdateOrderStatus(item.id, 'cancel')} title="Cancel Order" className="quantity-btn" style={{color:'var(--danger)'}}><Trash2 size={16}/></button>
                                                                )}
                                                            </div>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}

                        {activeTab === 'orders' && orderPageInfo.totalPages > 1 && (
                            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '0.75rem', marginTop: '1.5rem' }}>
                                <button className="btn" disabled={loading || orderPage === 0} onClick={() => setOrderPage(p => p - 1)} style={{ padding: '0.5rem 1rem' }}>
                                    ← Prev
                                </button>
                                <span style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                                    Page {orderPage + 1} / {orderPageInfo.totalPages}
                                </span>
                                <button className="btn" disabled={loading || orderPage + 1 >= orderPageInfo.totalPages} onClick={() => setOrderPage(p => p + 1)} style={{ padding: '0.5rem 1rem' }}>
                                    Next →
                                </button>
                            </div>
                        )}
                    </>
                )}
            </div>

            {/* Product Modal */}
            {showModal && (
                <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.8)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem' }}>
                    <div className="glass-panel" style={{ width: '100%', maxWidth: '600px', padding: '2rem', maxHeight: '90vh', overflowY: 'auto' }}>
                        <h2>{editingProduct ? 'Edit Product' : 'Add New Product'}</h2>
                        <form onSubmit={handleProductSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1.5rem' }}>
                            <div className="form-group">
                                <label>Product Name</label>
                                <input type="text" className="form-input" value={productForm.name} onChange={e => setProductForm({...productForm, name: e.target.value})} required />
                            </div>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                                <div className="form-group">
                                    <label>Current Price ($)</label>
                                    <input type="number" step="0.01" className="form-input" value={productForm.price} onChange={e => setProductForm({...productForm, price: e.target.value})} required />
                                </div>
                                <div className="form-group">
                                    <label>Original Price ($)</label>
                                    <input type="number" step="0.01" className="form-input" value={productForm.originalPrice} onChange={e => setProductForm({...productForm, originalPrice: e.target.value})} />
                                </div>
                            </div>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                                <div className="form-group">
                                    <label>Stock Quantity</label>
                                    <input type="number" className="form-input" value={productForm.stockQuantity} onChange={e => setProductForm({...productForm, stockQuantity: e.target.value})} required />
                                </div>
                                <div className="form-group">
                                    <label>Category</label>
                                    <select 
                                        className="form-input" 
                                        value={productForm.categoryId} 
                                        onChange={e => setProductForm({...productForm, categoryId: e.target.value})} 
                                        required
                                    >
                                        <option value="">Select Category</option>
                                        {categories.map(cat => (
                                            <option key={cat.id} value={cat.id}>{cat.name}</option>
                                        ))}
                                    </select>
                                </div>
                            </div>
                            <div className="form-group">
                                <label>Description</label>
                                <textarea className="form-input" style={{ minHeight: '100px' }} value={productForm.description} onChange={e => setProductForm({...productForm, description: e.target.value})}></textarea>
                            </div>
                            <div className="form-group">
                                <label>Product Image</label>
                                <input type="file" onChange={e => setProductForm({...productForm, image: e.target.files[0]})} />
                            </div>
                            <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
                                <button type="button" className="btn" onClick={() => setShowModal(false)} style={{ flex: 1, background: 'rgba(255,255,255,0.1)' }}>Cancel</button>
                                <button type="submit" className="btn btn-primary" style={{ flex: 2 }}>{editingProduct ? 'Update Product' : 'Create Product'}</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Category Modal */}
            {showCategoryModal && (
                <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.8)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem' }}>
                    <div className="glass-panel" style={{ width: '100%', maxWidth: '500px', padding: '2rem' }}>
                        <h2>{editingCategory ? 'Edit Category' : 'Add New Category'}</h2>
                        <form onSubmit={handleCategorySubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1.5rem' }}>
                            <div className="form-group">
                                <label>Category Name</label>
                                <input type="text" className="form-input" value={categoryForm.name} onChange={e => setCategoryForm({...categoryForm, name: e.target.value})} required />
                            </div>
                            <div className="form-group">
                                <label>Description</label>
                                <textarea className="form-input" style={{ minHeight: '80px' }} value={categoryForm.description} onChange={e => setCategoryForm({...categoryForm, description: e.target.value})}></textarea>
                            </div>
                            <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
                                <button type="button" className="btn" onClick={() => setShowCategoryModal(false)} style={{ flex: 1, background: 'rgba(255,255,255,0.1)' }}>Cancel</button>
                                <button type="submit" className="btn btn-primary" style={{ flex: 2 }}>{editingCategory ? 'Update Category' : 'Create Category'}</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

const DashboardContent = ({ stats, loading }) => {
    if (loading) return <div>Loading statistics...</div>;
    if (!stats) return <div>No data available.</div>;

    const cards = [
        { label: 'Total Revenue', value: `$${stats.totalRevenue?.toFixed(2)}`, icon: <DollarSign color="#10b981" />, color: 'rgba(16, 185, 129, 0.1)' },
        { label: 'Total Orders', value: stats.totalOrders, icon: <Package color="#3b82f6" />, color: 'rgba(59, 130, 246, 0.1)' },
        { label: 'Pending Orders', value: stats.pendingOrders, icon: <Clock color="#fbbf24" />, color: 'rgba(251, 191, 36, 0.1)' },
        { label: 'Unique Users', value: stats.totalUsers || 'N/A', icon: <Users color="#8b5cf6" />, color: 'rgba(139, 92, 246, 0.1)' },
    ];

    return (
        <div>
            <h1>Dashboard Overview</h1>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1.5rem', marginBottom: '3rem' }}>
                {cards.map((card, i) => (
                    <div key={i} className="glass-panel" style={{ padding: '1.5rem', background: card.color }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1rem' }}>
                            <span style={{ color: 'var(--text-muted)', fontWeight: 600 }}>{card.label}</span>
                            {card.icon}
                        </div>
                        <div style={{ fontSize: '2rem', fontWeight: 800 }}>{card.value}</div>
                    </div>
                ))}
            </div>

            <div className="glass-panel" style={{ padding: '1.5rem' }}>
                <h3>Order Distribution by Status</h3>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1rem', marginTop: '1rem' }}>
                    {Object.entries(stats.ordersByStatus || {}).map(([status, count]) => (
                        <div key={status} style={{ padding: '0.8rem 1.2rem', background: 'rgba(255,255,255,0.05)', borderRadius: '8px', flex: 1, minWidth: '150px' }}>
                            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{status}</div>
                            <div style={{ fontSize: '1.2rem', fontWeight: 700 }}>{count}</div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
};

const SidebarItem = ({ icon, label, active, onClick }) => (
    <div 
        onClick={onClick}
        style={{ 
            display: 'flex', alignItems: 'center', gap: '1rem', padding: '0.8rem 1rem', 
            borderRadius: '8px', cursor: 'pointer', transition: '0.3s',
            background: active ? 'rgba(59, 130, 246, 0.1)' : 'transparent',
            color: active ? 'var(--primary)' : 'var(--text-main)'
        }}
    >
        {icon}
        <span style={{ fontWeight: 500 }}>{label}</span>
    </div>
);

export default Admin;

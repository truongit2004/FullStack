import React from 'react';
import { ShoppingCart, LogOut, PackageSearch, User, Box, LayoutDashboard } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const Navbar = () => {
    const { user, logout } = useAuth();
    const navigate = useNavigate();

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    return (
        <nav className="navbar">
            <Link to="/" className="nav-brand">
                <Box size={28} />
            </Link>
            
            <div className="search-bar" style={{ flexGrow: 1, maxWidth: '400px', position: 'relative' }}>
                <input 
                    type="text" 
                    placeholder="Search for amazing products..." 
                    style={{ 
                        width: '100%', padding: '0.6rem 2.5rem 0.6rem 1rem', 
                        borderRadius: '20px', border: '1px solid var(--glass-border)',
                        background: 'rgba(255,255,255,0.05)', color: 'white'
                    }}
                    onKeyPress={(e) => {
                        if (e.key === 'Enter') {
                            navigate(`/products?search=${e.target.value}`);
                        }
                    }}
                />
                <PackageSearch size={18} style={{ position: 'absolute', right: '15px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
            </div>

            <div className="nav-links">
                <Link to="/products" className="nav-link">
                    <PackageSearch size={20} />
                    Products
                </Link>
                {user && (
                    <>
                        <Link to="/cart" className="nav-link">
                            <ShoppingCart size={20} />
                            Cart
                        </Link>
                        <Link to="/orders" className="nav-link">
                            <PackageSearch size={20} />
                            Orders
                        </Link>
                        {user.role === 'ADMIN' && (
                            <Link to="/admin" className="nav-link" style={{color: 'var(--primary)'}}>
                                <LayoutDashboard size={20} />
                                Admin
                            </Link>
                        )}
                        <Link to="/profile" className="nav-link" style={{ color: 'var(--accent)', marginLeft: '1rem' }}>
                            <User size={20} />
                            {user.username}
                        </Link>
                        <button onClick={handleLogout} className="btn" style={{ background: 'transparent', color: 'var(--danger)', padding: 0 }}>
                            <LogOut size={20} />
                        </button>
                    </>
                )}
                {!user && (
                    <Link to="/login" className="btn btn-primary" style={{ padding: '0.4rem 1rem' }}>
                        Login
                    </Link>
                )}
            </div>
        </nav>
    );
};

export default Navbar;

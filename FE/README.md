# E-Commerce Frontend

This is a modern React frontend for the E-Commerce Microservices system.

## 🚀 Features
- **Modern UI**: Glassmorphism, gradients, and high-quality typography (Outfit).
- **Authentication**: Complete Sign In, Sign Up, and Sign Out flow.
- **Protected Routes**: Secure access to the dashboard.
- **Dynamic Feedback**: Real-time error handling.
- **State Management**: React Context for auth state persistence.

## 🛠️ Setup Instructions
1. **Prequisites**: Ensure you have [Node.js](https://nodejs.org/) installed.
2. **Install Dependencies**:
   ```bash
   cd FE
   npm install
   ```
3. **Run Backend Services**:
   - Ensure the `Auth Service` is running on `http://localhost:8087`.
   - The Vite proxy is configured to forward `/auth` requests to this port.
4. **Launch Frontend**:
   ```bash
   npm run dev
   ```
5. **Access**: Open `http://localhost:5173` in your browser.

## 📂 Project Structure
- `src/context/AuthContext.jsx`: Centralized auth logic and Axios configuration.
- `src/pages/Login.jsx` & `src/pages/Register.jsx`: Interactive forms with modern design.
- `src/index.css`: Premium styling tokens and animations.
- `vite.config.js`: Proxy settings for backend integration.

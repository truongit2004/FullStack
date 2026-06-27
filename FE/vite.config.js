import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/auth': {
        target: 'http://localhost:8085', // Gateway
        changeOrigin: true
      },
      '/api': {
        target: 'http://localhost:8085', // Gateway
        changeOrigin: true
      }
    }
  }
})

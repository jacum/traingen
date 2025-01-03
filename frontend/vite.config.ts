import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
    server: {
        proxy: {
            '/user/api': {
                target: 'http://localhost:8080', // Replace with your target API server
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/api/, ''), // Optional: rewrite path if needed
            },
        },
    },
})

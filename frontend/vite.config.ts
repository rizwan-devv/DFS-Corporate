import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // Override: VITE_PROXY_TARGET=http://46.224.146.158:8050 npm run dev
        target: process.env.VITE_PROXY_TARGET || 'http://localhost:8090',
        changeOrigin: true,
      },
    },
  },
})

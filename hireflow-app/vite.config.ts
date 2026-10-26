/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

// https://vite.dev/config/
export default defineConfig(({ command }) => ({
  plugins: [tailwindcss(), react(), {
    name: 'hireflow-local-launcher',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url === '/__hireflow/launcher') {
          res.setHeader('Content-Type', 'application/json')
          res.end(JSON.stringify({ service: 'hireflow-frontend', root: import.meta.dirname.replaceAll('\\', '/'), mode: process.env.VITE_HIREFLOW_MODE || 'development', launchId: process.env.HIREFLOW_LAUNCH_ID || null }))
          return
        }
        next()
      })
    },
  }],
  define: {
    'import.meta.env.VITE_HIREFLOW_MODE': JSON.stringify(command === 'serve' ? (process.env.VITE_HIREFLOW_MODE || '') : ''),
  },
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: process.env.HIREFLOW_API_PROXY || 'http://127.0.0.1:8081',
        changeOrigin: true,
        secure: false,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    include: ['src/**/*.test.{ts,tsx}'],
  },
}))


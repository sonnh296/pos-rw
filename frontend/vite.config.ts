import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import fs from 'node:fs'
import path from 'node:path'

import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Load .env* values for dev-server proxy as well
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [
      vue(),
      {
        name: 'serve-local-test-results',
        configureServer(server) {
          server.middlewares.use((req, res, next) => {
            if (req.url === '/api/test-results/csv/phase1' && req.method === 'GET') {
              const file = path.resolve(__dirname, '../loadtest/results_csv/phase1_results.csv')
              if (fs.existsSync(file)) {
                res.setHeader('Content-Type', 'text/plain')
                res.end(fs.readFileSync(file))
              } else {
                res.end('')
              }
              return
            }
            if (req.url === '/api/test-results/csv/phase2' && req.method === 'GET') {
              const file = path.resolve(__dirname, '../loadtest/results_csv/phase2_results.csv')
              if (fs.existsSync(file)) {
                res.setHeader('Content-Type', 'text/plain')
                res.end(fs.readFileSync(file))
              } else {
                res.end('')
              }
              return
            }
            if (req.url === '/api/test-results/csv' && req.method === 'DELETE') {
              const p1 = path.resolve(__dirname, '../loadtest/results_csv/phase1_results.csv')
              const p2 = path.resolve(__dirname, '../loadtest/results_csv/phase2_results.csv')
              if (fs.existsSync(p1)) fs.unlinkSync(p1)
              if (fs.existsSync(p2)) fs.unlinkSync(p2)
              res.setHeader('Content-Type', 'application/json')
              res.end(JSON.stringify({status: 'success'}))
              return
            }
            next()
          })
        }
      }
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      proxy: {
        '/api': {
          target,
          changeOrigin: true,
        },
        '/actuator': {
          target,
          changeOrigin: true,
        },
      },
    },
  }
})

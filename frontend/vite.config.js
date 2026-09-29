import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'


// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      // 浏览器请求 /api/articles 时，Vite 转发给 Spring Boot
      '/api': 'http://localhost:8080',
    },
  },
})

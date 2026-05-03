<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()

const NAV = [
  { to: '/users', label: 'Điểm Khách Hàng', icon: '👥' },
  { to: '/custom-tests', label: 'Kiểm Thử Hiệu Năng', icon: '🧪' },
] as const

const PAGE_TITLES: Record<string, string> = {
  '/users': 'Danh sách điểm khách hàng',
  '/custom-tests': 'Kiểm Thử Hiệu Năng Tùy Chỉnh',
}
</script>

<template>
  <div class="app">
    <header class="topbar">
      <div class="brand">Boost Demo</div>

      <nav class="tabs">
        <router-link
          v-for="n in NAV"
          :key="n.to"
          :to="n.to"
          class="tab"
          :class="{ 'tab--active': route.path === n.to }"
        >
          <span class="tab__icon">{{ n.icon }}</span>
          <span class="tab__label">{{ n.label }}</span>
        </router-link>
      </nav>
    </header>

    <main class="main">
      <h1 class="page-title">{{ PAGE_TITLES[route.path] ?? 'Boost Demo' }}</h1>
      <router-view />
    </main>

    <footer class="footer">
      Proxy dev: <code>/api</code> và <code>/actuator</code> → backend (mặc định
      <code>localhost:8080</code>)
    </footer>
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
  background: #0b1020;
  color: #e8ecff;
  font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 20px;
  height: 52px;
  background: rgba(11, 16, 32, 0.88);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(232, 236, 255, 0.08);
}

.brand {
  font-weight: 800;
  font-size: 15px;
  letter-spacing: 0.2px;
  white-space: nowrap;
  flex-shrink: 0;
}

.tabs {
  display: flex;
  gap: 4px;
  align-items: center;
  overflow-x: auto;
}

.tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid transparent;
  color: rgba(232, 236, 255, 0.65);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
  transition: background 0.12s, color 0.12s, border-color 0.12s;
}

.tab:hover {
  background: rgba(232, 236, 255, 0.06);
  color: #e8ecff;
}

.tab--active {
  border-color: rgba(140, 170, 255, 0.45);
  background: rgba(140, 170, 255, 0.12);
  color: #c7d4ff;
}

.tab__icon {
  font-size: 14px;
  line-height: 1;
}

.main {
  width: min(1200px, calc(100% - 40px));
  margin: 0 auto;
  padding: 22px 0 40px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 16px;
}

.footer {
  width: min(1200px, calc(100% - 40px));
  margin: 0 auto;
  padding: 12px 0 24px;
  opacity: 0.5;
  font-size: 12px;
}

code {
  font-family: ui-monospace, Menlo, Monaco, Consolas, monospace;
  background: rgba(232, 236, 255, 0.08);
  border: 1px solid rgba(232, 236, 255, 0.12);
  padding: 1px 5px;
  border-radius: 6px;
}

@media (max-width: 640px) {
  .tab__label { display: none; }
  .tab { padding: 6px 10px; }
}
</style>

<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()

const NAV = [
  { to: '/users', label: 'Điểm Khách Hàng' },
  { to: '/customers', label: 'Người dùng' },
  { to: '/custom-tests', label: 'Hiệu Năng' },
] as const

const PAGE_TITLES: Record<string, string> = {
  '/users': 'Theo dõi Điểm Khách Hàng',
  '/custom-tests': 'Kiểm Thử Hiệu Năng Hệ Thống',
  '/customers': 'Quản lý Người dùng hệ thống',
}
</script>

<template>
  <div class="app-layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="logo">
          <span class="logo-text">Boost POS</span>
        </div>
      </div>

      <nav class="sidebar-nav">
        <router-link
          v-for="n in NAV"
          :key="n.to"
          :to="n.to"
          class="nav-item"
          :class="{ 'nav-item--active': route.path === n.to }"
        >
          <span class="nav-label">{{ n.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="status-indicator">
          <span class="dot"></span>
          Backend: Connected
        </div>
      </div>
    </aside>

    <div class="main-content">
      <header class="content-header">
        <div class="breadcrumb">
          <span class="breadcrumb-root">Dashboard</span>
          <span class="breadcrumb-sep">/</span>
          <span class="breadcrumb-current">{{ PAGE_TITLES[route.path] ?? 'Boost Demo' }}</span>
        </div>
        <div class="header-user">
          <div class="user-avatar">AD</div>
        </div>
      </header>

      <main class="page-container">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>

      <footer class="content-footer">
        <div class="api-info">
          Proxy: <code>/api</code> → <code>localhost:8080</code>
        </div>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
  background-color: #0b1020;
}

/* Sidebar */
.sidebar {
  width: 260px;
  background: rgba(15, 23, 42, 0.8);
  backdrop-filter: blur(20px);
  border-right: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
  z-index: 100;
}

.sidebar-header {
  padding: 32px 24px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  font-size: 24px;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.logo-text {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #fff;
}

.sidebar-nav {
  padding: 0 16px;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 12px;
  color: #94a3b8;
  text-decoration: none;
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 4px;
  transition: all 0.2s;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.03);
  color: #fff;
}

.nav-item--active {
  background: rgba(59, 130, 246, 0.1);
  color: #60a5fa;
  box-shadow: inset 0 0 0 1px rgba(59, 130, 246, 0.2);
}

.nav-icon {
  font-size: 18px;
}

.sidebar-footer {
  padding: 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #64748b;
}

.dot {
  width: 8px;
  height: 8px;
  background: #10b981;
  border-radius: 50%;
  box-shadow: 0 0 8px #10b981;
}

/* Main Content */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.content-header {
  height: 72px;
  padding: 0 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(11, 16, 32, 0.5);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  position: sticky;
  top: 0;
  z-index: 90;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.breadcrumb-root { color: #64748b; }
.breadcrumb-sep { color: #334155; }
.breadcrumb-current { color: #f1f5f9; font-weight: 600; }

.header-user {
  display: flex;
  align-items: center;
}

.user-avatar {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #475569 0%, #1e293b 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.page-container {
  padding: 40px;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
  flex: 1;
}

.content-footer {
  padding: 24px 40px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  font-size: 12px;
  color: #475569;
}

.api-info code {
  background: rgba(255, 255, 255, 0.05);
  padding: 2px 6px;
  border-radius: 4px;
  color: #94a3b8;
}

/* Page Transitions */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(5px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-5px);
}

@media (max-width: 1024px) {
  .sidebar { width: 80px; }
  .logo-text, .nav-label, .sidebar-footer { display: none; }
  .sidebar-header { padding: 24px 0; display: flex; justify-content: center; }
  .nav-item { justify-content: center; padding: 12px; }
  .page-container { padding: 24px; }
}
</style>

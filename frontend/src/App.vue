<script setup lang="ts">
import { computed, ref } from "vue";
import RewardsDemo from "./views/RewardsDemo.vue";
import UsersDemo from "./views/UsersDemo.vue";
import LoadTestDemo from "./views/LoadTestDemo.vue";

type Tab = "rewards" | "users" | "load";

const tab = ref<Tab>("rewards");
const title = computed(() => {
  if (tab.value === "rewards") return "Thanh toán / Rewards";
  if (tab.value === "users") return "Users API";
  return "Load test (DDoS giả lập)";
});
</script>

<template>
  <div class="app">
    <header class="topbar">
      <div class="brand">
        <div class="brand__title">Boost Demo</div>
      </div>

      <nav class="tabs" aria-label="Navigation tabs">
        <button
          class="tab"
          :data-active="tab === 'rewards'"
          @click="tab = 'rewards'"
        >
          Rewards
        </button>
        <button
          class="tab"
          :data-active="tab === 'users'"
          @click="tab = 'users'"
        >
          Users
        </button>
        <button class="tab" :data-active="tab === 'load'" @click="tab = 'load'">
          Load test
        </button>
      </nav>
    </header>

    <main class="main">
      <h1 class="page-title">{{ title }}</h1>
      <RewardsDemo v-if="tab === 'rewards'" />
      <UsersDemo v-else-if="tab === 'users'" />
      <LoadTestDemo v-else />
    </main>

    <footer class="footer">
      <div>
        Proxy dev: gọi <code>/api</code> và <code>/actuator</code> → backend
        (mặc định <code>localhost:8080</code>).
      </div>
    </footer>
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
  background: #0b1020;
  color: #e8ecff;
  font-family:
    system-ui,
    -apple-system,
    Segoe UI,
    Roboto,
    sans-serif;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  background: rgba(11, 16, 32, 0.8);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(232, 236, 255, 0.08);
}

.brand__title {
  font-weight: 700;
  letter-spacing: 0.3px;
}

.brand__sub {
  opacity: 0.75;
  font-size: 13px;
  margin-top: 2px;
}

.tabs {
  display: flex;
  gap: 8px;
  align-items: center;
}

.tab {
  appearance: none;
  border: 1px solid rgba(232, 236, 255, 0.18);
  background: rgba(232, 236, 255, 0.04);
  color: inherit;
  padding: 8px 12px;
  border-radius: 10px;
  cursor: pointer;
  font-weight: 600;
  font-size: 14px;
}

.tab[data-active="true"] {
  border-color: rgba(140, 170, 255, 0.55);
  background: rgba(140, 170, 255, 0.14);
}

.main {
  width: min(1100px, calc(100% - 40px));
  margin: 0 auto;
  padding: 22px 0 32px;
}

.page-title {
  font-size: 20px;
  margin: 0 0 14px;
}

.footer {
  width: min(1100px, calc(100% - 40px));
  margin: 0 auto;
  padding: 14px 0 24px;
  opacity: 0.7;
  font-size: 13px;
}

code {
  font-family:
    ui-monospace, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New",
    monospace;
  background: rgba(232, 236, 255, 0.08);
  border: 1px solid rgba(232, 236, 255, 0.12);
  padding: 2px 6px;
  border-radius: 8px;
}
</style>

<script setup lang="ts">
import type { TestStatus } from '@/types';

defineProps<{
  status: TestStatus;
}>();
</script>

<template>
  <div class="status-panel">
    <h3>Trạng Thái Kiểm Thử Đang Chạy</h3>
    
    <div v-if="status.phase1Running" class="progress-box">
      <div class="progress-info">
        <label>Giai Đoạn 1: Độ Chính Xác</label>
        <span>{{ Math.round((status.phase1Progress / 30) * 100) }}%</span>
      </div>
      <div class="progress-bar">
        <div class="fill" :style="{ width: (status.phase1Progress / 30 * 100) + '%' }"></div>
      </div>
      <small>{{ status.phase1Progress }} / 30 vòng lặp hoàn tất</small>
    </div>

    <div v-if="status.phase2Running" class="progress-box">
      <div class="progress-info">
        <label>Giai Đoạn 2: Hiệu Năng</label>
        <span>{{ Math.round((status.phase2Progress / 10) * 100) }}%</span>
      </div>
      <div class="progress-bar progress-bar--purple">
        <div class="fill" :style="{ width: (status.phase2Progress / 10 * 100) + '%' }"></div>
      </div>
      <small>{{ status.phase2Progress }} / 10 vòng lặp hoàn tất</small>
    </div>
  </div>
</template>

<style scoped>
.status-panel {
  background: linear-gradient(145deg, rgba(30, 41, 59, 0.8), rgba(15, 23, 42, 0.9));
  border: 1px solid rgba(56, 189, 248, 0.3);
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.3);
  animation: slideDown 0.3s ease-out;
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-20px); }
  to { opacity: 1; transform: translateY(0); }
}

h3 {
  margin: 0 0 20px;
  font-size: 15px;
  color: #38bdf8;
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.progress-box {
  margin-top: 20px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
}

.progress-info label {
  font-weight: 700;
  color: #f8fafc;
  font-size: 13px;
}

.progress-info span {
  font-family: monospace;
  color: #38bdf8;
  font-weight: 700;
}

.progress-bar {
  height: 12px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 8px;
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.2);
}

.progress-bar .fill {
  height: 100%;
  background: linear-gradient(90deg, #10b981, #34d399);
  box-shadow: 0 0 10px rgba(16, 185, 129, 0.5);
  transition: width 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.progress-bar--purple .fill {
  background: linear-gradient(90deg, #8b5cf6, #a78bfa);
  box-shadow: 0 0 10px rgba(139, 92, 246, 0.5);
}

small {
  color: #94a3b8;
  font-size: 11px;
}
</style>

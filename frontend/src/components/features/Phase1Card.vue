<script setup lang="ts">
import { onMounted, watch, nextTick, ref } from 'vue';
import { Chart, registerables } from 'chart.js';
import type { Phase1GroupSummary } from '@/types';
import BaseCard from '@/components/ui/BaseCard.vue';
import BaseButton from '@/components/ui/BaseButton.vue';

Chart.register(...registerables);

const props = defineProps<{
  summary: Record<string, Phase1GroupSummary> | null;
  isRunning: boolean;
}>();

defineEmits(['run']);

let charts: Record<string, Chart> = {};
const chartInstances = ref<Record<string, boolean>>({});

const execs = ['SINGLE', 'PLATFORM', 'VIRTUAL'];
const modes = ['LOCK', 'NO_LOCK'];

function getExecLabel(exec: string) {
  const labels: Record<string, string> = {
    'SINGLE': 'Single Thread (Serial)',
    'PLATFORM': 'Platform Threads (Fixed Pool)',
    'VIRTUAL': 'Virtual Threads (Loom)'
  };
  return labels[exec] || exec;
}

function drawCharts() {
  if (!props.summary) return;

  for (const exec of execs) {
    for (const mode of modes) {
      const key = `${exec}_${mode}`;
      const stats = props.summary[key];
      if (!stats || stats.total === 0) continue;

      const canvasId = `p1_pie_${key}`;
      const ctx = document.getElementById(canvasId) as HTMLCanvasElement;
      if (!ctx) continue;

      if (charts[key]) charts[key].destroy();

      charts[key] = new Chart(ctx, {
        type: 'doughnut',
        data: {
          labels: ['Chính xác', 'Sai lệch'],
          datasets: [{
            data: [stats.accurate, Math.max(0, stats.total - stats.accurate)],
            backgroundColor: [
              '#10b981', // Success Emerald
              '#ef4444'  // Error Red
            ],
            borderColor: 'rgba(15, 23, 42, 0.8)',
            borderWidth: 2,
            hoverOffset: 10,
            borderRadius: 4
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          cutout: '75%',
          animation: {
            animateRotate: true,
            animateScale: true,
            duration: 1000,
            easing: 'easeOutQuart'
          },
          plugins: { 
            legend: { display: false },
            tooltip: {
              backgroundColor: 'rgba(15, 23, 42, 0.9)',
              titleColor: '#fff',
              bodyColor: '#94a3b8',
              padding: 12,
              cornerRadius: 8,
              displayColors: true
            }
          }
        }
      });
      chartInstances.value[key] = true;
    }
  }
}

watch(() => props.summary, async (newVal) => {
  if (newVal) {
    await nextTick();
    drawCharts();
  } else {
    chartInstances.value = {};
  }
}, { deep: true });

onMounted(() => {
  if (props.summary) drawCharts();
});
</script>

<template>
  <BaseCard 
    class="premium-card"
    title="Kiểm Thử Độ Chính Xác (Accuracy)"
    subtitle="Thực hiện 5 giao dịch đồng thời cho mỗi mô hình để kiểm chứng khả năng bảo toàn dữ liệu."
  >
    <template #headerActions>
      <div class="card-actions">
        <BaseButton 
          variant="primary" 
          size="small" 
          @click="$emit('run')" 
          :disabled="isRunning"
          class="run-btn"
        >
          <span v-if="isRunning">Đang chạy...</span>
          <span v-else>Bắt đầu Test</span>
        </BaseButton>
      </div>
    </template>

    <div v-if="summary" class="summary-container">
      <div v-for="exec in execs" :key="exec" class="exec-card">
        <div class="exec-header">
          <div class="exec-icon">{{ exec === 'VIRTUAL' ? '⚡' : (exec === 'PLATFORM' ? '🏗️' : '🧵') }}</div>
          <h4 class="exec-title">{{ getExecLabel(exec) }}</h4>
        </div>

        <div class="modes-grid">
          <div v-for="mode in modes" :key="mode" class="mode-item" :class="{ 'fail': summary[`${exec}_${mode}`].percent < 100 }">
            <div class="mode-info">
              <span class="mode-name">{{ mode === 'LOCK' ? 'Mô hình LOCK' : 'Mô hình NO LOCK' }}</span>
              <span class="mode-tag" :class="mode.toLowerCase()">{{ mode }}</span>
            </div>

            <div class="visual-area">
              <div class="canvas-box">
                <canvas :id="'p1_pie_' + exec + '_' + mode"></canvas>
                <div class="center-metrics">
                  <div class="pct" :class="{ 'error': summary[`${exec}_${mode}`].percent < 100 }">
                    {{ Math.round(summary[`${exec}_${mode}`].percent) }}%
                  </div>
                  <div class="lbl">Accurate</div>
                </div>
              </div>
            </div>

            <div class="stats-footer">
              <div class="stat">
                <span class="val">{{ summary[`${exec}_${mode}`].avgDuration }}ms</span>
                <span class="key">Latency</span>
              </div>
              <div class="stat-divider"></div>
              <div class="stat">
                <span class="val">{{ summary[`${exec}_${mode}`].accurate }}/{{ summary[`${exec}_${mode}`].total }}</span>
                <span class="key">Passed</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <div class="empty-icon">🧪</div>
      <p>Sẵn sàng cho bài kiểm tra độ chính xác.</p>
      <span>Dữ liệu sẽ được thu thập trực tiếp từ Backend API qua 5 request đồng thời.</span>
    </div>
  </BaseCard>
</template>

<style scoped>
.premium-card {
  background: linear-gradient(165deg, rgba(30, 41, 59, 0.4), rgba(15, 23, 42, 0.6));
  border: 1px solid rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
}

.card-actions { display: flex; gap: 8px; }

.run-btn {
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.summary-container {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.exec-card {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.03);
  border-radius: 20px;
  padding: 24px;
}

.exec-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.exec-icon { font-size: 20px; }
.exec-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #f8fafc;
}

.modes-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.mode-item {
  background: rgba(15, 23, 42, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 16px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: all 0.3s ease;
}

.mode-item:hover {
  border-color: rgba(59, 130, 246, 0.3);
  background: rgba(15, 23, 42, 0.6);
  transform: translateY(-2px);
}

.mode-item.fail {
  border-color: rgba(239, 68, 68, 0.2);
}

.mode-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mode-name {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 600;
}

.mode-tag {
  font-size: 9px;
  font-weight: 800;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.05);
  color: #64748b;
}

.mode-tag.lock { color: #38bdf8; background: rgba(56, 189, 248, 0.1); }
.mode-tag.no_lock { color: #fb7185; background: rgba(251, 113, 133, 0.1); }

.visual-area {
  display: flex;
  justify-content: center;
  align-items: center;
}

.canvas-box {
  position: relative;
  width: 140px;
  height: 140px;
}

.center-metrics {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  pointer-events: none;
}

.pct {
  font-size: 24px;
  font-weight: 800;
  color: #10b981;
  line-height: 1;
}

.pct.error { color: #ef4444; }

.lbl {
  font-size: 9px;
  color: #64748b;
  text-transform: uppercase;
  margin-top: 2px;
}

.stats-footer {
  display: flex;
  justify-content: space-around;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 12px;
  padding: 10px;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat .val {
  font-size: 13px;
  font-weight: 700;
  color: #f1f5f9;
}

.stat .key {
  font-size: 9px;
  color: #64748b;
  text-transform: uppercase;
}

.stat-divider {
  width: 1px;
  background: rgba(255, 255, 255, 0.05);
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #64748b;
}

.empty-icon {
  font-size: 40px;
  margin-bottom: 16px;
  opacity: 0.3;
}

.empty-state span {
  display: block;
  font-size: 11px;
  margin-top: 8px;
  opacity: 0.7;
}

@media (max-width: 768px) {
  .modes-grid { grid-template-columns: 1fr; }
}
</style>

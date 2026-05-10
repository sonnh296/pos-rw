<script setup lang="ts">
import { onMounted, watch, nextTick } from 'vue';
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

const execs = ['SINGLE', 'PLATFORM', 'VIRTUAL'];
const modes = ['LOCK', 'NO_LOCK'];

function getExecLabel(exec: string) {
  const labels: Record<string, string> = {
    'SINGLE': 'Luồng Đơn (Single)',
    'PLATFORM': 'Luồng Nền tảng (Platform)',
    'VIRTUAL': 'Luồng Ảo (Virtual)'
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
            backgroundColor: ['#10b981', '#ef4444'],
            borderWidth: 0,
            hoverOffset: 4
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          cutout: '70%',
          plugins: { 
            legend: { 
              display: true, 
              position: 'bottom',
              labels: { color: '#94a3b8', boxWidth: 10, font: { size: 10 } }
            } 
          }
        }
      });
    }
  }
}

watch(() => props.summary, async () => {
  await nextTick();
  drawCharts();
}, { deep: true });

onMounted(() => {
  if (props.summary) drawCharts();
});
</script>

<template>
  <BaseCard 
    title="Giai Đoạn 1: Độ Chính Xác (Khóa vs Không Khóa)"
    subtitle="Frontend gọi API trực tiếp — 5 request đồng thời cho mỗi executor/mode. Kiểm chứng data consistency."
  >
    <template #headerActions>
      <div class="card-actions">
        <BaseButton variant="primary" size="small" @click="$emit('run')" :disabled="isRunning">Chạy Test</BaseButton>
      </div>
    </template>

    <div v-if="summary" class="summary-grid">
      <div v-for="exec in execs" :key="exec" class="exec-group">
        <h4 class="exec-title">{{ getExecLabel(exec) }}</h4>
        <div class="pie-container">
          <div v-for="mode in modes" :key="mode" class="pie-wrapper">
            <h5 class="mode-label">{{ mode === 'LOCK' ? 'CÓ KHÓA (LOCK)' : 'KHÔNG KHÓA (NO LOCK)' }}</h5>
            <div class="canvas-container">
              <canvas :id="'p1_pie_' + exec + '_' + mode"></canvas>
            </div>
            <div v-if="summary[`${exec}_${mode}`] && summary[`${exec}_${mode}`].total > 0" class="metrics-overlay">
              <div class="accuracy-val">{{ Math.round(summary[`${exec}_${mode}`].percent) }}%</div>
              <div class="duration-val">{{ summary[`${exec}_${mode}`].avgDuration }}ms</div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="empty-state">
      <p>Chưa có dữ liệu. Nhấn "Chạy Test" để gửi request trực tiếp đến API.</p>
    </div>
  </BaseCard>
</template>

<style scoped>
.card-actions { display: flex; gap: 8px; }

.summary-grid {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.exec-group {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  padding: 16px;
}

.exec-title {
  margin: 0 0 16px;
  font-size: 13px;
  color: #f1f5f9;
  text-align: center;
  border-bottom: 1px dashed rgba(255, 255, 255, 0.1);
  padding-bottom: 8px;
}

.pie-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.pie-wrapper {
  position: relative;
  height: 220px;
  display: flex;
  flex-direction: column;
}

.mode-label {
  font-size: 10px;
  color: #64748b;
  text-align: center;
  margin: 0 0 8px;
  letter-spacing: 0.05em;
}

.canvas-container {
  flex: 1;
  position: relative;
}

.metrics-overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -100%);
  text-align: center;
  pointer-events: none;
}

.accuracy-val {
  font-size: 18px;
  font-weight: 800;
  color: #10b981;
}

.duration-val {
  font-size: 11px;
  color: #94a3b8;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #64748b;
}

.empty-state .icon { font-size: 32px; margin-bottom: 12px; opacity: 0.5; }
</style>

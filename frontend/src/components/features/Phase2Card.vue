<script setup lang="ts">
import { onMounted, watch, nextTick } from 'vue';
import { Chart, registerables } from 'chart.js';
import type { Phase2Result } from '@/types';
import BaseCard from '@/components/ui/BaseCard.vue';
import BaseButton from '@/components/ui/BaseButton.vue';

Chart.register(...registerables);

const props = defineProps<{
  summary: Phase2Result['summary'] | null;
  isRunning: boolean;
}>();

defineEmits(['run', 'refresh']);

let throughputChart: Chart | null = null;
let p95Chart: Chart | null = null;

function drawCharts() {
  if (!props.summary || !props.summary['PLATFORM']) return;

  const plat = props.summary['PLATFORM'];
  const virt = props.summary['VIRTUAL'];

  const tCtx = document.getElementById('p2_throughput_chart') as HTMLCanvasElement;
  if (tCtx) {
    if (throughputChart) throughputChart.destroy();
    throughputChart = new Chart(tCtx, {
      type: 'bar',
      data: {
        labels: ['Platform Thread', 'Virtual Thread'],
        datasets: [{
          label: 'RPS',
          data: [plat.avgThroughput, virt.avgThroughput],
          backgroundColor: ['#3b82f6', '#8b5cf6'],
          borderRadius: 8
        }]
      },
      options: { 
        responsive: true, 
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#64748b' } },
          x: { grid: { display: false }, ticks: { color: '#f1f5f9' } }
        }
      }
    });
  }

  const pCtx = document.getElementById('p2_p95_chart') as HTMLCanvasElement;
  if (pCtx) {
    if (p95Chart) p95Chart.destroy();
    p95Chart = new Chart(pCtx, {
      type: 'bar',
      data: {
        labels: ['Platform Thread', 'Virtual Thread'],
        datasets: [{
          label: 'ms',
          data: [plat.avgP95, virt.avgP95],
          backgroundColor: ['#f59e0b', '#ec4899'],
          borderRadius: 8
        }]
      },
      options: { 
        responsive: true, 
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#64748b' } },
          x: { grid: { display: false }, ticks: { color: '#f1f5f9' } }
        }
      }
    });
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
    title="Giai Đoạn 2: Hiệu Năng (Platform vs Virtual Thread)"
    subtitle="Lặp lại 5 lần. Mỗi vòng lặp gửi 5000 request I/O đồng thời để so sánh throughput và latency."
  >
    <template #headerActions>
      <div class="card-actions">
        <BaseButton variant="primary" size="small" @click="$emit('run')" :disabled="isRunning">Chạy Test</BaseButton>
        <BaseButton variant="ghost" size="small" @click="$emit('refresh')">Làm Mới</BaseButton>
      </div>
    </template>

    <div v-if="summary && summary['PLATFORM']" class="charts-grid">
      <div class="chart-wrapper">
        <h4>Lưu lượng trung bình (RPS)</h4>
        <div class="canvas-container">
          <canvas id="p2_throughput_chart"></canvas>
        </div>
        <div class="chart-metrics">
          <div class="metric">
            <span class="label">Platform:</span>
            <span class="val">{{ Math.round(summary['PLATFORM'].avgThroughput) }}</span>
          </div>
          <div class="metric">
            <span class="label">Virtual:</span>
            <span class="val highlighted">{{ Math.round(summary['VIRTUAL'].avgThroughput) }}</span>
          </div>
        </div>
      </div>

      <div class="chart-wrapper">
        <h4>Độ Trễ P95 trung bình (ms)</h4>
        <div class="canvas-container">
          <canvas id="p2_p95_chart"></canvas>
        </div>
        <div class="chart-metrics">
          <div class="metric">
            <span class="label">Platform:</span>
            <span class="val">{{ Math.round(summary['PLATFORM'].avgP95) }}ms</span>
          </div>
          <div class="metric">
            <span class="label">Virtual:</span>
            <span class="val highlighted-warn">{{ Math.round(summary['VIRTUAL'].avgP95) }}ms</span>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="empty-state">
      <p>Chưa có dữ liệu. Hãy chạy kiểm thử để so sánh hiệu năng.</p>
    </div>
  </BaseCard>
</template>

<style scoped>
.card-actions { display: flex; gap: 8px; }

.charts-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.chart-wrapper {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

h4 {
  margin: 0 0 20px;
  font-size: 14px;
  color: #94a3b8;
  text-align: center;
}

.canvas-container {
  height: 240px;
  position: relative;
  margin-bottom: 16px;
}

.chart-metrics {
  display: flex;
  justify-content: center;
  gap: 20px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.metric {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.metric .label { font-size: 10px; color: #64748b; text-transform: uppercase; }
.metric .val { font-size: 16px; font-weight: 700; color: #f1f5f9; }
.metric .val.highlighted { color: #8b5cf6; }
.metric .val.highlighted-warn { color: #ec4899; }

.empty-state {
  text-align: center;
  padding: 40px;
  color: #64748b;
}

.empty-state .icon { font-size: 32px; margin-bottom: 12px; opacity: 0.5; }

@media (max-width: 800px) {
  .charts-grid { grid-template-columns: 1fr; }
}
</style>

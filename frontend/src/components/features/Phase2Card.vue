<script setup lang="ts">
import { computed, onMounted, watch, nextTick } from 'vue';
import { Chart, registerables } from 'chart.js';
import type { Phase2RampSeries, Phase2Result } from '@/types';
import BaseCard from '@/components/ui/BaseCard.vue';
import BaseButton from '@/components/ui/BaseButton.vue';

Chart.register(...registerables);

const props = defineProps<{
  summary: Phase2Result['summary'] | null;
  byRps?: Phase2RampSeries;
  isRunning: boolean;
}>();

defineEmits(['run', 'refresh']);

const peakRps = computed(() => {
  if (props.byRps?.rpsLevels?.length) {
    return props.byRps.rpsLevels[props.byRps.rpsLevels.length - 1];
  }
  return null;
});

const peakComparison = computed(() => {
  if (!props.byRps?.PLATFORM?.length || !props.byRps?.VIRTUAL?.length) return null;
  const p = props.byRps.PLATFORM[props.byRps.PLATFORM.length - 1];
  const v = props.byRps.VIRTUAL[props.byRps.VIRTUAL.length - 1];
  return { p, v };
});

const virtualUplift = computed(() => {
  const peak = peakComparison.value;
  if (peak) {
    return peak.p.throughputRps > 0
      ? Math.round(((peak.v.throughputRps - peak.p.throughputRps) / peak.p.throughputRps) * 100)
      : 0;
  }
  if (!props.summary?.['PLATFORM'] || !props.summary?.['VIRTUAL']) return 0;
  const p = props.summary['PLATFORM'].avgThroughput;
  const v = props.summary['VIRTUAL'].avgThroughput;
  return p > 0 ? Math.round(((v - p) / p) * 100) : 0;
});

const p99Improvement = computed(() => {
  const peak = peakComparison.value;
  if (peak && peak.p.p99Ms > 0 && peak.v.p99Ms < peak.p.p99Ms) {
    return Math.round(((peak.p.p99Ms - peak.v.p99Ms) / peak.p.p99Ms) * 100);
  }
  return 0;
});

let throughputChart: Chart | null = null;
let latencyChart: Chart | null = null;

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { labels: { color: '#94a3b8' } } },
  scales: {
    y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#64748b' } },
    x: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#f1f5f9' } },
  },
};

function drawCharts() {
  if (!props.summary?.['PLATFORM']) return;

  const tCtx = document.getElementById('p2_throughput_chart') as HTMLCanvasElement;
  const lCtx = document.getElementById('p2_latency_chart') as HTMLCanvasElement;
  if (!tCtx || !lCtx) return;

  if (throughputChart) throughputChart.destroy();
  if (latencyChart) latencyChart.destroy();

  if (props.byRps?.rpsLevels?.length) {
    const labels = props.byRps.rpsLevels.map(r => `${r}`);
    throughputChart = new Chart(tCtx, {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: 'Platform',
            data: props.byRps.PLATFORM.map(p => p.throughputRps),
            borderColor: '#3b82f6',
            backgroundColor: 'rgba(59, 130, 246, 0.15)',
            tension: 0.2,
          },
          {
            label: 'Virtual',
            data: props.byRps.VIRTUAL.map(p => p.throughputRps),
            borderColor: '#8b5cf6',
            backgroundColor: 'rgba(139, 92, 246, 0.15)',
            tension: 0.2,
          },
        ],
      },
      options: {
        ...chartOptions,
        plugins: { ...chartOptions.plugins, title: { display: true, text: 'Throughput (RPS)', color: '#94a3b8' } },
      },
    });

    latencyChart = new Chart(lCtx, {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: 'Platform P99',
            data: props.byRps.PLATFORM.map(p => p.p99Ms),
            borderColor: '#f59e0b',
            tension: 0.2,
          },
          {
            label: 'Virtual P99',
            data: props.byRps.VIRTUAL.map(p => p.p99Ms),
            borderColor: '#ec4899',
            tension: 0.2,
          },
        ],
      },
      options: {
        ...chartOptions,
        plugins: { ...chartOptions.plugins, title: { display: true, text: 'P99 latency (ms)', color: '#94a3b8' } },
      },
    });
    return;
  }

  const plat = props.summary['PLATFORM'];
  const virt = props.summary['VIRTUAL'];

  throughputChart = new Chart(tCtx, {
    type: 'bar',
    data: {
      labels: ['Platform', 'Virtual'],
      datasets: [{
        label: 'RPS',
        data: [plat.avgThroughput, virt.avgThroughput],
        backgroundColor: ['#3b82f6', '#8b5cf6'],
        borderRadius: 8,
      }],
    },
    options: { ...chartOptions, plugins: { legend: { display: false } } },
  });

  latencyChart = new Chart(lCtx, {
    type: 'bar',
    data: {
      labels: ['Platform', 'Virtual'],
      datasets: [{
        label: 'P95 ms',
        data: [plat.avgP95, virt.avgP95],
        backgroundColor: ['#f59e0b', '#ec4899'],
        borderRadius: 8,
      }],
    },
    options: { ...chartOptions, plugins: { legend: { display: false } } },
  });
}

watch([() => props.summary, () => props.byRps], async () => {
  await nextTick();
  drawCharts();
}, { deep: true });

onMounted(() => {
  if (props.summary) drawCharts();
});
</script>

<template>
  <BaseCard title="Giai Đoạn 2: Hiệu Năng (Platform vs Virtual Thread)">
    <template #headerActions>
      <div class="card-actions">
        <BaseButton variant="ghost" size="small" @click="$emit('refresh')">Làm Mới</BaseButton>
      </div>
    </template>

    <p v-if="summary && summary['PLATFORM']" class="win-banner">
      Virtual thread:
      <strong>{{ virtualUplift }}%</strong> throughput
      <span v-if="p99Improvement > 0"> · p99 thấp hơn {{ p99Improvement }}% ở {{ peakRps }} RPS</span>
      <span class="hint">{{ byRps ? '(ramp theo concurrency)' : '(trung bình CSV)' }}</span>
    </p>

    <div v-if="summary && summary['PLATFORM']" class="charts-grid">
      <div class="chart-wrapper">
        <h4>{{ byRps ? 'Throughput theo RPS' : 'Lưu lượng trung bình (RPS)' }}</h4>
        <div class="canvas-container">
          <canvas id="p2_throughput_chart"></canvas>
        </div>
      </div>

      <div class="chart-wrapper">
        <h4>{{ byRps ? 'P99 theo RPS' : 'Độ trễ P95 trung bình (ms)' }}</h4>
        <div class="canvas-container">
          <canvas id="p2_latency_chart"></canvas>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <p>Chưa có dữ liệu Phase 2.</p>
      <p class="run-hint">
        Chạy load test (Docker + JMeter trên máy host):
      </p>
      <p class="run-hint"><code>docker compose up -d --build</code></p>
      <p class="run-hint"><code>cd loadtest/script_run && ./run-phase2-http-concurrency.sh</code></p>
      <p class="run-hint">Hoặc full suite: <code>./run-performance-suite.sh</code></p>
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
  height: 280px;
  position: relative;
}

.win-banner {
  margin: 0 0 16px;
  padding: 12px 16px;
  border-radius: 8px;
  background: rgba(139, 92, 246, 0.12);
  border: 1px solid rgba(139, 92, 246, 0.35);
  color: #c4b5fd;
  font-size: 14px;
}
.win-banner strong { color: #a78bfa; }
.win-banner .hint { color: #64748b; font-size: 12px; }

.empty-state {
  text-align: center;
  padding: 40px;
  color: #64748b;
}

.run-hint {
  margin: 8px 0 0;
  font-size: 13px;
}

.run-hint code {
  font-size: 12px;
  color: #94a3b8;
}

@media (max-width: 800px) {
  .charts-grid { grid-template-columns: 1fr; }
}
</style>

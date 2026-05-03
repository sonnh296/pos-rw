<template>
  <div class="custom-tests-container">
    <div class="actions">
      <button v-if="status && (status.phase1Running || status.phase2Running)" @click="stopTests" class="btn warning" :disabled="loading">
        <span class="icon">🛑</span> Dừng Test
      </button>
      <button @click="clearResults" class="btn danger" :disabled="loading || (status && (status.phase1Running || status.phase2Running))">
        <span class="icon">🗑️</span> Xóa Dữ Liệu
      </button>
    </div>

    <!-- Trạng thái tiến trình chạy ngầm -->
    <div v-if="status && (status.phase1Running || status.phase2Running)" class="status-panel">
      <h3>Trạng Thái Kiểm Thử</h3>
      
      <div v-if="status.phase1Running" class="progress-box">
        <label>Đang chạy Giai Đoạn 1 ({{ Math.round((status.phase1Progress / 30) * 100) }}%)</label>
        <div class="progress-bar">
          <div class="fill" :style="{ width: (status.phase1Progress / 30 * 100) + '%' }"></div>
        </div>
        <small>{{ status.phase1Progress }} / 30 vòng lặp</small>
      </div>

      <div v-if="status.phase2Running" class="progress-box">
        <label>Đang chạy Giai Đoạn 2 ({{ Math.round((status.phase2Progress / 10) * 100) }}%)</label>
        <div class="progress-bar">
          <div class="fill" :style="{ width: (status.phase2Progress / 10 * 100) + '%' }"></div>
        </div>
        <small>{{ status.phase2Progress }} / 10 vòng lặp</small>
      </div>
    </div>

    <div class="test-sections">
      <!-- Giai Đoạn 1: Độ Chính Xác -->
      <div class="test-card">
        <div class="card-header">
          <h2>Giai Đoạn 1: Độ Chính Xác (Khóa vs Không Khóa)</h2>
          <p>Lặp lại 5 lần cho mỗi mô hình Thread. Mỗi vòng lặp gửi đồng thời 5 giao dịch với số tiền ngẫu nhiên để kiểm chứng độ chính xác khi đối soát.</p>
        </div>
        
        <div class="controls">
          <button class="btn primary" @click="runPhase1" :disabled="status?.phase1Running">
            ▶ Chạy Kiểm Thử Giai Đoạn 1
          </button>
          <button class="btn secondary" @click="fetchPhase1">
            ↻ Làm Mới Dữ Liệu
          </button>
        </div>
        
        <div v-if="phase1Summary" class="charts-grid-p1">
          <div v-for="exec in ['SINGLE', 'PLATFORM', 'VIRTUAL']" :key="exec" class="exec-group">
            <h4 class="exec-title">{{ getExecLabel(exec) }}</h4>
            <div class="pie-container">
              <div class="pie-chart-wrapper">
                <h5>CÓ KHÓA (LOCK)</h5>
                <div class="canvas-container">
                  <canvas :id="'p1_pie_' + exec + '_LOCK'"></canvas>
                </div>
                <div class="metrics-text" v-if="phase1Summary[`${exec}_LOCK`]">
                  Chính xác: {{ phase1Summary[`${exec}_LOCK`].accurate }} | Sai lệch: {{ Math.max(0, phase1Summary[`${exec}_LOCK`].total - phase1Summary[`${exec}_LOCK`].accurate) }}
                </div>
              </div>
              <div class="pie-chart-wrapper">
                <h5>KHÔNG KHÓA (NO LOCK)</h5>
                <div class="canvas-container">
                  <canvas :id="'p1_pie_' + exec + '_NO_LOCK'"></canvas>
                </div>
                <div class="metrics-text" v-if="phase1Summary[`${exec}_NO_LOCK`]">
                  Chính xác: {{ phase1Summary[`${exec}_NO_LOCK`].accurate }} | Sai lệch: {{ Math.max(0, phase1Summary[`${exec}_NO_LOCK`].total - phase1Summary[`${exec}_NO_LOCK`].accurate) }}
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">Chưa có dữ liệu. Hãy chạy kiểm thử.</div>
      </div>

      <!-- Giai Đoạn 2: Hiệu Năng -->
      <div class="test-card">
        <div class="card-header">
          <h2>Giai Đoạn 2: Hiệu Năng (Platform vs Virtual Thread)</h2>
          <p>Lặp lại 5 lần. Mỗi vòng lặp ném 5000 request I/O đồng thời để so sánh thời gian thực thi, Throughput (RPS) và P95 Latency.</p>
        </div>
        
        <div class="controls">
          <button class="btn primary" @click="runPhase2" :disabled="status?.phase2Running">
            ▶ Chạy Kiểm Thử Giai Đoạn 2
          </button>
          <button class="btn secondary" @click="fetchPhase2">
            ↻ Làm Mới Dữ Liệu
          </button>
        </div>
        
        <div v-if="phase2Summary" class="charts-grid-p2">
          <div class="bar-chart-wrapper">
            <h4>Lưu lượng trung bình (RPS)</h4>
            <div class="canvas-container">
              <canvas id="p2_throughput_chart"></canvas>
            </div>
          </div>
          <div class="bar-chart-wrapper">
            <h4>Độ Trễ P95 trung bình (ms)</h4>
            <div class="canvas-container">
              <canvas id="p2_p95_chart"></canvas>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">Chưa có dữ liệu. Hãy chạy kiểm thử.</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue';
import { Chart, registerables } from 'chart.js';
Chart.register(...registerables);

const loading = ref(false);
const status = ref<any>(null);
const phase1Summary = ref<any>(null);
const phase2Summary = ref<any>(null);

let pollInterval: any = null;
let p1Charts: Record<string, Chart> = {};
let p2ThroughputChart: Chart | null = null;
let p2P95Chart: Chart | null = null;

const getExecLabel = (exec: string) => {
  if (exec === 'SINGLE') return 'Luồng Đơn (Single)';
  if (exec === 'PLATFORM') return 'Luồng Nền tảng (Platform)';
  if (exec === 'VIRTUAL') return 'Luồng Ảo (Virtual)';
  return exec;
};

onMounted(() => {
  fetchStatus();
  fetchPhase1();
  fetchPhase2();
  pollInterval = setInterval(() => {
    fetchStatus();
    if (status.value && (status.value.phase1Running || status.value.phase2Running)) {
      if (status.value.phase1Running && status.value.phase1Progress % 1000 === 0) fetchPhase1();
      if (status.value.phase2Running && status.value.phase2Progress % 100 === 0) fetchPhase2();
    }
  }, 2000);
});

onUnmounted(() => {
  if (pollInterval) clearInterval(pollInterval);
});

async function fetchStatus() {
  try {
    const res = await fetch('/api/custom-tests/status');
    status.value = await res.json();
  } catch (err) {
    console.error(err);
  }
}

async function stopTests() {
  loading.value = true;
  try {
    await fetch('/api/custom-tests/stop', { method: 'POST' });
    alert('Đã gửi yêu cầu dừng test!');
  } catch (err) {
    console.error(err);
  } finally {
    loading.value = false;
  }
}

async function clearResults() {
  if (!confirm("Bạn có chắc chắn muốn xóa toàn bộ dữ liệu kiểm thử không?")) return;
  loading.value = true;
  try {
    await fetch('/api/custom-tests/clear', { method: 'POST' });
    phase1Summary.value = null;
    phase2Summary.value = null;
  } catch (err) {
    console.error(err);
  } finally {
    loading.value = false;
  }
}

async function runPhase1() {
  try {
    await fetch(`/api/custom-tests/run-phase1?iterations=5`, { method: 'POST' });
    fetchStatus();
  } catch (err) {
    console.error(err);
  }
}

async function runPhase2() {
  try {
    await fetch(`/api/custom-tests/run-phase2?iterations=5&totalRequestsPerIter=5000`, { method: 'POST' });
    fetchStatus();
  } catch (err) {
    console.error(err);
  }
}

async function fetchPhase1() {
  try {
    const res = await fetch('/api/custom-tests/results/phase1');
    const data = await res.json();
    if (Object.keys(data.summary).length > 0) {
      phase1Summary.value = data.summary;
      await nextTick();
      drawPhase1Charts();
    }
  } catch (err) {
    console.error(err);
  }
}

async function fetchPhase2() {
  try {
    const res = await fetch('/api/custom-tests/results/phase2');
    const data = await res.json();
    if (data.summary && Object.keys(data.summary).length > 0) {
      phase2Summary.value = data.summary;
      await nextTick();
      drawPhase2Charts();
    }
  } catch (err) {
    console.error(err);
  }
}

function drawPhase1Charts() {
  if (!phase1Summary.value) return;
  const execs = ['SINGLE', 'PLATFORM', 'VIRTUAL'];
  const modes = ['LOCK', 'NO_LOCK'];

  for (const exec of execs) {
    for (const mode of modes) {
      const key = `${exec}_${mode}`;
      const stats = phase1Summary.value[key];
      if (!stats) continue;

      const canvasId = `p1_pie_${key}`;
      const ctx = document.getElementById(canvasId) as HTMLCanvasElement;
      if (!ctx) continue;

      if (p1Charts[key]) p1Charts[key].destroy();

      p1Charts[key] = new Chart(ctx, {
        type: 'pie',
        data: {
          labels: ['Chính xác', 'Sai lệch'],
          datasets: [{
            data: [stats.accurate, Math.max(0, stats.total - stats.accurate)],
            backgroundColor: ['#10b981', '#ef4444'], // Green & Red
            borderWidth: 0
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: { 
            legend: { 
              display: true, 
              position: 'bottom',
              labels: { color: '#e2e8f0', boxWidth: 12, padding: 10 }
            } 
          }
        }
      });
    }
  }
}

function drawPhase2Charts() {
  if (!phase2Summary.value || !phase2Summary.value['PLATFORM']) return;
  const plat = phase2Summary.value['PLATFORM'];
  const virt = phase2Summary.value['VIRTUAL'];

  const tCtx = document.getElementById('p2_throughput_chart') as HTMLCanvasElement;
  if (tCtx) {
    if (p2ThroughputChart) p2ThroughputChart.destroy();
    p2ThroughputChart = new Chart(tCtx, {
      type: 'bar',
      data: {
        labels: [
          ['Platform Thread', Math.round(plat.avgThroughput) + ' RPS'], 
          ['Virtual Thread', Math.round(virt.avgThroughput) + ' RPS']
        ],
        datasets: [{
          label: 'Throughput (RPS)',
          data: [plat.avgThroughput, virt.avgThroughput],
          backgroundColor: ['#3b82f6', '#8b5cf6'],
          borderRadius: 6
        }]
      },
      options: { 
        responsive: true, 
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#94a3b8' } },
          x: { grid: { display: false }, ticks: { color: '#e2e8f0' } }
        }
      }
    });
  }

  const pCtx = document.getElementById('p2_p95_chart') as HTMLCanvasElement;
  if (pCtx) {
    if (p2P95Chart) p2P95Chart.destroy();
    p2P95Chart = new Chart(pCtx, {
      type: 'bar',
      data: {
        labels: [
          ['Platform Thread', Math.round(plat.avgP95) + ' ms'], 
          ['Virtual Thread', Math.round(virt.avgP95) + ' ms']
        ],
        datasets: [{
          label: 'P95 Latency (ms)',
          data: [plat.avgP95, virt.avgP95],
          backgroundColor: ['#f59e0b', '#ec4899'],
          borderRadius: 6
        }]
      },
      options: { 
        responsive: true, 
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#94a3b8' } },
          x: { grid: { display: false }, ticks: { color: '#e2e8f0' } }
        }
      }
    });
  }
}
</script>

<style scoped>
.custom-tests-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
  animation: fadeIn 0.4s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.actions {
  display: flex;
  justify-content: flex-end;
}

.status-panel {
  background: linear-gradient(145deg, rgba(30, 41, 59, 0.8), rgba(15, 23, 42, 0.9));
  border: 1px solid rgba(148, 163, 184, 0.15);
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 10px 25px rgba(0,0,0,0.2);
}

.status-panel h3 {
  margin: 0 0 16px;
  font-size: 1.1rem;
  color: #38bdf8;
}

.progress-box {
  margin-top: 16px;
}

.progress-box label {
  font-weight: 600;
  color: #f8fafc;
  font-size: 0.9rem;
}

.progress-bar {
  height: 10px;
  background: rgba(255,255,255,0.1);
  border-radius: 5px;
  overflow: hidden;
  margin: 8px 0;
  box-shadow: inset 0 2px 4px rgba(0,0,0,0.2);
}

.progress-bar .fill {
  height: 100%;
  background: linear-gradient(90deg, #10b981, #34d399);
  transition: width 0.3s ease;
}

.progress-box small {
  color: #94a3b8;
}

.test-sections {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.test-card {
  background: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 28px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  box-shadow: 0 15px 35px rgba(0,0,0,0.15);
}

.card-header h2 {
  margin: 0 0 8px;
  font-size: 1.4rem;
  color: #f1f5f9;
}

.card-header p {
  margin: 0;
  color: #94a3b8;
  font-size: 0.95rem;
  line-height: 1.5;
}

.controls {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.btn {
  padding: 10px 20px;
  border-radius: 8px;
  border: none;
  font-weight: 600;
  font-size: 0.95rem;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none !important;
}

.btn:active:not(:disabled) {
  transform: scale(0.96);
}

.btn.primary { 
  background: #3b82f6; 
  color: white; 
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.btn.primary:hover:not(:disabled) {
  background: #2563eb;
}

.btn.secondary { 
  background: #475569; 
  color: white; 
}

.btn.secondary:hover:not(:disabled) {
  background: #334155;
}

.btn.warning { 
  background: #f59e0b; 
  color: white; 
}

.btn.warning:hover:not(:disabled) {
  background: #d97706;
}

.btn.danger { 
  background: #ef4444; 
  color: white; 
}

.btn.danger:hover:not(:disabled) {
  background: #dc2626;
}

.charts-grid-p1 {
  display: flex;
  flex-direction: column;
  gap: 32px;
  margin-top: 16px;
}

.exec-group {
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(255,255,255,0.05);
  padding: 20px;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.exec-title {
  text-align: center;
  margin: 0;
  font-size: 1.1rem;
  color: #e2e8f0;
  padding-bottom: 12px;
  border-bottom: 1px dashed rgba(255,255,255,0.1);
}

.pie-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.pie-chart-wrapper {
  height: 280px;
  position: relative;
  text-align: center;
  display: flex;
  flex-direction: column;
}

.canvas-container {
  flex: 1;
  position: relative;
  min-height: 0;
  width: 100%;
}

.pie-chart-wrapper h5 {
  margin: 0 0 12px;
  font-size: 0.8rem;
  color: #94a3b8;
  letter-spacing: 0.5px;
}

.charts-grid-p2 {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 32px;
  margin-top: 16px;
}

.bar-chart-wrapper {
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(255,255,255,0.05);
  padding: 24px;
  border-radius: 12px;
  height: 350px;
  position: relative;
  display: flex;
  flex-direction: column;
}

.bar-chart-wrapper h4 {
  margin: 0 0 20px;
  font-size: 1.1rem;
  color: #e2e8f0;
  text-align: center;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #64748b;
  font-style: italic;
  background: rgba(0,0,0,0.1);
  border-radius: 8px;
}

.metrics-text {
  margin-top: 12px;
  font-size: 0.95rem;
  color: #e2e8f0;
  font-weight: 500;
  text-align: center;
  padding: 6px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 6px;
}
</style>

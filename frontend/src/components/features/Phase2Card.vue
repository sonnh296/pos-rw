<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue';
import { apiFetch } from '@/api/client';
import BaseCard from '@/components/ui/BaseCard.vue';
import BaseButton from '@/components/ui/BaseButton.vue';

const isRunning = ref(false);
const logs = ref<string[]>([]);
const summary = ref<any>(null);
const grafanaUrl = 'http://localhost:3000/d/thread-comparison';

let pollInterval: any = null;

async function fetchStatus() {
  const res = await apiFetch<{ running: boolean }>('/api/k6/status');
  if (res.ok) {
    const wasRunning = isRunning.value;
    isRunning.value = res.data.running;
    
    if (isRunning.value) {
      fetchLogs();
    } else if (wasRunning) {
      // Test just finished, fetch summary
      fetchSummary();
    }
  }
}

async function fetchLogs() {
  const res = await apiFetch<string[]>('/api/k6/logs');
  if (res.ok) {
    logs.value = res.data;
    nextTick(() => {
      const term = document.getElementById('k6-terminal');
      if (term) term.scrollTop = term.scrollHeight;
    });
  }
}

async function fetchSummary() {
  const res = await apiFetch<any>('/api/k6/summary');
  if (res.ok && res.data) {
    summary.value = res.data;
  }
}

async function runBurstTest() {
  if (isRunning.value) return;
  logs.value = ['Initializing Burst Test (2000 VUs)...'];
  summary.value = null;
  const res = await apiFetch('/api/k6/run', {
    method: 'POST',
    body: JSON.stringify({ script: 'phase2-burst.js' })
  });
  if (res.ok) {
    isRunning.value = true;
  }
}

async function stopTest() {
  await apiFetch('/api/k6/stop', { method: 'POST' });
  fetchStatus();
}

onMounted(() => {
  fetchStatus();
  fetchSummary(); // Check if there's an existing summary
  pollInterval = setInterval(fetchStatus, 2000);
});

onUnmounted(() => {
  if (pollInterval) clearInterval(pollInterval);
});

function openGrafana() {
  window.open(grafanaUrl, '_blank');
}
</script>

<template>
  <BaseCard 
    class="k6-dashboard-card"
    title="Giai Đoạn 2: Burst Load Test (2000 VUs)"
    subtitle="Ném đồng thời 2000 người dùng ảo để so sánh trực tiếp Platform vs Virtual Thread."
  >
    <template #headerActions>
      <BaseButton variant="ghost" size="small" @click="openGrafana" icon="external-link">
        Grafana Monitoring
      </BaseButton>
    </template>

    <div class="dashboard-content">
      <!-- Test Description -->
      <div class="test-info">
        <div class="info-item">
          <span class="label">Người dùng ảo:</span>
          <span class="value highlight">2,000 VUs</span>
        </div>
        <div class="info-item">
          <span class="label">Mô hình:</span>
          <span class="value">Platform vs Virtual</span>
        </div>
        <div class="info-item">
          <span class="label">Loại tải:</span>
          <span class="value">Constant Load (Burst)</span>
        </div>
      </div>

      <!-- Result Summary (Hidden while running) -->
      <Transition name="fade">
        <div v-if="summary && !isRunning" class="result-summary-card">
          <div class="summary-header">
            <h4>🏆 Kết quả kiểm thử gần nhất</h4>
            <span class="total-reqs">Tổng request: {{ summary.total_requests.toLocaleString() }}</span>
          </div>
          
          <div class="comparison-grid">
            <!-- Platform Results -->
            <div class="result-col platform">
              <div class="col-head">Platform Threads</div>
              <div class="metric">
                <span class="m-val">{{ Math.round(summary.platform.rps) }}</span>
                <span class="m-lbl">RPS (Throughput)</span>
              </div>
              <div class="metric">
                <span class="m-val">{{ Math.round(summary.platform.p95) }}ms</span>
                <span class="m-lbl">P95 Latency</span>
              </div>
            </div>

            <div class="vs-divider">VS</div>

            <!-- Virtual Results -->
            <div class="result-col virtual">
              <div class="col-head">Virtual Threads</div>
              <div class="metric">
                <span class="m-val">{{ Math.round(summary.virtual.rps) }}</span>
                <span class="m-lbl">RPS (Throughput)</span>
              </div>
              <div class="metric">
                <span class="m-val">{{ Math.round(summary.virtual.p95) }}ms</span>
                <span class="m-lbl">P95 Latency</span>
              </div>
            </div>
          </div>

          <div class="insight">
            💡 <strong>Nhận xét:</strong> 
            {{ summary.virtual.rps > summary.platform.rps * 1.5 ? 'Virtual Threads cho thấy hiệu năng vượt trội khi xử lý tải Burst.' : 'Cả hai mô hình đều xử lý tốt mức tải này.' }}
          </div>
        </div>
      </Transition>

      <!-- Action -->
      <div class="main-action">
        <BaseButton 
          v-if="!isRunning" 
          variant="primary" 
          size="large"
          @click="runBurstTest" 
          class="burst-btn"
        >
          🚀 {{ summary ? 'Chạy lại Burst Test' : 'Bắt đầu Burst Test (2000 VUs)' }}
        </BaseButton>
        <BaseButton 
          v-else 
          variant="danger" 
          size="large"
          @click="stopTest" 
          class="stop-btn"
        >
          🛑 Dừng Kiểm Thử
        </BaseButton>
      </div>

      <!-- Live Terminal -->
      <div class="monitor-area">
        <div class="terminal-header">
          <div class="dots"><span></span><span></span><span></span></div>
          <div class="title">k6-burst-output.log</div>
          <div class="status-badge" :class="{ 'active': isRunning }">
            {{ isRunning ? 'TESTING' : 'READY' }}
          </div>
        </div>
        <div id="k6-terminal" class="terminal-body">
          <div v-for="(log, idx) in logs" :key="idx" class="log-line">
            <span class="ln">{{ idx + 1 }}</span>
            <span class="txt">{{ log }}</span>
          </div>
          <div v-if="logs.length === 0" class="empty-term">
            Sẵn sàng để ném 2000 VUs...
          </div>
        </div>
      </div>
    </div>
  </BaseCard>
</template>

<style scoped>
.k6-dashboard-card {
  background: linear-gradient(165deg, rgba(30, 41, 59, 0.4), rgba(15, 23, 42, 0.6));
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.dashboard-content { display: flex; flex-direction: column; gap: 24px; }

.test-info {
  display: flex;
  justify-content: space-around;
  background: rgba(255, 255, 255, 0.02);
  padding: 16px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.info-item { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.info-item .label { font-size: 10px; text-transform: uppercase; color: #64748b; }
.info-item .value { font-size: 14px; font-weight: 700; color: #f1f5f9; }
.info-item .value.highlight { color: #38bdf8; }

/* Result Summary Card */
.result-summary-card {
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.1), rgba(139, 92, 246, 0.1));
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 16px;
  padding: 24px;
}

.summary-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.summary-header h4 { margin: 0; font-size: 16px; color: #f1f5f9; }
.total-reqs { font-size: 11px; color: #64748b; }

.comparison-grid {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}

.result-col {
  flex: 1;
  text-align: center;
  padding: 16px;
  background: rgba(15, 23, 42, 0.4);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.col-head {
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 16px;
  color: #94a3b8;
}

.result-col.platform .col-head { color: #f59e0b; }
.result-col.virtual .col-head { color: #10b981; }

.metric { display: flex; flex-direction: column; margin-bottom: 12px; }
.metric:last-child { margin-bottom: 0; }
.m-val { font-size: 20px; font-weight: 800; color: #fff; }
.m-lbl { font-size: 9px; text-transform: uppercase; color: #64748b; }

.vs-divider {
  font-weight: 900;
  font-style: italic;
  color: #475569;
  font-size: 18px;
}

.insight {
  font-size: 12px;
  color: #94a3b8;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  text-align: center;
}

.main-action { display: flex; justify-content: center; }
.burst-btn { width: 100%; max-width: 400px; height: 54px; font-weight: 800; }

/* Terminal */
.monitor-area { background: #0f172a; border-radius: 12px; overflow: hidden; border: 1px solid rgba(255, 255, 255, 0.05); }
.terminal-header { background: #1e293b; padding: 10px 16px; display: flex; align-items: center; justify-content: space-between; }
.dots { display: flex; gap: 6px; }
.dots span { width: 10px; height: 10px; border-radius: 50%; background: #334155; }
.dots span:nth-child(1) { background: #ef4444; }
.dots span:nth-child(2) { background: #f59e0b; }
.dots span:nth-child(3) { background: #10b981; }
.terminal-header .title { font-size: 11px; font-family: monospace; color: #94a3b8; }
.status-badge { font-size: 10px; font-weight: 800; padding: 2px 8px; border-radius: 4px; color: #64748b; background: rgba(255, 255, 255, 0.05); }
.status-badge.active { background: rgba(56, 189, 248, 0.1); color: #38bdf8; animation: pulse 2s infinite; }
@keyframes pulse { 0% { opacity: 1; } 50% { opacity: 0.5; } 100% { opacity: 1; } }

.terminal-body { height: 180px; overflow-y: auto; padding: 16px; font-family: 'JetBrains Mono', monospace; font-size: 12px; line-height: 1.6; }
.log-line { display: flex; gap: 12px; }
.log-line .ln { color: #334155; min-width: 24px; text-align: right; }
.log-line .txt { color: #e2e8f0; white-space: pre-wrap; }
.empty-term { height: 100%; display: flex; align-items: center; justify-content: center; color: #475569; font-style: italic; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.5s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>

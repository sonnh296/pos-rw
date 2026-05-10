<script setup lang="ts">
import { ref } from 'vue';
import BaseCard from '@/components/ui/BaseCard.vue';
import BaseButton from '@/components/ui/BaseButton.vue';

const grafanaUrl = ref('http://localhost:3000/d/thread-comparison');
const copied = ref('');

function copyCommand(cmd: string) {
  navigator.clipboard.writeText(cmd);
  copied.value = cmd;
  setTimeout(() => copied.value = '', 2000);
}

function openGrafana() {
  window.open(grafanaUrl.value, '_blank');
}
</script>

<template>
  <BaseCard 
    title="Giai Đoạn 2: Hiệu Năng (Platform vs Virtual Thread)"
    subtitle="Dùng K6 để gửi tới 5000 request đồng thời. Theo dõi kết quả real-time trên Grafana dashboard."
  >
    <template #headerActions>
      <div class="card-actions">
        <BaseButton variant="primary" size="small" @click="openGrafana">
          Mở Grafana
        </BaseButton>
      </div>
    </template>

    <div class="k6-guide">
      <!-- Architecture Info -->
      <div class="info-section">
        <h4>Kiến trúc mới</h4>
        <div class="arch-flow">
          <div class="flow-item">
            <div class="flow-icon">K6</div>
            <span>Load Generator</span>
          </div>
          <div class="flow-arrow">→</div>
          <div class="flow-item">
            <div class="flow-icon api">API</div>
            <span>Spring Boot</span>
          </div>
          <div class="flow-arrow">→</div>
          <div class="flow-item">
            <div class="flow-icon prom">P</div>
            <span>Prometheus</span>
          </div>
          <div class="flow-arrow">→</div>
          <div class="flow-item">
            <div class="flow-icon graf">G</div>
            <span>Grafana</span>
          </div>
        </div>
      </div>

      <!-- Commands -->
      <div class="commands-section">
        <h4>Chạy Performance Test</h4>
        
        <div class="command-block">
          <div class="command-label">
            <span class="tag platform">Platform vs Virtual</span>
            <span class="desc">5000 VUs, ramp-up, so sánh throughput + latency</span>
          </div>
          <div class="command-line" @click="copyCommand('docker compose run k6 run -o experimental-prometheus-rw /scripts/phase2-throughput.js')">
            <code>docker compose run k6 run -o experimental-prometheus-rw /scripts/phase2-throughput.js</code>
            <span class="copy-hint">{{ copied === 'docker compose run k6 run -o experimental-prometheus-rw /scripts/phase2-throughput.js' ? '✓ Copied' : 'Click to copy' }}</span>
          </div>
        </div>

        <div class="command-block">
          <div class="command-label">
            <span class="tag accuracy">Accuracy Test</span>
            <span class="desc">Kiểm tra data consistency dưới concurrency</span>
          </div>
          <div class="command-line" @click="copyCommand('docker compose run k6 run -o experimental-prometheus-rw /scripts/phase1-accuracy.js')">
            <code>docker compose run k6 run -o experimental-prometheus-rw /scripts/phase1-accuracy.js</code>
            <span class="copy-hint">{{ copied === 'docker compose run k6 run -o experimental-prometheus-rw /scripts/phase1-accuracy.js' ? '✓ Copied' : 'Click to copy' }}</span>
          </div>
        </div>
      </div>

      <!-- Grafana Panels -->
      <div class="metrics-preview">
        <h4>Metrics trên Grafana Dashboard</h4>
        <div class="metrics-grid">
          <div class="metric-item">
            <div class="metric-icon">📈</div>
            <div class="metric-info">
              <strong>Request Rate</strong>
              <span>Platform vs Virtual req/s</span>
            </div>
          </div>
          <div class="metric-item">
            <div class="metric-icon">⏱</div>
            <div class="metric-info">
              <strong>P95 Latency</strong>
              <span>Response time comparison</span>
            </div>
          </div>
          <div class="metric-item">
            <div class="metric-icon">🧵</div>
            <div class="metric-info">
              <strong>JVM Threads</strong>
              <span>Live + Peak thread count</span>
            </div>
          </div>
          <div class="metric-item">
            <div class="metric-icon">💾</div>
            <div class="metric-info">
              <strong>Memory & CPU</strong>
              <span>Heap usage, CPU, GC pauses</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Links -->
      <div class="links-section">
        <a :href="grafanaUrl" target="_blank" class="link-card grafana-link">
          <span class="link-icon">📊</span>
          <div>
            <strong>Grafana Dashboard</strong>
            <span>{{ grafanaUrl }}</span>
          </div>
        </a>
        <a href="http://localhost:9090" target="_blank" class="link-card prom-link">
          <span class="link-icon">🔍</span>
          <div>
            <strong>Prometheus UI</strong>
            <span>http://localhost:9090</span>
          </div>
        </a>
      </div>
    </div>
  </BaseCard>
</template>

<style scoped>
.card-actions { display: flex; gap: 8px; }

.k6-guide {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Architecture Flow */
.info-section h4,
.commands-section h4,
.metrics-preview h4 {
  margin: 0 0 16px;
  font-size: 13px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.arch-flow {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 12px;
}

.flow-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.flow-item span {
  font-size: 10px;
  color: #64748b;
}

.flow-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 13px;
  background: linear-gradient(135deg, #7c3aed, #6d28d9);
  color: #fff;
}

.flow-icon.api { background: linear-gradient(135deg, #3b82f6, #2563eb); }
.flow-icon.prom { background: linear-gradient(135deg, #f59e0b, #d97706); }
.flow-icon.graf { background: linear-gradient(135deg, #ec4899, #db2777); }

.flow-arrow {
  font-size: 18px;
  color: #475569;
  font-weight: 700;
}

/* Commands */
.command-block {
  margin-bottom: 12px;
}

.command-label {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.tag {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 3px 8px;
  border-radius: 4px;
}

.tag.platform { background: rgba(139, 92, 246, 0.2); color: #a78bfa; }
.tag.accuracy { background: rgba(16, 185, 129, 0.2); color: #34d399; }

.command-label .desc {
  font-size: 12px;
  color: #64748b;
}

.command-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 10px 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.command-line:hover {
  border-color: rgba(139, 92, 246, 0.4);
  background: rgba(0, 0, 0, 0.4);
}

.command-line code {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  color: #e2e8f0;
  word-break: break-all;
}

.copy-hint {
  font-size: 10px;
  color: #64748b;
  white-space: nowrap;
  margin-left: 12px;
}

/* Metrics Preview */
.metrics-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.metric-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 10px;
}

.metric-icon { font-size: 20px; }

.metric-info {
  display: flex;
  flex-direction: column;
}

.metric-info strong {
  font-size: 12px;
  color: #f1f5f9;
}

.metric-info span {
  font-size: 10px;
  color: #64748b;
}

/* Links */
.links-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.link-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  text-decoration: none;
  transition: all 0.2s;
}

.link-card:hover {
  border-color: rgba(139, 92, 246, 0.3);
  background: rgba(255, 255, 255, 0.04);
  transform: translateY(-1px);
}

.link-icon { font-size: 24px; }

.link-card strong {
  display: block;
  font-size: 13px;
  color: #f1f5f9;
}

.link-card span {
  font-size: 11px;
  color: #64748b;
}

@media (max-width: 800px) {
  .arch-flow { flex-wrap: wrap; }
  .metrics-grid { grid-template-columns: 1fr; }
  .links-section { grid-template-columns: 1fr; }
}
</style>

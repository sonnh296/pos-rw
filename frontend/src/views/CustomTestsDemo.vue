<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { testService } from '@/api/test.service';
import type { Phase1Result, Phase2Result } from '@/types';
import BaseButton from '@/components/ui/BaseButton.vue';
import Phase1Card from '@/components/features/Phase1Card.vue';
import Phase2Card from '@/components/features/Phase2Card.vue';

const phase1Summary = ref<Phase1Result['summary'] | null>(null);
const phase2Summary = ref<Phase2Result['summary'] | null>(null);
const isGlobalLoading = ref(false);

async function fetchAll() {
  isGlobalLoading.value = true;
  await Promise.all([
    fetchPhase1(),
    fetchPhase2()
  ]);
  isGlobalLoading.value = false;
}

onMounted(() => {
  fetchAll();
});

// Parse CSV: executor,mode,iteration,amounts,expected_val,actual_val,is_accurate,duration_ms
async function fetchPhase1() {
  const res = await testService.getPhase1Csv();
  if (res.ok && res.data) {
    const lines = res.data.split('\n').map(l => l.trim()).filter(l => l);
    if (lines.length <= 1) return; // Only header or empty
    
    const summary: Record<string, any> = {};
    const groups: Record<string, any[]> = {};
    
    // Skip header
    for (let i = 1; i < lines.length; i++) {
      const parts = lines[i].split(',');
      if (parts.length < 8) continue;
      
      const executor = parts[0];
      const mode = parts[1];
      const isAccurate = parts[6] === 'true';
      const durationMs = parseInt(parts[7], 10) || 0;
      
      const key = `${executor}_${mode}`;
      if (!groups[key]) groups[key] = [];
      groups[key].push({ isAccurate, durationMs });
    }
    
    for (const key of Object.keys(groups)) {
      const items = groups[key];
      const accurate = items.filter(i => i.isAccurate).length;
      const total = items.length;
      const percent = total > 0 ? (accurate / total) * 100 : 0;
      const avgDuration = items.reduce((sum, i) => sum + i.durationMs, 0) / total;
      
      summary[key] = { accurate, total, percent, avgDuration };
    }
    
    phase1Summary.value = summary;
  }
}

// Parse CSV: executor,iteration,duration_ms,throughput_rps,p95_ms
async function fetchPhase2() {
  const res = await testService.getPhase2Csv();
  if (res.ok && res.data) {
    const lines = res.data.split('\n').map(l => l.trim()).filter(l => l);
    if (lines.length <= 1) return; // Only header or empty
    
    const summary: Record<string, any> = {};
    const groups: Record<string, any[]> = {};
    
    // Skip header
    for (let i = 1; i < lines.length; i++) {
      const parts = lines[i].split(',');
      if (parts.length < 5) continue;
      
      const executor = parts[0];
      const throughputRps = parseFloat(parts[3]) || 0;
      const p95Ms = parseInt(parts[4], 10) || 0;
      
      if (!groups[executor]) groups[executor] = [];
      groups[executor].push({ throughputRps, p95Ms });
    }
    
    for (const key of Object.keys(groups)) {
      const items = groups[key];
      const total = items.length;
      const avgThroughput = items.reduce((sum, i) => sum + i.throughputRps, 0) / total;
      const avgP95 = items.reduce((sum, i) => sum + i.p95Ms, 0) / total;
      
      summary[key] = { avgThroughput, avgP95 };
    }
    
    phase2Summary.value = summary;
  }
}

async function clearResults() {
  if (!confirm("Bạn có chắc chắn muốn xóa toàn bộ dữ liệu CSV không?")) return;
  isGlobalLoading.value = true;
  await testService.clear();
  phase1Summary.value = null;
  phase2Summary.value = null;
  isGlobalLoading.value = false;
}
</script>

<template>
  <div class="performance-page">
    <div class="header-info">
      <div class="instructions">
        <h3>Hướng dẫn chạy Test</h3>
        <p>Hệ thống không tự động chạy test để đảm bảo tách biệt môi trường thực tế. Hãy mở Terminal và chạy lệnh sau:</p>
        <code>cd pos/loadtest/script_run && ./run-phase1.sh 100</code>
        <br/><br/>
        <code>cd pos/loadtest/script_run && ./run-phase2.sh 100 5000</code>
      </div>
      <div class="top-actions">
        <BaseButton 
          variant="primary" 
          @click="fetchAll" 
          :loading="isGlobalLoading"
        >
          Tải Lại Dữ Liệu
        </BaseButton>
        <BaseButton 
          variant="danger" 
          @click="clearResults" 
          :disabled="isGlobalLoading"
        >
          Xóa Dữ Liệu
        </BaseButton>
      </div>
    </div>

    <div class="dashboard-grid">
      <!-- Phase 1 -->
      <Phase1Card 
        :summary="phase1Summary" 
        :isRunning="false"
        @refresh="fetchPhase1"
      />

      <!-- Phase 2 -->
      <Phase2Card 
        :summary="phase2Summary" 
        :isRunning="false"
        @refresh="fetchPhase2"
      />
    </div>
  </div>
</template>

<style scoped>
.performance-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.header-info {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  background: rgba(255, 255, 255, 0.05);
  padding: 20px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.instructions h3 {
  margin-top: 0;
  margin-bottom: 8px;
  color: var(--text-color);
}

.instructions p {
  margin-top: 0;
  margin-bottom: 12px;
  color: rgba(255,255,255,0.7);
  font-size: 14px;
}

.instructions code {
  background: rgba(0,0,0,0.3);
  padding: 8px 12px;
  border-radius: 4px;
  color: #4CAF50;
  font-family: monospace;
  display: inline-block;
  margin-bottom: 8px;
}

.top-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dashboard-grid {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

@media (max-width: 1024px) {
  .dashboard-grid { gap: 24px; }
  .header-info { flex-direction: column; gap: 20px; }
  .top-actions { flex-direction: row; width: 100%; justify-content: flex-end; }
}
</style>

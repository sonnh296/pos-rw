<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { testService } from '@/api/test.service';
import type { Phase1Result, Phase2Result } from '@/types';
import BaseButton from '@/components/ui/BaseButton.vue';
import Phase1Card from '@/components/features/Phase1Card.vue';
import Phase2Card from '@/components/features/Phase2Card.vue';

const phase1Summary = ref<Phase1Result['summary'] | null>(null);
const phase2Summary = ref<Phase2Result['summary'] | null>(null);
const phase2ByRps = ref<Phase2Result['byRps']>(undefined);
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

// CSV ramp: executor,rps,duration_ms,throughput_rps,p95_ms,p99_ms
// CSV legacy: executor,iteration,duration_ms,throughput_rps,p95_ms[,p99_ms]
async function fetchPhase2() {
  const res = await testService.getPhase2Csv();
  if (res.ok && res.data) {
    const lines = res.data.split('\n').map(l => l.trim()).filter(l => l);
    if (lines.length <= 1) return;

    const header = lines[0].split(',').map(h => h.trim().toLowerCase());
    const rpsIdx = header.indexOf('rps');
    const isRamp = rpsIdx >= 0;

    const summary: Record<string, { avgThroughput: number; avgP95: number; avgP99: number }> = {};
    const groups: Record<string, { throughputRps: number; p95Ms: number; p99Ms: number; rps?: number }[]> = {};
    const rampByRps = new Map<number, Record<string, { throughputRps: number; p95Ms: number; p99Ms: number }>>();

    for (let i = 1; i < lines.length; i++) {
      const parts = lines[i].split(',');
      if (parts.length < 5) continue;

      const executor = parts[0];
      let throughputRps: number;
      let p95Ms: number;
      let p99Ms: number;
      let rps: number | undefined;

      if (isRamp) {
        rps = parseInt(parts[rpsIdx], 10) || 0;
        throughputRps = parseFloat(parts[header.indexOf('throughput_rps')]) || 0;
        p95Ms = parseInt(parts[header.indexOf('p95_ms')], 10) || 0;
        p99Ms = parseInt(parts[header.indexOf('p99_ms')], 10) || 0;
      } else {
        throughputRps = parseFloat(parts[3]) || 0;
        p95Ms = parseInt(parts[4], 10) || 0;
        p99Ms = parseInt(parts[5], 10) || 0;
      }

      if (!groups[executor]) groups[executor] = [];
      groups[executor].push({ throughputRps, p95Ms, p99Ms, rps });

      if (rps != null && rps > 0) {
        if (!rampByRps.has(rps)) rampByRps.set(rps, {});
        rampByRps.get(rps)![executor] = { throughputRps, p95Ms, p99Ms };
      }
    }

    for (const key of Object.keys(groups)) {
      const items = groups[key];
      const total = items.length;
      summary[key] = {
        avgThroughput: items.reduce((sum, i) => sum + i.throughputRps, 0) / total,
        avgP95: items.reduce((sum, i) => sum + i.p95Ms, 0) / total,
        avgP99: items.reduce((sum, i) => sum + i.p99Ms, 0) / total,
      };
    }

    phase2Summary.value = summary;

    if (rampByRps.size > 0) {
      const sorted = [...rampByRps.keys()].sort((a, b) => a - b);
      phase2ByRps.value = {
        rpsLevels: sorted,
        PLATFORM: sorted.map(rps => ({ rps, ...rampByRps.get(rps)!.PLATFORM })),
        VIRTUAL: sorted.map(rps => ({ rps, ...rampByRps.get(rps)!.VIRTUAL })),
      };
    } else {
      phase2ByRps.value = undefined;
    }
  }
}

async function clearResults() {
  if (!confirm("Bạn có chắc chắn muốn xóa toàn bộ dữ liệu CSV không?")) return;
  isGlobalLoading.value = true;
  await testService.clear();
  phase1Summary.value = null;
  phase2Summary.value = null;
  phase2ByRps.value = undefined;
  isGlobalLoading.value = false;
}
</script>

<template>
  <div class="performance-page">
    <div class="header-info">
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
        :by-rps="phase2ByRps"
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

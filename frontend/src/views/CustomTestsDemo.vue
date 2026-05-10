<script setup lang="ts">
import { ref } from 'vue';
import { runPhase1Test, clearRewardData } from '@/api/test.service';
import type { Phase1Progress, Phase1TestState } from '@/api/test.service';
import type { Phase1GroupSummary } from '@/types';
import BaseButton from '@/components/ui/BaseButton.vue';
import StatusPanel from '@/components/features/StatusPanel.vue';
import Phase1Card from '@/components/features/Phase1Card.vue';
import Phase2Card from '@/components/features/Phase2Card.vue';

const phase1Summary = ref<Record<string, Phase1GroupSummary> | null>(null);
const phase1Progress = ref<Phase1Progress | null>(null);
const isRunning = ref(false);
const isCancelled = ref(false);
const isClearing = ref(false);

async function runPhase1() {
  if (isRunning.value) return;
  isRunning.value = true;
  isCancelled.value = false;
  phase1Summary.value = null;

  try {
    const result: Phase1TestState = await runPhase1Test(
      5,
      (p) => { phase1Progress.value = p; },
      () => isCancelled.value
    );
    phase1Summary.value = result.summary;
  } catch (e) {
    console.error('Phase 1 test error:', e);
  } finally {
    isRunning.value = false;
    phase1Progress.value = null;
  }
}

function stopTests() {
  isCancelled.value = true;
}

async function clearResults() {
  if (!confirm("Xóa toàn bộ dữ liệu reward (để reset trước test mới)?")) return;
  isClearing.value = true;
  await clearRewardData();
  phase1Summary.value = null;
  isClearing.value = false;
}
</script>

<template>
  <div class="performance-page">
    <div class="top-actions">
      <BaseButton 
        v-if="isRunning" 
        variant="warning" 
        @click="stopTests"
      >
        Dừng Test
      </BaseButton>
      <BaseButton 
        variant="danger" 
        @click="clearResults" 
        :disabled="isClearing || isRunning"
      >
        Xóa Dữ Liệu
      </BaseButton>
    </div>

    <div class="dashboard-grid">
      <!-- Status Section -->
      <StatusPanel 
        v-if="isRunning && phase1Progress" 
        :progress="phase1Progress"
        class="status-section"
      />

      <!-- Phase 1: Accuracy — frontend gọi API trực tiếp -->
      <Phase1Card 
        :summary="phase1Summary" 
        :isRunning="isRunning"
        @run="runPhase1"
      />

      <!-- Phase 2: Throughput — K6 + Grafana -->
      <Phase2Card />
    </div>
  </div>
</template>

<style scoped>
.performance-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.top-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.dashboard-grid {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.status-section {
  margin-bottom: 8px;
}

@media (max-width: 1024px) {
  .dashboard-grid { gap: 24px; }
}
</style>

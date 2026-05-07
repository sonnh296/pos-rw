<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { testService } from '@/api/test.service';
import type { TestStatus, Phase1Result, Phase2Result } from '@/types';
import BaseButton from '@/components/ui/BaseButton.vue';
import StatusPanel from '@/components/features/StatusPanel.vue';
import Phase1Card from '@/components/features/Phase1Card.vue';
import Phase2Card from '@/components/features/Phase2Card.vue';

const status = ref<TestStatus | null>(null);
const phase1Summary = ref<Phase1Result['summary'] | null>(null);
const phase2Summary = ref<Phase2Result['summary'] | null>(null);
const isGlobalLoading = ref(false);

let pollInterval: any = null;

async function fetchAll() {
  await Promise.all([
    fetchStatus(),
    fetchPhase1(),
    fetchPhase2()
  ]);
}

onMounted(() => {
  fetchAll();
  pollInterval = setInterval(() => {
    fetchStatus();
    if (status.value && (status.value.phase1Running || status.value.phase2Running)) {
      // Refresh results periodically if running
      fetchPhase1();
      fetchPhase2();
    }
  }, 2000);
});

onUnmounted(() => {
  if (pollInterval) clearInterval(pollInterval);
});

async function fetchStatus() {
  const res = await testService.getStatus();
  if (res.ok) status.value = res.data;
}

async function fetchPhase1() {
  const res = await testService.getPhase1Results();
  if (res.ok && Object.keys(res.data.summary).length > 0) {
    phase1Summary.value = res.data.summary;
  }
}

async function fetchPhase2() {
  const res = await testService.getPhase2Results();
  if (res.ok && res.data.summary && Object.keys(res.data.summary).length > 0) {
    phase2Summary.value = res.data.summary;
  }
}

async function stopTests() {
  isGlobalLoading.value = true;
  await testService.stop();
  isGlobalLoading.value = false;
  fetchStatus();
}

async function clearResults() {
  if (!confirm("Bạn có chắc chắn muốn xóa toàn bộ dữ liệu kiểm thử không?")) return;
  isGlobalLoading.value = true;
  await testService.clear();
  phase1Summary.value = null;
  phase2Summary.value = null;
  isGlobalLoading.value = false;
}

async function runPhase1() {
  await testService.runPhase1(5);
  fetchStatus();
}

async function runPhase2() {
  await testService.runPhase2(5, 5000);
  fetchStatus();
}
</script>

<template>
  <div class="performance-page">
    <div class="top-actions">
      <BaseButton 
        v-if="status && (status.phase1Running || status.phase2Running)" 
        variant="warning" 
        @click="stopTests" 
        :loading="isGlobalLoading"
      >
        Dừng Test
      </BaseButton>
      <BaseButton 
        variant="danger" 
        @click="clearResults" 
        :disabled="isGlobalLoading || !!(status?.phase1Running || status?.phase2Running)"
      >
        Xóa Dữ Liệu
      </BaseButton>
    </div>

    <div class="dashboard-grid">
      <!-- Status Section -->
      <StatusPanel 
        v-if="status && (status.phase1Running || status.phase2Running)" 
        :status="status" 
        class="status-section"
      />

      <!-- Phase 1 -->
      <Phase1Card 
        :summary="phase1Summary" 
        :isRunning="status?.phase1Running ?? false"
        @run="runPhase1"
        @refresh="fetchPhase1"
      />

      <!-- Phase 2 -->
      <Phase2Card 
        :summary="phase2Summary" 
        :isRunning="status?.phase2Running ?? false"
        @run="runPhase2"
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

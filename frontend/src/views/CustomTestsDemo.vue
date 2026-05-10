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
  
  // Fade out old results before starting
  if (phase1Summary.value) {
    phase1Summary.value = null;
    await new Promise(r => setTimeout(r, 300));
  }

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
    <!-- Hero Header -->
    <header class="hero-header">
      <div class="hero-content">
        <h1>Bảng Điều Khiển Hiệu Năng</h1>
        <p>Phân tích chuyên sâu về mô hình luồng và độ chính xác dữ liệu.</p>
      </div>
      <div class="top-actions">
        <BaseButton 
          v-if="isRunning" 
          variant="warning" 
          @click="stopTests"
          icon="stop"
        >
          Dừng Kiểm Thử
        </BaseButton>
        <BaseButton 
          variant="danger" 
          @click="clearResults" 
          :disabled="isClearing || isRunning"
          icon="trash"
        >
          Xóa Dữ Liệu
        </BaseButton>
      </div>
    </header>

    <div class="dashboard-grid">
      <!-- Status Section with Transition -->
      <Transition name="slide-fade">
        <StatusPanel 
          v-if="isRunning && phase1Progress" 
          :progress="phase1Progress"
          class="status-section"
        />
      </Transition>

      <div class="main-content">
        <!-- Phase 1: Accuracy -->
        <Phase1Card 
          :summary="phase1Summary" 
          :isRunning="isRunning"
          @run="runPhase1"
          class="content-card phase1-section"
        />

        <!-- Phase 2: Throughput -->
        <Phase2Card class="content-card phase2-section" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.performance-page {
  display: flex;
  flex-direction: column;
  gap: 32px;
  max-width: 1400px;
  margin: 0 auto;
  padding-bottom: 60px;
}

.hero-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: 40px 0 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.hero-content h1 {
  font-size: 32px;
  font-weight: 800;
  margin: 0 0 8px;
  background: linear-gradient(to right, #fff, #94a3b8);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-content p {
  color: #64748b;
  margin: 0;
  font-size: 16px;
}

.top-actions {
  display: flex;
  gap: 12px;
}

.dashboard-grid {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.main-content {
  display: grid;
  grid-template-columns: 1fr;
  gap: 32px;
}

.content-card {
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

/* Animations */
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  transform: translateY(-20px);
  opacity: 0;
}

@media (max-width: 1024px) {
  .hero-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 20px;
  }
}
</style>

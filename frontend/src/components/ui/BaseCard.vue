<script setup lang="ts">
interface Props {
  title?: string;
  subtitle?: string;
  noPadding?: boolean;
}

defineProps<Props>();
</script>

<template>
  <div class="base-card" :class="{ 'base-card--no-padding': noPadding }">
    <div v-if="title || $slots.header" class="base-card__header">
      <div class="base-card__title-group">
        <h3 v-if="title" class="base-card__title">{{ title }}</h3>
        <p v-if="subtitle" class="base-card__subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.headerActions" class="base-card__actions">
        <slot name="headerActions"></slot>
      </div>
    </div>
    
    <div class="base-card__body">
      <slot></slot>
    </div>
    
    <div v-if="$slots.footer" class="base-card__footer">
      <slot name="footer"></slot>
    </div>
  </div>
</template>

<style scoped>
.base-card {
  background: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 10px 30px -5px rgba(0, 0, 0, 0.3);
  transition: border-color 0.3s ease;
}

.base-card:hover {
  border-color: rgba(255, 255, 255, 0.15);
}

.base-card__header {
  padding: 20px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.base-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #f1f5f9;
}

.base-card__subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: #94a3b8;
}

.base-card__body {
  padding: 24px;
  flex: 1;
}

.base-card--no-padding .base-card__body {
  padding: 0;
}

.base-card__footer {
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.02);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

@media (max-width: 640px) {
  .base-card__header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { apiFetch, jsonBody } from '../lib/api'

type RewardResponse = {
  customerId: string
  totalPoints: number
  status: string
  threadName?: string
  processingTimeMs?: number
}

const customerId = ref('customer-001')
const amount = ref(100)
const txnPrefix = ref('txn-web')
const endpoint = ref<'platform/lock' | 'platform/no-lock' | 'virtual/lock' | 'virtual/no-lock'>('platform/lock')

const last = ref<unknown>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const txnId = computed(() => `${txnPrefix.value}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`)

async function postReward() {
  loading.value = true
  error.value = null
  const body = {
    customerId: customerId.value,
    transactionId: txnId.value,
    amount: Number(amount.value),
  }
  const res = await apiFetch<RewardResponse>(`/api/rewards/${endpoint.value}`, {
    method: 'POST',
    ...jsonBody(body),
  })
  loading.value = false
  if (!res.ok) {
    error.value = `${res.error.message}`
    last.value = res.error
    return
  }
  last.value = res.data
}

async function getPoints() {
  loading.value = true
  error.value = null
  const res = await apiFetch<Record<string, unknown>>(`/api/rewards/points/${encodeURIComponent(customerId.value)}`)
  loading.value = false
  if (!res.ok) {
    error.value = `${res.error.message}`
    last.value = res.error
    return
  }
  last.value = res.data
}

async function compare() {
  loading.value = true
  error.value = null
  const res = await apiFetch<Record<string, unknown>>(
    `/api/rewards/balance/compare/${encodeURIComponent(customerId.value)}`
  )
  loading.value = false
  if (!res.ok) {
    error.value = `${res.error.message}`
    last.value = res.error
    return
  }
  last.value = res.data
}

async function health() {
  loading.value = true
  error.value = null
  const res = await apiFetch<Record<string, unknown>>(`/actuator/health`)
  loading.value = false
  if (!res.ok) {
    error.value = `${res.error.message}`
    last.value = res.error
    return
  }
  last.value = res.data
}
</script>

<template>
  <section class="card">
    <div class="grid">
      <label class="field">
        <div class="field__label">Customer ID</div>
        <input v-model="customerId" class="input" placeholder="customer-001" />
      </label>

      <label class="field">
        <div class="field__label">Số tiền (amount)</div>
        <input v-model.number="amount" class="input" type="number" min="0" step="1" />
      </label>

      <label class="field">
        <div class="field__label">Transaction prefix</div>
        <input v-model="txnPrefix" class="input" placeholder="txn-web" />
      </label>

      <label class="field">
        <div class="field__label">Endpoint</div>
        <select v-model="endpoint" class="input">
          <option value="platform/lock">POST /api/rewards/platform/lock</option>
          <option value="platform/no-lock">POST /api/rewards/platform/no-lock</option>
          <option value="virtual/lock">POST /api/rewards/virtual/lock</option>
          <option value="virtual/no-lock">POST /api/rewards/virtual/no-lock</option>
        </select>
      </label>
    </div>

    <div class="actions">
      <button class="btn primary" :disabled="loading" @click="postReward">
        Thanh toán (gửi transaction)
      </button>
      <button class="btn" :disabled="loading" @click="getPoints">GET points</button>
      <button class="btn" :disabled="loading" @click="compare">Compare Redis vs MySQL</button>
      <button class="btn" :disabled="loading" @click="health">Health</button>
    </div>

    <div v-if="error" class="err">Error: {{ error }}</div>

    <div class="result">
      <div class="result__title">Last response</div>
      <pre class="pre">{{ JSON.stringify(last, null, 2) }}</pre>
    </div>
  </section>
</template>

<style scoped>
.card {
  background: rgba(232, 236, 255, 0.04);
  border: 1px solid rgba(232, 236, 255, 0.1);
  border-radius: 16px;
  padding: 16px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.field__label {
  font-size: 12px;
  opacity: 0.8;
  margin-bottom: 6px;
}
.input {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(232, 236, 255, 0.14);
  background: rgba(11, 16, 32, 0.45);
  color: inherit;
}
.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 14px;
}
.btn {
  border-radius: 12px;
  border: 1px solid rgba(232, 236, 255, 0.14);
  background: rgba(232, 236, 255, 0.04);
  color: inherit;
  padding: 10px 12px;
  cursor: pointer;
  font-weight: 700;
  font-size: 13px;
}
.btn.primary {
  border-color: rgba(140, 170, 255, 0.55);
  background: rgba(140, 170, 255, 0.14);
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.err {
  margin-top: 12px;
  color: #ffb4b4;
}
.result {
  margin-top: 14px;
}
.result__title {
  font-weight: 700;
  font-size: 13px;
  opacity: 0.9;
  margin-bottom: 8px;
}
.pre {
  margin: 0;
  max-height: 320px;
  overflow: auto;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid rgba(232, 236, 255, 0.12);
  background: rgba(0, 0, 0, 0.25);
  font-size: 12px;
  line-height: 1.4;
}
@media (max-width: 840px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>


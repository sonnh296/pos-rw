<script setup lang="ts">
import { computed, ref } from 'vue'
import { apiFetch, jsonBody } from '../lib/api'

type UserDto = { id: string; name: string; email?: string | null; createdAt: string }

const users = ref<UserDto[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const name = ref('demo-user')
const email = ref('demo-user@demo.local')

const count = computed(() => users.value.length)

async function refresh() {
  loading.value = true
  error.value = null
  const res = await apiFetch<UserDto[]>('/api/users')
  loading.value = false
  if (!res.ok) {
    error.value = res.error.message
    return
  }
  users.value = res.data
}

async function create() {
  loading.value = true
  error.value = null
  const res = await apiFetch<UserDto>('/api/users', {
    method: 'POST',
    ...jsonBody({ name: name.value, email: email.value }),
  })
  loading.value = false
  if (!res.ok) {
    error.value = res.error.message
    return
  }
  users.value = [res.data, ...users.value]
}

refresh()
</script>

<template>
  <section class="card">
    <div class="row">
      <button class="btn" :disabled="loading" @click="refresh">Refresh</button>
      <div class="pill">Total: {{ count }}</div>
    </div>

    <div class="grid">
      <label class="field">
        <div class="field__label">Name</div>
        <input v-model="name" class="input" />
      </label>
      <label class="field">
        <div class="field__label">Email</div>
        <input v-model="email" class="input" />
      </label>
      <div class="field">
        <div class="field__label">Create</div>
        <button class="btn primary" :disabled="loading" @click="create">POST /api/users</button>
      </div>
    </div>

    <div v-if="error" class="err">Error: {{ error }}</div>

    <div class="table">
      <div class="thead">
        <div>ID</div>
        <div>Name</div>
        <div>Email</div>
        <div>Created</div>
      </div>
      <div v-for="u in users" :key="u.id" class="trow">
        <div class="mono">{{ u.id }}</div>
        <div>{{ u.name }}</div>
        <div class="mono">{{ u.email ?? '' }}</div>
        <div class="mono">{{ u.createdAt }}</div>
      </div>
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
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.pill {
  font-size: 12px;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(232, 236, 255, 0.12);
  background: rgba(0, 0, 0, 0.18);
}
.grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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
.table {
  margin-top: 14px;
  border: 1px solid rgba(232, 236, 255, 0.12);
  border-radius: 14px;
  overflow: hidden;
}
.thead,
.trow {
  display: grid;
  grid-template-columns: 1.3fr 0.7fr 1fr 1fr;
  gap: 10px;
  padding: 10px 12px;
}
.thead {
  font-weight: 800;
  font-size: 12px;
  background: rgba(0, 0, 0, 0.18);
}
.trow {
  font-size: 12px;
  border-top: 1px solid rgba(232, 236, 255, 0.08);
}
.mono {
  font-family: ui-monospace, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}
@media (max-width: 980px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .thead,
  .trow {
    grid-template-columns: 1fr;
  }
}
</style>


<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { apiFetch, jsonBody } from '../lib/api'

type UserDto = { id: string; name: string; email?: string | null; createdAt: string }

const users = ref<UserDto[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// Create form
const newName = ref('')
const newEmail = ref('')

// Edit state
const editId = ref<string | null>(null)
const editName = ref('')
const editEmail = ref('')

// Search
const search = ref('')

const filtered = computed(() => {
  const q = search.value.toLowerCase()
  if (!q) return users.value
  return users.value.filter(
    (u) => u.name.toLowerCase().includes(q) || (u.email ?? '').toLowerCase().includes(q),
  )
})

async function load() {
  loading.value = true
  error.value = null
  const res = await apiFetch<UserDto[]>('/api/users')
  loading.value = false
  if (!res.ok) { error.value = res.error.message; return }
  users.value = res.data
}

async function create() {
  if (!newName.value.trim()) return
  loading.value = true
  error.value = null
  const res = await apiFetch<UserDto>('/api/users', {
    method: 'POST',
    ...jsonBody({ name: newName.value.trim(), email: newEmail.value.trim() || null }),
  })
  loading.value = false
  if (!res.ok) { error.value = res.error.message; return }
  users.value = [res.data, ...users.value]
  newName.value = ''
  newEmail.value = ''
}

function startEdit(u: UserDto) {
  editId.value = u.id
  editName.value = u.name
  editEmail.value = u.email ?? ''
}

function cancelEdit() {
  editId.value = null
}

async function saveEdit(id: string) {
  loading.value = true
  error.value = null
  const res = await apiFetch<UserDto>(`/api/users/${id}`, {
    method: 'PUT',
    ...jsonBody({ name: editName.value.trim(), email: editEmail.value.trim() || null }),
  })
  loading.value = false
  if (!res.ok) { error.value = res.error.message; return }
  const idx = users.value.findIndex((u) => u.id === id)
  if (idx !== -1) users.value[idx] = res.data
  editId.value = null
}

async function remove(id: string) {
  if (!confirm('Xóa user này?')) return
  loading.value = true
  error.value = null
  const res = await apiFetch<void>(`/api/users/${id}`, { method: 'DELETE' })
  loading.value = false
  if (!res.ok) { error.value = res.error.message; return }
  users.value = users.value.filter((u) => u.id !== id)
}

function fmtDate(s: string) {
  try { return new Date(s).toLocaleString('vi-VN') } catch { return s }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <!-- Create form -->
    <section class="card">
      <div class="card__title">Thêm user mới</div>
      <div class="form-row">
        <label class="field">
          <div class="field__label">Name *</div>
          <input v-model="newName" class="input" placeholder="Tên user" @keyup.enter="create" />
        </label>
        <label class="field">
          <div class="field__label">Email</div>
          <input v-model="newEmail" class="input" placeholder="email@example.com" @keyup.enter="create" />
        </label>
        <div class="field field--action">
          <div class="field__label">&nbsp;</div>
          <button class="btn btn--primary" :disabled="loading || !newName.trim()" @click="create">
            + Tạo user
          </button>
        </div>
      </div>
      <div v-if="error" class="error-msg">{{ error }}</div>
    </section>

    <!-- Table -->
    <section class="card">
      <div class="table-header">
        <div class="card__title" style="margin-bottom: 0">
          Danh sách
          <span class="badge">{{ users.length }}</span>
        </div>
        <div class="header-actions">
          <input v-model="search" class="input input--search" placeholder="Tìm theo tên / email…" />
          <button class="btn" :disabled="loading" @click="load">↻ Refresh</button>
        </div>
      </div>

      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Email</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="filtered.length === 0">
              <td colspan="5" class="empty">Không có dữ liệu</td>
            </tr>
            <tr v-for="u in filtered" :key="u.id">
              <td class="mono id-cell" :title="u.id">{{ u.id.slice(0, 8) }}…</td>

              <!-- Inline edit mode -->
              <td v-if="editId === u.id">
                <input v-model="editName" class="input input--inline" @keyup.enter="saveEdit(u.id)" @keyup.esc="cancelEdit" />
              </td>
              <td v-else>{{ u.name }}</td>

              <td v-if="editId === u.id">
                <input v-model="editEmail" class="input input--inline" @keyup.enter="saveEdit(u.id)" @keyup.esc="cancelEdit" />
              </td>
              <td v-else class="mono">{{ u.email ?? '—' }}</td>

              <td class="date-cell">{{ fmtDate(u.createdAt) }}</td>

              <td class="actions-cell">
                <template v-if="editId === u.id">
                  <button class="btn btn--save" :disabled="loading" @click="saveEdit(u.id)">✓ Lưu</button>
                  <button class="btn btn--cancel" @click="cancelEdit">✕</button>
                </template>
                <template v-else>
                  <button class="btn btn--edit" @click="startEdit(u)">Sửa</button>
                  <button class="btn btn--delete" :disabled="loading" @click="remove(u.id)">Xóa</button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }

.card {
  background: rgba(232, 236, 255, 0.03);
  border: 1px solid rgba(232, 236, 255, 0.1);
  border-radius: 16px;
  padding: 18px 20px;
}

.card__title {
  font-weight: 700;
  font-size: 14px;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 12px;
  align-items: end;
}

.field { display: flex; flex-direction: column; }
.field--action { align-self: end; }
.field__label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  opacity: 0.55;
  margin-bottom: 6px;
}

.input {
  padding: 9px 12px;
  border-radius: 10px;
  border: 1px solid rgba(232, 236, 255, 0.13);
  background: rgba(10, 14, 28, 0.5);
  color: inherit;
  font-size: 13px;
  width: 100%;
  box-sizing: border-box;
}

.input--search {
  width: 220px;
}

.input--inline {
  padding: 5px 8px;
  font-size: 12px;
}

.btn {
  padding: 8px 14px;
  border-radius: 9px;
  border: 1px solid rgba(232, 236, 255, 0.14);
  background: rgba(232, 236, 255, 0.04);
  color: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.12s;
}

.btn:disabled { opacity: 0.45; cursor: not-allowed; }
.btn--primary { border-color: rgba(140, 170, 255, 0.5); background: rgba(140, 170, 255, 0.12); }
.btn--primary:not(:disabled):hover { background: rgba(140, 170, 255, 0.2); }
.btn--edit { border-color: rgba(251, 191, 36, 0.4); background: rgba(251, 191, 36, 0.07); }
.btn--edit:hover { background: rgba(251, 191, 36, 0.14); }
.btn--delete { border-color: rgba(244, 114, 182, 0.4); background: rgba(244, 114, 182, 0.06); }
.btn--delete:not(:disabled):hover { background: rgba(244, 114, 182, 0.14); }
.btn--save { border-color: rgba(52, 211, 153, 0.5); background: rgba(52, 211, 153, 0.1); }
.btn--cancel { border-color: rgba(232, 236, 255, 0.15); }

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  gap: 12px;
  flex-wrap: wrap;
}

.header-actions { display: flex; gap: 8px; align-items: center; }

.badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(232, 236, 255, 0.08);
  border: 1px solid rgba(232, 236, 255, 0.12);
}

.table-wrap { overflow-x: auto; }

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.table th {
  text-align: left;
  padding: 8px 12px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  opacity: 0.5;
  border-bottom: 1px solid rgba(232, 236, 255, 0.1);
  white-space: nowrap;
}

.table td {
  padding: 9px 12px;
  border-bottom: 1px solid rgba(232, 236, 255, 0.05);
  vertical-align: middle;
}

.table tr:last-child td { border-bottom: none; }
.table tr:hover td { background: rgba(232, 236, 255, 0.02); }

.mono { font-family: ui-monospace, Menlo, Monaco, Consolas, monospace; font-size: 12px; }
.id-cell { opacity: 0.6; }
.date-cell { font-size: 12px; opacity: 0.7; white-space: nowrap; }
.actions-cell { display: flex; gap: 6px; }

.empty { text-align: center; opacity: 0.4; padding: 24px; }

.error-msg {
  margin-top: 10px;
  color: #f87171;
  font-size: 13px;
}

@media (max-width: 768px) {
  .form-row { grid-template-columns: 1fr; }
  .input--search { width: 100%; }
}
</style>

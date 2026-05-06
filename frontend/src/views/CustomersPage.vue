<script setup lang="ts">
import { ref, onMounted } from "vue";
import { apiFetch } from "../lib/api";

type User = {
  id: string;
  name: string;
  email: string;
  createdAt: string;
};

const users = ref<User[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);

// Form state
const showModal = ref(false);
const isEditing = ref(false);
const currentUserId = ref<string | null>(null);
const form = ref({
  name: "",
  email: "",
});

// Delete confirmation state
const showDeleteConfirm = ref(false);
const userToDelete = ref<User | null>(null);

async function loadUsers() {
  loading.value = true;
  error.value = null;
  const res = await apiFetch<User[]>("/api/users");
  loading.value = false;
  if (!res.ok) {
    error.value = res.error.message;
    return;
  }
  users.value = res.data ?? [];
}

function openAddModal() {
  isEditing.value = false;
  currentUserId.value = null;
  form.value = { name: "", email: "" };
  showModal.value = true;
}

function openEditModal(user: User) {
  isEditing.value = true;
  currentUserId.value = user.id;
  form.value = { name: user.name, email: user.email };
  showModal.value = true;
}

async function saveUser() {
  loading.value = true;
  error.value = null;
  
  const url = isEditing.value ? `/api/users/${currentUserId.value}` : "/api/users";
  const method = isEditing.value ? "PUT" : "POST";
  
  const res = await apiFetch<User>(url, {
    method,
    body: JSON.stringify(form.value),
    headers: {
      "Content-Type": "application/json",
    },
  });
  
  loading.value = false;
  if (!res.ok) {
    error.value = res.error.message;
    return;
  }
  
  showModal.value = false;
  await loadUsers();
}

function confirmDelete(user: User) {
  userToDelete.value = user;
  showDeleteConfirm.value = true;
}

async function doDelete() {
  if (!userToDelete.value) return;
  
  loading.value = true;
  error.value = null;
  const res = await apiFetch<void>(`/api/users/${userToDelete.value.id}`, {
    method: "DELETE",
  });
  
  loading.value = false;
  if (!res.ok) {
    error.value = res.error.message;
    return;
  }
  
  showDeleteConfirm.value = false;
  userToDelete.value = null;
  await loadUsers();
}

function fmtDate(s: string) {
  try {
    return new Date(s).toLocaleString("vi-VN");
  } catch {
    return s;
  }
}

onMounted(async () => {
  await loadUsers();
});
</script>

<template>
  <div class="page">
    <section class="card">
      <div class="table-header">
        <div class="card__title" style="margin-bottom: 0">
          Danh sách Người dùng
          <span class="badge">{{ users.length }}</span>
        </div>
        <div class="header-actions">
          <button class="btn btn--primary" @click="openAddModal">
            + Thêm Người dùng
          </button>
          <button class="btn" :disabled="loading" @click="loadUsers">
            ↻ Làm mới
          </button>
        </div>
      </div>

      <div v-if="error" class="error-msg">{{ error }}</div>

      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Tên</th>
              <th>Email</th>
              <th>Ngày tạo</th>
              <th style="text-align: right">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="users.length === 0">
              <td colspan="5" class="empty">Không có người dùng nào</td>
            </tr>
            <tr v-for="user in users" :key="user.id">
              <td class="mono">{{ user.id.substring(0, 8) }}…</td>
              <td><strong>{{ user.name }}</strong></td>
              <td>{{ user.email }}</td>
              <td class="date-cell">{{ fmtDate(user.createdAt) }}</td>
              <td style="text-align: right">
                <div class="row-actions">
                  <button class="btn btn--small" @click="openEditModal(user)">Sửa</button>
                  <button class="btn btn--small btn--danger" @click="confirmDelete(user)">Xóa</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- Form Modal -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ isEditing ? 'Cập nhật Người dùng' : 'Thêm Người dùng mới' }}</h3>
          <button class="close-btn" @click="showModal = false">&times;</button>
        </div>
        <form @submit.prevent="saveUser">
          <div class="form-group">
            <label>Tên</label>
            <input v-model="form.name" class="input" placeholder="Nhập tên người dùng..." required />
          </div>
          <div class="form-group">
            <label>Email</label>
            <input v-model="form.email" type="email" class="input" placeholder="Nhập email..." required />
          </div>
          <div class="modal-footer">
            <button type="button" class="btn" @click="showModal = false">Hủy</button>
            <button type="submit" class="btn btn--primary" :disabled="loading">
              {{ loading ? 'Đang lưu...' : (isEditing ? 'Cập nhật' : 'Thêm mới') }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <div v-if="showDeleteConfirm" class="modal-overlay" @click.self="showDeleteConfirm = false">
      <div class="modal">
        <div class="modal-header">
          <h3>Xác nhận xóa</h3>
          <button class="close-btn" @click="showDeleteConfirm = false">&times;</button>
        </div>
        <div class="modal-body">
          <p>Bạn có chắc chắn muốn xóa người dùng <strong>{{ userToDelete?.name }}</strong>?</p>
          <p class="warn-text">Hành động này không thể hoàn tác.</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn" @click="showDeleteConfirm = false">Hủy</button>
          <button type="button" class="btn btn--danger" :disabled="loading" @click="doDelete">
            {{ loading ? 'Đang xóa...' : 'Xóa ngay' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

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

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  gap: 12px;
  flex-wrap: wrap;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(232, 236, 255, 0.08);
  border: 1px solid rgba(232, 236, 255, 0.12);
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
  transition: all 0.12s;
}

.btn--primary {
  background: rgba(96, 165, 250, 0.2);
  border-color: rgba(96, 165, 250, 0.4);
  color: #93c5fd;
}

.btn--primary:hover {
  background: rgba(96, 165, 250, 0.3);
}

.btn--danger {
  border-color: rgba(244, 114, 182, 0.4);
  background: rgba(244, 114, 182, 0.07);
  color: #f9a8d4;
}

.btn--danger:hover {
  background: rgba(244, 114, 182, 0.14);
}

.btn--small {
  padding: 4px 10px;
  font-size: 11px;
}

.btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.table-wrap {
  overflow-x: auto;
}

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
}

.table td {
  padding: 12px;
  border-bottom: 1px solid rgba(232, 236, 255, 0.05);
  vertical-align: middle;
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.mono {
  font-family: ui-monospace, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  opacity: 0.7;
}

.date-cell {
  font-size: 12px;
  opacity: 0.7;
  white-space: nowrap;
}

.empty {
  text-align: center;
  opacity: 0.4;
  padding: 40px;
}

.error-msg {
  margin-bottom: 16px;
  padding: 10px;
  background: rgba(248, 113, 113, 0.1);
  border: 1px solid rgba(248, 113, 113, 0.2);
  border-radius: 8px;
  color: #f87171;
  font-size: 13px;
}

/* Modal Styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  background: #111827;
  border: 1px solid rgba(232, 236, 255, 0.1);
  border-radius: 16px;
  width: min(400px, 90vw);
  padding: 24px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
}

.close-btn {
  background: none;
  border: none;
  color: inherit;
  font-size: 24px;
  cursor: pointer;
  opacity: 0.5;
}

.modal-body {
  margin-bottom: 20px;
  font-size: 14px;
  line-height: 1.5;
}

.warn-text {
  color: #fbbf24;
  font-size: 12px;
  margin-top: 8px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 6px;
  opacity: 0.8;
}

.input {
  width: 100%;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(232, 236, 255, 0.13);
  background: rgba(232, 236, 255, 0.04);
  color: inherit;
  font-size: 14px;
  box-sizing: border-box;
}

.input:focus {
  outline: none;
  border-color: rgba(96, 165, 250, 0.5);
  background: rgba(232, 236, 255, 0.07);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}
</style>

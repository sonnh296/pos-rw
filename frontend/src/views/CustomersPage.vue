<script setup lang="ts">
import { ref, onMounted } from "vue";
import { userService } from "@/api/user.service";
import type { User } from "@/types";
import BaseCard from "@/components/ui/BaseCard.vue";
import BaseButton from "@/components/ui/BaseButton.vue";

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
  const res = await userService.getAll();
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
  
  const res = isEditing.value 
    ? await userService.update(currentUserId.value!, form.value)
    : await userService.create(form.value);
  
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
  const res = await userService.delete(userToDelete.value.id);
  
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
  <div class="customers-page">
    <BaseCard title="Danh sách Người dùng" :subtitle="`Tổng số: ${users.length} người dùng`" noPadding>
      <template #headerActions>
        <div class="header-actions">
          <BaseButton variant="primary" @click="openAddModal">Thêm Người dùng</BaseButton>
          <BaseButton variant="ghost" :loading="loading" @click="loadUsers">Làm mới</BaseButton>
        </div>
      </template>

      <div v-if="error" class="error-container">
        <div class="error-msg">{{ error }}</div>
      </div>

      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Tên Người Dùng</th>
              <th>Email</th>
              <th>Ngày Tạo</th>
              <th style="text-align: right">Thao Tác</th>
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
                  <BaseButton size="small" variant="ghost" @click="openEditModal(user)">Sửa</BaseButton>
                  <BaseButton size="small" variant="danger" @click="confirmDelete(user)">Xóa</BaseButton>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </BaseCard>

    <!-- Form Modal -->
    <Teleport to="body">
      <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
        <div class="modal-content">
          <div class="modal-header">
            <h3>{{ isEditing ? 'Cập nhật Người dùng' : 'Thêm Người dùng mới' }}</h3>
            <button class="close-btn" @click="showModal = false">&times;</button>
          </div>
          <form @submit.prevent="saveUser">
            <div class="form-body">
              <div class="form-group">
                <label>Tên</label>
                <input v-model="form.name" class="form-input" placeholder="Nhập tên người dùng..." required />
              </div>
              <div class="form-group">
                <label>Email</label>
                <input v-model="form.email" type="email" class="form-input" placeholder="Nhập email..." required />
              </div>
            </div>
            <div class="modal-footer">
              <BaseButton type="button" variant="ghost" @click="showModal = false">Hủy</BaseButton>
              <BaseButton type="submit" variant="primary" :loading="loading">
                {{ isEditing ? 'Cập nhật' : 'Thêm mới' }}
              </BaseButton>
            </div>
          </form>
        </div>
      </div>
    </Teleport>

    <!-- Delete Confirmation Modal -->
    <Teleport to="body">
      <div v-if="showDeleteConfirm" class="modal-overlay" @click.self="showDeleteConfirm = false">
        <div class="modal-content modal-content--confirm">
          <div class="modal-header">
            <h3>Xác nhận xóa</h3>
            <button class="close-btn" @click="showDeleteConfirm = false">&times;</button>
          </div>
          <div class="modal-body">
            <p>Bạn có chắc chắn muốn xóa người dùng <strong>{{ userToDelete?.name }}</strong>?</p>
            <p class="warn-text">Hành động này không thể hoàn tác và sẽ xóa toàn bộ dữ liệu liên quan.</p>
          </div>
          <div class="modal-footer">
            <BaseButton variant="ghost" @click="showDeleteConfirm = false">Hủy</BaseButton>
            <BaseButton variant="danger" :loading="loading" @click="doDelete">Xóa ngay</BaseButton>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.customers-page {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.header-actions {
  display: flex;
  gap: 10px;
}

.error-container {
  padding: 20px 24px 0;
}

.error-msg {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #f87171;
  padding: 12px;
  border-radius: 8px;
  font-size: 13px;
}

.table-wrap {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table th {
  text-align: left;
  padding: 14px 24px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #94a3b8;
  background: rgba(255, 255, 255, 0.02);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.data-table td {
  padding: 14px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.data-table tr:hover td {
  background: rgba(255, 255, 255, 0.01);
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.mono {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 12px;
  opacity: 0.7;
}

.date-cell {
  color: #64748b;
  font-size: 12px;
}

.empty {
  text-align: center;
  padding: 60px !important;
  color: #64748b;
  font-style: italic;
}

/* Modal Styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 20px;
  width: min(440px, 90vw);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  overflow: hidden;
  animation: modalEnter 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes modalEnter {
  from { opacity: 0; transform: scale(0.9) translateY(20px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.modal-header {
  padding: 20px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  color: #f1f5f9;
}

.close-btn {
  background: none;
  border: none;
  color: #94a3b8;
  font-size: 24px;
  cursor: pointer;
  transition: color 0.2s;
}

.close-btn:hover { color: #fff; }

.form-body { padding: 24px; }

.form-group { margin-bottom: 20px; }

.form-group label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.form-input {
  width: 100%;
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.03);
  color: #fff;
  font-size: 14px;
  transition: all 0.2s;
}

.form-input:focus {
  outline: none;
  border-color: #3b82f6;
  background: rgba(255, 255, 255, 0.05);
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.1);
}

.modal-footer {
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.02);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.modal-body { padding: 24px; font-size: 14px; color: #cbd5e1; line-height: 1.6; }

.warn-text { color: #fbbf24; font-size: 13px; margin-top: 12px; font-weight: 500; }
</style>

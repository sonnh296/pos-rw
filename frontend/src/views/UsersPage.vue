<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { userService } from "@/api/user.service";
import type { CustomerPointRow } from "@/types";
import BaseCard from "@/components/ui/BaseCard.vue";
import BaseButton from "@/components/ui/BaseButton.vue";

const pointsRows = ref<CustomerPointRow[]>([]);
const pointsLoading = ref(false);
const pointsError = ref<string | null>(null);
const pointsSearch = ref("");
const pointsLimit = ref(20);
const pointsOffset = ref(0);
const pointsTotal = ref(0);

async function loadPoints() {
  pointsLoading.value = true;
  pointsError.value = null;
  
  const res = await userService.getPoints({
    limit: pointsLimit.value,
    offset: pointsOffset.value,
    keyword: pointsSearch.value.trim() || undefined
  });

  pointsLoading.value = false;
  if (!res.ok) {
    pointsError.value = res.error.message;
    return;
  }
  pointsRows.value = res.data.rows ?? [];
  pointsTotal.value = Number(res.data.total ?? 0);
}

async function clearAllData() {
  if (!confirm("Xóa toàn bộ dữ liệu điểm và lịch sử giao dịch để reset test?"))
    return;
    
  pointsLoading.value = true;
  const res = await userService.clearAllPoints();
  pointsLoading.value = false;

  if (!res.ok) {
    pointsError.value = res.error.message;
    return;
  }

  pointsOffset.value = 0;
  await loadPoints();
}

async function searchPoints() {
  pointsOffset.value = 0;
  await loadPoints();
}

async function prevPoints() {
  if (pointsOffset.value <= 0) return;
  pointsOffset.value = Math.max(0, pointsOffset.value - pointsLimit.value);
  await loadPoints();
}

async function nextPoints() {
  const next = pointsOffset.value + pointsLimit.value;
  if (next >= pointsTotal.value) return;
  pointsOffset.value = next;
  await loadPoints();
}

function fmtDate(s: string) {
  try {
    return new Date(s).toLocaleString("vi-VN");
  } catch {
    return s;
  }
}

function fmtPoints(v: number | null | undefined) {
  if (v == null) return "—";
  return Number(v).toLocaleString("vi-VN");
}

const pointsPageText = computed(() => {
  if (pointsTotal.value === 0 || pointsRows.value.length === 0) return "0/0";
  const from = pointsOffset.value + 1;
  const to = pointsOffset.value + pointsRows.value.length;
  return `${from}-${to}/${pointsTotal.value}`;
});

onMounted(async () => {
  await loadPoints();
});
</script>

<template>
  <div class="users-page">
    <BaseCard title="Điểm khách hàng" :subtitle="`Tổng số: ${pointsTotal} bản ghi`">
      <template #headerActions>
        <div class="header-actions">
          <input
            v-model="pointsSearch"
            class="search-input"
            placeholder="Tìm theo mã khách hàng…"
            @keyup.enter="searchPoints"
          />
          <BaseButton @click="searchPoints" :loading="pointsLoading">Tìm</BaseButton>
          <BaseButton variant="ghost" @click="loadPoints" :disabled="pointsLoading">Làm mới</BaseButton>
          <BaseButton variant="danger" @click="clearAllData" :disabled="pointsLoading">Xóa dữ liệu</BaseButton>
        </div>
      </template>

      <div v-if="pointsError" class="error-msg">{{ pointsError }}</div>

      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>Mã Khách Hàng</th>
              <th>Điểm Gốc</th>
              <th>MySQL</th>
              <th>Redis</th>
              <th>Đồng Bộ</th>
              <th>Nguồn</th>
              <th>Cập Nhật</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="pointsRows.length === 0">
              <td colspan="7" class="empty">Không có dữ liệu điểm</td>
            </tr>
            <tr v-for="r in pointsRows" :key="r.customerId">
              <td class="mono">{{ r.customerId }}</td>
              <td><strong>{{ fmtPoints(r.primaryPoints) }}</strong></td>
              <td>{{ fmtPoints(r.mysqlBalance) }}</td>
              <td>{{ fmtPoints(r.redisPoints) }}</td>
              <td :class="r.inSync ? 'status--ok' : 'status--warn'">
                {{ r.inSync ? "Đã đồng bộ" : "Chưa đồng bộ" }}
              </td>
              <td class="mono source-tag">{{ r.source }}</td>
              <td class="date-cell">{{ r.updatedAt ? fmtDate(r.updatedAt) : "—" }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <template #footer>
        <div class="pager">
          <div class="pager-info">Trang: <strong>{{ pointsPageText }}</strong></div>
          <div class="pager-actions">
            <BaseButton
              size="small"
              :disabled="pointsLoading || pointsOffset <= 0"
              @click="prevPoints"
            >
              Trước
            </BaseButton>
            <BaseButton
              size="small"
              :disabled="pointsLoading || pointsOffset + pointsLimit >= pointsTotal"
              @click="nextPoints"
            >
              Tiếp
            </BaseButton>
          </div>
        </div>
      </template>
    </BaseCard>
  </div>
</template>

<style scoped>
.users-page {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.search-input {
  padding: 9px 14px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(0, 0, 0, 0.2);
  color: #fff;
  font-size: 13px;
  width: 220px;
  transition: all 0.2s;
}

.search-input:focus {
  outline: none;
  border-color: rgba(59, 130, 246, 0.5);
  background: rgba(0, 0, 0, 0.3);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.table-wrap {
  margin: -24px; /* Offset card body padding */
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
  background: rgba(255, 255, 255, 0.02);
}

.mono {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 12px;
}

.source-tag {
  color: #60a5fa;
  background: rgba(96, 165, 250, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
}

.status--ok { color: #34d399; font-weight: 600; }
.status--warn { color: #fbbf24; font-weight: 600; }

.date-cell { color: #64748b; font-size: 12px; }

.pager {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pager-info { font-size: 13px; color: #94a3b8; }

.error-msg {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #f87171;
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 16px;
  font-size: 13px;
}

.empty {
  text-align: center;
  padding: 48px !important;
  color: #64748b;
  font-style: italic;
}
</style>

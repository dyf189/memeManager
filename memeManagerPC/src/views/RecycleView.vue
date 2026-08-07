<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { api } from "../api";
import type { Media } from "../types";
import { useSettingsStore } from "../stores/settings";
import MediaThumb from "../components/MediaThumb.vue";

const settingsStore = useSettingsStore();

const items = ref<Media[]>([]);
const multiSelect = ref(false);
const selectedIds = ref<Set<number>>(new Set());

async function load() {
  items.value = await api.listRecycle();
}

onMounted(load);

function toggle(id: number) {
  if (!multiSelect.value) {
    multiSelect.value = true;
    selectedIds.value = new Set([id]);
    return;
  }
  const s = new Set(selectedIds.value);
  if (s.has(id)) s.delete(id);
  else s.add(id);
  selectedIds.value = s;
  if (s.size === 0) multiSelect.value = false;
}

function selectAll() {
  const all = items.value.map((m) => m.id);
  const next = new Set(all);
  if (selectedIds.value.size === next.size) {
    selectedIds.value = new Set();
    multiSelect.value = false;
  } else {
    selectedIds.value = next;
    multiSelect.value = true;
  }
}

function exitMulti() {
  multiSelect.value = false;
  selectedIds.value = new Set();
}

async function restoreSelected() {
  if (selectedIds.value.size === 0) {
    ElMessage.info("请选择要还原的媒体");
    return;
  }
  await api.restoreMedia([...selectedIds.value]);
  exitMulti();
  await load();
  ElMessage.success("已还原");
}

async function purgeSelected() {
  if (selectedIds.value.size === 0) {
    ElMessage.info("请选择要删除的媒体");
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确定彻底删除选中的 ${selectedIds.value.size} 项吗？删除后不可恢复。`,
      "彻底删除",
      { type: "warning", confirmButtonText: "彻底删除", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  await api.purgeMedia([...selectedIds.value]);
  exitMulti();
  await load();
  ElMessage.success("已彻底删除");
}

async function purgeAll() {
  if (items.value.length === 0) return;
  try {
    await ElMessageBox.confirm(
      `确定清空回收站（${items.value.length} 项）吗？删除后不可恢复。`,
      "清空回收站",
      { type: "warning", confirmButtonText: "清空", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  await api.purgeMedia(items.value.map((m) => m.id));
  exitMulti();
  await load();
  ElMessage.success("回收站已清空");
}

const gridStyle = computed(() =>
  `repeat(${settingsStore.settings.gridCols}, minmax(0, 1fr))`
);
</script>

<template>
  <div class="recycle-view">
    <header class="recycle-top">
      <span class="page-title">回收站</span>
      <el-button size="small" type="danger" plain @click="purgeAll">
        <el-icon><Delete /></el-icon>
        <span>清空回收站</span>
      </el-button>
    </header>

    <!-- 批量操作条 -->
    <div v-if="multiSelect" class="recycle-batch">
      <button class="batch-back" @click="exitMulti">
        <el-icon><ArrowLeft /></el-icon>
        <span>取消</span>
      </button>
      <span class="batch-count">已选 {{ selectedIds.size }} / {{ items.length }} 项</span>
      <button class="batch-btn primary" @click="selectAll">全选</button>
      <div class="batch-spacer" />
      <el-button size="small" type="primary" @click="restoreSelected">
        <el-icon><RefreshLeft /></el-icon>
        <span>还原</span>
      </el-button>
      <el-button size="small" type="danger" @click="purgeSelected">
        <el-icon><Delete /></el-icon>
        <span>彻底删除</span>
      </el-button>
    </div>

    <div class="recycle-body">
      <div class="recycle-grid" :style="{ gridTemplateColumns: gridStyle }">
        <div
          v-for="m in items"
          :key="m.id"
          class="recycle-cell"
          :class="{ selected: selectedIds.has(m.id) }"
          @click="toggle(m.id)"
        >
          <MediaThumb :media="m" show-dots show-badge />
        </div>
      </div>

      <div v-if="items.length === 0" class="empty-state">
        <el-empty description="回收站是空的" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.recycle-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--card-bg);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.recycle-top {
  height: var(--app-topbar-height);
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--divider);
}

.page-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.recycle-batch {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--divider);
}

.batch-back {
  display: flex;
  align-items: center;
  gap: 2px;
  border: none;
  background: none;
  font-size: 14px;
  color: var(--text-main);
  cursor: pointer;
  padding: 4px 6px;
}

.batch-count {
  font-size: 14px;
  font-weight: 600;
}

.batch-btn {
  border: none;
  background: var(--hover-bg);
  font-size: 12px;
  padding: 5px 10px;
  border-radius: 6px;
  cursor: pointer;
  color: var(--text-main);
}

.batch-spacer {
  flex: 1;
}

.recycle-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.recycle-grid {
  display: grid;
  gap: 6px;
}

.recycle-cell {
  aspect-ratio: 1;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  position: relative;
  border: 2.5px solid transparent;
  transition: transform 0.12s ease, border-color 0.12s ease;
  content-visibility: auto;
  contain-intrinsic-size: 110px;
}

.recycle-cell:hover {
  transform: scale(1.03);
}

.recycle-cell.selected {
  border-color: var(--accent);
  background: var(--selected-bg);
}

.empty-state {
  padding: 80px 0;
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useAlbumStore } from "../stores/album";
import { useTagStore } from "../stores/tags";
import type { Media } from "../types";
import MediaGrid from "../components/MediaGrid.vue";
import MediaList from "../components/MediaList.vue";
import SearchResults from "../components/SearchResults.vue";
import FilterPanel from "../components/FilterPanel.vue";
import BatchBar from "../components/BatchBar.vue";
import MediaDetail from "../components/MediaDetail.vue";
import AddMediaMenu from "../components/AddMediaMenu.vue";
import ExportProgressDialog from "../components/ExportProgressDialog.vue";
import TagChip from "../components/TagChip.vue";

const album = useAlbumStore();
const tagStore = useTagStore();

onMounted(async () => {
  await Promise.all([album.loadMedia(), tagStore.loadTags()]);
});

// —— 顶部工具栏状态 ——
const showFilter = ref(false);
const addMenuVisible = ref(false);
const exportVisible = ref(false);

// —— 详情页状态 ——
const detailVisible = ref(false);
const detailIndex = ref(0);
const detailMedia = computed<Media | null>(() =>
  detailVisible.value ? (album.filteredMedia[detailIndex.value] ?? null) : null
);

function openDetail(m: Media) {
  detailIndex.value = album.filteredMedia.findIndex((x) => x.id === m.id);
  detailVisible.value = true;
}

function stepDetail(dir: 1 | -1) {
  const next = detailIndex.value + dir;
  if (next >= 0 && next < album.filteredMedia.length) detailIndex.value = next;
}

// —— 批量操作 ——
const batchTagDialog = ref(false);
const batchTagDraft = ref<number[]>([]);

function openBatchTag() {
  // 初始勾选 = 所有选中媒体共有的标签（交集）
  const selected = album.mediaList.filter((m) => album.selectedIds.has(m.id));
  const common = selected.reduce<number[]>((acc, m) => {
    return acc.filter((t) => m.tagIds.includes(t));
  }, selected[0] ? [...selected[0].tagIds] : []);
  batchTagDraft.value = common;
  batchTagDialog.value = true;
}

function saveBatchTag() {
  for (const id of album.selectedIds) {
    const m = album.mediaList.find((x) => x.id === id);
    if (m) m.tagIds = [...batchTagDraft.value];
  }
  batchTagDialog.value = false;
  ElMessage.success("批量打标签完成");
}

async function batchDelete() {
  const n = album.selectedIds.size;
  try {
    await ElMessageBox.confirm(
      `确定将选中的 ${n} 项媒体移入回收站吗？`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  for (const id of album.selectedIds) {
    const m = album.mediaList.find((x) => x.id === id);
    if (m) {
      m.isDeleted = true;
      m.deletedTime = Date.now();
    }
  }
  album.exitMultiSelect();
  ElMessage.success("已移入回收站");
}

function exportSelected() {
  exportVisible.value = true;
}
</script>

<template>
  <div class="album-view">
    <!-- 批量操作栏（多选模式） -->
    <BatchBar
      v-if="album.multiSelect"
      :count="album.selectedIds.size"
      @exit="album.exitMultiSelect"
      @tag="openBatchTag"
      @export="exportSelected"
      @delete="batchDelete"
    />

    <!-- 顶部工具栏 -->
    <header class="album-top" :class="{ 'with-batch': album.multiSelect }">
      <template v-if="!album.multiSelect">
        <span class="page-title">相册</span>
        <div class="search-box">
          <el-icon class="search-icon"><Search /></el-icon>
          <input
            v-model="album.searchQuery"
            class="search-input"
            type="text"
            placeholder="搜索文件名、描述、标签..."
          />
          <el-icon v-if="album.searchQuery" class="search-clear" @click="album.searchQuery = ''">
            <CircleCloseFilled />
          </el-icon>
        </div>
        <button class="icon-btn" :class="{ on: showFilter }" title="筛选" @click="showFilter = !showFilter">
          <el-icon :size="18"><Filter /></el-icon>
        </button>
        <div class="view-switch">
          <button class="icon-btn" :class="{ on: album.viewMode === 'grid' }" title="网格视图" @click="album.viewMode = 'grid'">
            <el-icon :size="17"><Grid /></el-icon>
          </button>
          <button class="icon-btn" :class="{ on: album.viewMode === 'list' }" title="列表视图" @click="album.viewMode = 'list'">
            <el-icon :size="17"><List /></el-icon>
          </button>
        </div>
        <button class="icon-btn add-btn" title="添加" @click="addMenuVisible = true">
          <el-icon :size="19"><Plus /></el-icon>
        </button>
      </template>
    </header>

    <!-- 标签胶囊筛选栏 -->
    <div v-show="!album.isSearchActive" class="tag-filter-bar">
      <TagChip
        :tag="{ id: -1, name: '全部', bgColor: '#f0f2f5', sortOrder: 0 }"
        neutral
        :active="album.tagFilterId === 'all'"
        @click="album.tagFilterId = 'all'"
      />
      <TagChip
        v-for="t in tagStore.sorted"
        :key="t.id"
        :tag="t"
        :active="album.tagFilterId === t.id"
        @click="album.tagFilterId = t.id"
      />
    </div>

    <!-- 筛选面板 -->
    <FilterPanel
      v-if="showFilter"
      :model-value="album.filter"
      @update:model-value="album.setFilter"
      @apply="showFilter = false"
      @clear="showFilter = false"
    />

    <!-- 内容区 -->
    <div class="album-body">
      <SearchResults
        v-if="album.isSearchActive"
        :items="album.filteredMedia"
        :query="album.searchQuery"
        @open="openDetail"
      />
      <template v-else>
        <MediaGrid
          v-if="album.viewMode === 'grid'"
          :items="album.filteredMedia"
          :multi-select="album.multiSelect"
          :selected-ids="album.selectedIds"
          @open="openDetail"
          @select="(m: Media) => album.toggleSelect(m.id)"
          @multi="(m: Media) => album.enterMultiSelect(m.id)"
        />
        <MediaList
          v-else
          :items="album.filteredMedia"
          :multi-select="album.multiSelect"
          :selected-ids="album.selectedIds"
          @open="openDetail"
          @select="(m: Media) => album.toggleSelect(m.id)"
          @multi="(m: Media) => album.enterMultiSelect(m.id)"
        />
      </template>

      <!-- 空状态 -->
      <div v-if="album.filteredMedia.length === 0" class="empty-state">
        <el-empty description="没有符合条件的媒体，试试调整筛选或添加媒体" />
      </div>
    </div>

    <!-- 浮层 -->
    <MediaDetail
      :visible="detailVisible"
      :media="detailMedia"
      :index="detailIndex"
      :total="album.filteredMedia.length"
      @close="detailVisible = false"
      @prev="stepDetail(-1)"
      @next="stepDetail(1)"
    />
    <AddMediaMenu v-model:visible="addMenuVisible" />
    <ExportProgressDialog v-model:visible="exportVisible" />

    <!-- 批量打标签 -->
    <el-dialog v-model="batchTagDialog" title="批量打标签" width="80%" align-center>
      <el-checkbox-group v-model="batchTagDraft" class="batch-tags">
        <el-checkbox v-for="t in tagStore.sorted" :key="t.id" :value="t.id">
          {{ t.name }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="batchTagDialog = false">取消</el-button>
        <el-button type="primary" @click="saveBatchTag">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.album-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  background: var(--card-bg);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.album-top {
  height: var(--app-topbar-height);
  flex: none;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 14px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--divider);
}

.page-title {
  flex: none;
  font-size: 16px;
  font-weight: 700;
  margin-right: 10px;
  color: var(--text-main);
}

.with-batch {
  visibility: hidden;
}

.search-box {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 10px;
  background: var(--input-bg);
  border-radius: 17px;
  border: 1px solid transparent;
  transition: border-color 0.15s, background 0.15s;
}

.search-box:focus-within {
  border-color: var(--accent);
  background: var(--card-bg);
}

.search-icon {
  color: var(--text-muted);
}

.search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: none;
  font-size: 13px;
  color: var(--text-main);
}

.search-clear {
  color: var(--text-muted);
  cursor: pointer;
}

.icon-btn {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  border-radius: 8px;
  cursor: pointer;
  color: var(--text-main);
}

.icon-btn:hover {
  background: var(--hover-bg);
}

.icon-btn.on {
  color: var(--accent);
  background: var(--accent-bg);
}

.add-btn {
  color: var(--accent);
}

.view-switch {
  display: flex;
  gap: 2px;
}

.tag-filter-bar {
  flex: none;
  display: flex;
  gap: 8px;
  padding: 8px 10px;
  overflow-x: auto;
  background: var(--card-bg);
  border-bottom: 1px solid var(--divider);
  scrollbar-width: none;
}

.tag-filter-bar::-webkit-scrollbar {
  display: none;
}

.album-body {
  flex: 1;
  overflow-y: auto;
  position: relative;
}

.empty-state {
  padding: 60px 0;
}

.batch-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
}
</style>

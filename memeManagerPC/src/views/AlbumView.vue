<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { listen } from "@tauri-apps/api/event";
import { useAlbumStore } from "../stores/album";
import { useTagStore } from "../stores/tags";
import { useSettingsStore } from "../stores/settings";
import { api } from "../api";
import type { Media } from "../types";
import { prewarmIcon } from "../utils/drag";
import MediaGrid from "../components/MediaGrid.vue";
import MediaList from "../components/MediaList.vue";
import SearchResults from "../components/SearchResults.vue";
import FilterPanel from "../components/FilterPanel.vue";
import BatchBar from "../components/BatchBar.vue";
import MediaDetail from "../components/MediaDetail.vue";
import AddMediaMenu from "../components/AddMediaMenu.vue";
import ExportProgressDialog from "../components/ExportProgressDialog.vue";
import ImportingDialog from "../components/ImportingDialog.vue";
import ImportDestinationDialog from "../components/ImportDestinationDialog.vue";
import TagChip from "../components/TagChip.vue";

const album = useAlbumStore();
const tagStore = useTagStore();
const settingsStore = useSettingsStore();

onMounted(async () => {
  await Promise.all([album.loadMedia(), tagStore.loadTags(), settingsStore.load()]);
  window.addEventListener("keydown", onKeydown);
  // 已索引目录文件变化（外部复制/删除）→ 自动刷新相册（防抖合并）
  // 监听失败（如浏览器调试环境无事件系统）不阻塞页面功能
  unlistenMediaChanged = await listen("media-changed", () => {
    if (refreshTimer !== undefined) window.clearTimeout(refreshTimer);
    refreshTimer = window.setTimeout(() => album.loadMedia(), 300);
  }).catch(() => undefined);
  // 导出进度事件
  unlistenExportProgress = await listen<[number, number]>(
    "export-progress",
    (e) => {
      exporting.current = e.payload[0];
      exporting.total = e.payload[1];
    }
  ).catch(() => undefined);
});

let unlistenMediaChanged: (() => void) | undefined;
let unlistenExportProgress: (() => void) | undefined;
let refreshTimer: number | undefined;

onBeforeUnmount(() => {
  window.removeEventListener("keydown", onKeydown);
  unlistenMediaChanged?.();
  unlistenExportProgress?.();
  if (refreshTimer !== undefined) window.clearTimeout(refreshTimer);
});

// —— 顶部工具栏状态 ——
// 筛选面板默认关闭，仅通过工具栏筛选按钮开关
const showFilter = ref(false);
const addMenuVisible = ref(false);
const searchInputEl = ref<HTMLInputElement | null>(null);

/** 多选快捷键：Ctrl/Cmd+A 全选可见项，Esc 退出多选；Ctrl/Cmd+F 聚焦搜索框 */
function onKeydown(e: KeyboardEvent) {
  const inEditable =
    e.target instanceof HTMLElement &&
    (e.target.tagName === "INPUT" ||
      e.target.tagName === "TEXTAREA" ||
      e.target.isContentEditable);

  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "f") {
    e.preventDefault();
    searchInputEl.value?.focus();
    searchInputEl.value?.select();
    return;
  }
  if (e.key === "Escape") {
    if (album.multiSelect) album.exitMultiSelect();
    else if (album.isSearchActive && !inEditable) {
      album.searchQuery = "";
    }
    return;
  }
  if (album.multiSelect && (e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "a") {
    e.preventDefault();
    album.selectAll(album.filteredMedia.map((m) => m.id));
  }
}

/** 搜索框内按 Esc：有内容先清空，空内容失焦交还全局（退出多选等） */
function onSearchKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") {
    if (album.searchQuery) {
      e.stopPropagation();
      album.searchQuery = "";
    } else {
      searchInputEl.value?.blur();
    }
  }
}

/** 顶部标签栏：点击切换选中（多选） */
function toggleTagFilter(id: number) {
  const arr = album.tagFilterIds;
  album.tagFilterIds = arr.includes(id)
    ? arr.filter((x) => x !== id)
    : [...arr, id];
}

// —— 导出状态（进度由 export-progress 事件驱动）——
const exporting = reactive({ visible: false, current: 0, total: 0 });

// —— 详情页状态 ——
const detailVisible = ref(false);
const detailIndex = ref(0);
const detailMedia = computed<Media | null>(() =>
  detailVisible.value ? (album.filteredMedia[detailIndex.value] ?? null) : null
);

function openDetail(m: Media) {
  detailIndex.value = album.filteredMedia.findIndex((x) => x.id === m.id);
  detailVisible.value = true;
  // 预热当前大图的拖拽图标
  prewarmIcon(m);
}

function stepDetail(dir: 1 | -1) {
  const next = detailIndex.value + dir;
  if (next >= 0 && next < album.filteredMedia.length) detailIndex.value = next;
}

// 列表变化（如详情页内删除、外部刷新）→ 校正详情索引：越界则回退到最后一张，清空则关闭
watch(
  () => album.filteredMedia.length,
  (len) => {
    if (!detailVisible.value) return;
    if (len === 0) {
      detailVisible.value = false;
    } else if (detailIndex.value > len - 1) {
      detailIndex.value = len - 1;
    }
  }
);

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
  if (batchTagDraft.value.length === 0) {
    ElMessage.info("未选择标签");
    return;
  }
  const ids = [...album.selectedIds];
  api
    .replaceMediaTags(ids, batchTagDraft.value)
    .then(() => {
      batchTagDialog.value = false;
      ElMessage.success("批量打标签完成");
      return album.loadMedia();
    });
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
  const ids = [...album.selectedIds];
  api.deleteMedia(ids).then(async () => {
    album.exitMultiSelect();
    ElMessage.success("已移入回收站");
    await album.loadMedia();
  });
}

// —— 导入文件夹 ——
const importing = ref(false);

async function importFolder() {
  const dir = await api.pickDirectory();
  if (!dir) return;
  importing.value = true;
  try {
    const sum = await api.scanFolder(dir, true);
    ElMessage.success(
      `导入完成：新增 ${sum.added} 张，更新 ${sum.updated} 张，清理 ${sum.removed} 条失效记录`
    );
    await Promise.all([album.loadMedia(), tagStore.loadTags()]);
  } catch (e) {
    ElMessage.error(`导入失败：${e}`);
  } finally {
    importing.value = false;
  }
}

function exportSelected() {
  const items = album.mediaList.filter((m) => album.selectedIds.has(m.id));
  runExport(items);
}

// —— 导出为 .mpak ——
function tagNamesOf(m: Media): string[] {
  return tagStore.tags
    .filter((t) => m.tagIds.includes(t.id))
    .map((t) => t.name);
}

async function runExport(items: Media[]) {
  if (items.length === 0) {
    ElMessage.info("没有可导出的媒体");
    return;
  }
  const destDir = await api.pickDirectory();
  if (!destDir) return;

  exporting.visible = true;
  exporting.current = 0;
  exporting.total = items.length;
  const maxSize = (settingsStore.settings.shardSize || 100) * 1024 * 1024;
  const payload = items.map((m) => ({
    name: m.fileName,
    type: m.mediaType,
    filePath: m.filePath,
    width: m.width,
    height: m.height,
    description: m.description || null,
    createdAt: m.importTime,
    tags: tagNamesOf(m),
  }));

  try {
    const result = await api.exportPak(payload, maxSize, destDir);
    await album.loadMedia();
    ElMessage.success(
      `导出完成：${result.shards.length} 个分片，共 ${result.totalMedia} 张`
    );
  } catch (e) {
    ElMessage.error(`导出失败：${e}`);
  } finally {
    exporting.visible = false;
  }
}

// —— 导入单张/批量文件 ——
async function importFiles(paths: string[]) {
  if (paths.length === 0) return;
  importing.value = true;
  try {
    const destDir = await api.defaultMediaDir();
    const sum = await api.importFiles(paths, destDir);
    await album.loadMedia();
    ElMessage.success(`已导入 ${sum.added} 张，更新 ${sum.updated} 张`);
  } catch (e) {
    ElMessage.error(`导入失败：${e}`);
  } finally {
    importing.value = false;
  }
}

// —— 分享：复制选中媒体到剪贴板（多选时取第一张）——
async function shareSelected() {
  const first = album.mediaList.find((m) => album.selectedIds.has(m.id));
  if (!first) {
    ElMessage.info("请选择要分享的媒体");
    return;
  }
  try {
    const msg = await api.copyToClipboard(first.filePath);
    ElMessage.success(msg);
  } catch (e) {
    try {
      await navigator.clipboard.writeText(first.filePath);
      ElMessage.warning(`复制图像失败（${e}），已降级复制文件路径`);
    } catch {
      ElMessage.error(`复制失败：${e}`);
    }
  }
}

// —— 导入 .mpak 分片 ——
const mpakState = reactive({
  file: "",
  defaultDir: "",
  destDialog: false,
  importing: false,
  stage: "",
});

async function importMpak() {
  const file = await api.pickMpakFile();
  if (!file) return;
  mpakState.file = file;
  mpakState.defaultDir = await api.defaultMediaDir();
  mpakState.destDialog = true;
}

async function startMpakImport(destDir: string) {
  mpakState.destDialog = false;
  mpakState.importing = true;
  try {
    mpakState.stage = "正在解包并校验 .mpak …";
    const res = await api.importPak(mpakState.file, destDir);
    mpakState.stage = "正在扫描媒体并写入媒体库 …";
    const sum = await api.scanFolder(destDir, true);
    await Promise.all([album.loadMedia(), tagStore.loadTags()]);
    mpakState.importing = false;
    const failMsg =
      res.failedNames.length > 0 ? `\n失败文件：${res.failedNames.join("、")}` : "";
    await ElMessageBox.alert(
      `成功 ${res.succeeded} 张，跳过 ${res.skipped} 张，失败 ${res.failed} 张${failMsg}` +
        `\n媒体库：新增 ${sum.added} 条，更新 ${sum.updated} 条，清理 ${sum.removed} 条失效记录`,
      "导入结果",
      { confirmButtonText: "好" }
    );
  } catch (e) {
    mpakState.importing = false;
    ElMessage.error(`导入失败：${e}`);
  }
}
</script>

<template>
  <div class="album-view">
    <!-- 批量操作栏（多选模式） -->
    <BatchBar
      v-if="album.multiSelect"
      :count="album.selectedIds.size"
      :total="album.filteredMedia.length"
      @exit="album.exitMultiSelect"
      @tag="openBatchTag"
      @export="exportSelected"
      @delete="batchDelete"
      @share="shareSelected"
      @select-all="album.selectAll(album.filteredMedia.map((m) => m.id))"
    />

    <!-- 顶部工具栏 -->
    <header class="album-top" :class="{ 'with-batch': album.multiSelect }">
      <template v-if="!album.multiSelect">
        <span class="page-title">相册</span>
        <div class="search-box">
          <el-icon class="search-icon"><Search /></el-icon>
          <input
            ref="searchInputEl"
            v-model="album.searchQuery"
            class="search-input"
            type="text"
            placeholder="搜索文件名、描述、标签…（Ctrl+F）"
            @keydown="onSearchKeydown"
          />
          <el-icon v-if="album.searchQuery" class="search-clear" @click="album.searchQuery = ''">
            <CircleCloseFilled />
          </el-icon>
        </div>
        <button
          class="icon-btn"
          :class="{ on: album.sortMode }"
          title="拖拽排序（开启后拖拽缩略图进行排序，关闭时拖拽拖出文件）"
          @click="album.toggleSortMode"
        >
          <el-icon :size="18"><Sort /></el-icon>
        </button>
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

    <!-- 标签胶囊筛选栏（多选，或逻辑） -->
    <div v-show="!album.isSearchActive" class="tag-filter-bar">
      <TagChip
        :tag="{ id: -1, name: '全部', bgColor: '#f0f2f5', sortOrder: 0 }"
        neutral
        :active="album.tagFilterIds.length === 0"
        @click="album.tagFilterIds = []"
      />
      <TagChip
        v-for="t in tagStore.sorted"
        :key="t.id"
        :tag="t"
        :active="album.tagFilterIds.includes(t.id)"
        @click="toggleTagFilter(t.id)"
      />
    </div>

    <!-- 筛选面板（更改即生效，开关只由工具栏按钮控制） -->
    <FilterPanel
      v-if="showFilter"
      :model-value="album.filter"
      @update:model-value="album.setFilter"
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
          :columns="settingsStore.settings.gridCols"
          :sort-mode="album.sortMode"
          @open="openDetail"
          @select="(m: Media) => album.toggleSelect(m.id)"
          @multi="(m: Media) => album.enterMultiSelect(m.id)"
          @reorder="album.applyOrder"
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

      <!-- 空状态：区分「库为空」与「搜索/筛选无结果」 -->
      <div v-if="album.filteredMedia.length === 0" class="empty-state">
        <template v-if="album.mediaList.length === 0">
          <div class="empty-emoji">🗂️</div>
          <div class="empty-title">还没有任何媒体</div>
          <div class="empty-sub">导入一个文件夹，开始整理你的表情包库</div>
          <el-button type="primary" round :loading="importing" @click="importFolder">
            <el-icon><FolderOpened /></el-icon>
            <span>导入文件夹</span>
          </el-button>
        </template>
        <template v-else>
          <div class="empty-emoji">🔍</div>
          <div class="empty-title">没有匹配的媒体</div>
          <div class="empty-sub">换个关键词，或清除当前筛选条件试试</div>
          <div class="empty-actions">
            <el-button v-if="album.isSearchActive" round @click="album.searchQuery = ''">
              <el-icon><CircleCloseFilled /></el-icon>
              <span>清除搜索</span>
            </el-button>
            <el-button
              v-if="album.tagFilterIds.length > 0"
              round
              @click="album.tagFilterIds = []"
            >
              <span>清除标签筛选</span>
            </el-button>
            <el-button
              v-if="album.filterActive"
              round
              @click="album.clearFilter(); showFilter = false"
            >
              <span>清除筛选条件</span>
            </el-button>
          </div>
        </template>
      </div>
    </div>

    <!-- 浮层 -->
    <AddMediaMenu
      v-model:visible="addMenuVisible"
      @import-folder="importFolder"
      @import-mpak="importMpak"
      @import-files="importFiles"
    />

    <!-- .mpak 导入：目标位置二选一 + 导入中反馈 -->
    <ImportDestinationDialog
      v-model:visible="mpakState.destDialog"
      :default-dir="mpakState.defaultDir"
      @choose-default="startMpakImport(mpakState.defaultDir)"
      @choose-custom="
        api.pickDirectory().then((dir) => {
          if (dir) return startMpakImport(dir);
        })
      "
    />
    <ImportingDialog v-model:visible="mpakState.importing" :stage="mpakState.stage" />
    <ExportProgressDialog
      v-model:visible="exporting.visible"
      :current="exporting.current"
      :total="exporting.total"
      @cancel="exporting.visible = false"
    />

    <MediaDetail
      :visible="detailVisible"
      :media="detailMedia"
      :index="detailIndex"
      :total="album.filteredMedia.length"
      @close="detailVisible = false"
      @prev="stepDetail(-1)"
      @next="stepDetail(1)"
      @changed="album.loadMedia"
      @export="detailMedia && runExport([detailMedia])"
    />

    <!-- 批量打标签 -->
    <el-dialog v-model="batchTagDialog" title="批量打标签" width="min(460px, 92%)" align-center>
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
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;
}

.icon-btn:hover {
  background: var(--hover-bg);
}

.icon-btn.on {
  color: var(--accent);
  background: var(--accent-bg);
}

.add-btn {
  color: #fff;
  background: var(--accent);
  box-shadow: 0 2px 6px rgba(246, 130, 31, 0.35);
}

.add-btn:hover {
  color: #fff;
  background: var(--accent-strong);
}

.view-switch {
  display: flex;
  gap: 2px;
  padding: 2px;
  background: var(--input-bg);
  border-radius: 9px;
}

.view-switch .icon-btn {
  width: 30px;
  height: 30px;
  border-radius: 7px;
}

.view-switch .icon-btn.on {
  background: var(--card-bg);
  box-shadow: var(--shadow-sm);
}

.tag-filter-bar {
  flex: none;
  display: flex;
  gap: 8px;
  padding: 10px 14px;
  overflow-x: auto;
  background: var(--card-bg);
  border-bottom: 1px solid var(--divider);
  scrollbar-width: none;
  /* 两端渐隐提示可横向滚动 */
  mask-image: linear-gradient(to right, transparent, #000 14px, #000 calc(100% - 14px), transparent);
  -webkit-mask-image: linear-gradient(to right, transparent, #000 14px, #000 calc(100% - 14px), transparent);
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
  padding: 60px 0 80px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  text-align: center;
}

.empty-emoji {
  font-size: 52px;
  line-height: 1;
  margin-bottom: 10px;
  /* 轻微呼吸动画，避免大面积留白显得死板 */
  animation: empty-float 3s ease-in-out infinite;
}

@keyframes empty-float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-6px);
  }
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-main);
}

.empty-sub {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 14px;
}

.empty-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
}

.batch-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
}
</style>

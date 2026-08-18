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
  const ids = [...album.selectedIds];
  const clearing = batchTagDraft.value.length === 0;
  api
    .replaceMediaTags(ids, batchTagDraft.value)
    .then(() => {
      batchTagDialog.value = false;
      ElMessage.success(clearing ? "已清空选中媒体的标签" : "批量打标签完成");
      return album.loadMedia();
    })
    .catch((e) => ElMessage.error(`批量打标签失败：${e}`));
}

async function batchDelete() {
  const n = album.selectedIds.size;
  // 回收站保留天数设为 0：删除即永久删除（连同源文件），不进回收站
  const permanent = settingsStore.settings.recycleDays === 0;
  try {
    await ElMessageBox.confirm(
      permanent
        ? `当前设置保留天数为 0，选中的 ${n} 项媒体将被永久删除（连同源文件），不可恢复。`
        : `确定将选中的 ${n} 项媒体移入回收站吗？`,
      permanent ? "永久删除" : "删除确认",
      { type: "warning", confirmButtonText: permanent ? "永久删除" : "删除", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  const ids = [...album.selectedIds];
  const req = permanent ? api.purgeMedia(ids) : api.deleteMedia(ids);
  try {
    await req;
  } catch (e) {
    ElMessage.error(`删除失败：${e}`);
    return;
  }
  album.exitMultiSelect();
  ElMessage.success(permanent ? "已永久删除" : "已移入回收站");
  await album.loadMedia();
}

// —— 导入文件夹 ——
const importing = ref(false);

/** 时间分组依据（来自设置：拍摄/导入日期），传给网格/列表 */
const groupField = computed<"takenTime" | "importTime">(() =>
  settingsStore.settings.groupBy === "import" ? "importTime" : "takenTime"
);

/** 导入目标目录：设置默认存储为自定义目录时用之，否则程序默认媒体目录 */
async function resolveImportDest(): Promise<string> {
  const s = settingsStore.settings;
  if (s.defaultStorage === "custom" && s.customDir) return s.customDir;
  return api.defaultMediaDir();
}

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
    if (String(e).includes("取消")) {
      ElMessage.info("已取消导出");
    } else {
      ElMessage.error(`导出失败：${e}`);
    }
  } finally {
    exporting.visible = false;
  }
}

/** 取消导出：对话框保持到后端任务返回（成功中止或完成）后由 finally 关闭 */
function cancelExport() {
  api.cancelExport().catch(() => {});
}

// —— 导入单张/批量文件 ——
async function importFiles(paths: string[]) {
  if (paths.length === 0) return;
  importing.value = true;
  try {
    const destDir = await resolveImportDest();
    const res = await api.importFiles(paths, destDir);
    await album.loadMedia();
    ElMessage.success(`已导入 ${res.copied} 张，库新增 ${res.scan.added} 条`);
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
  mpakState.defaultDir = await resolveImportDest();
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
          :group-field="groupField"
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
          :group-field="groupField"
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
      @cancel="cancelExport"
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
  background: var(--header-bg);
  backdrop-filter: blur(20px) saturate(1.8);
  -webkit-backdrop-filter: blur(20px) saturate(1.8);
  border-bottom: 1px solid var(--divider);
}

.page-title {
  flex: none;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
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
  height: 32px;
  padding: 0 12px;
  background: var(--input-bg);
  border-radius: 16px;
  border: 1px solid var(--divider);
  transition:
    box-shadow 0.18s var(--ease-out),
    border-color 0.18s var(--ease-out),
    background-color 0.18s var(--ease-out);
}

/* 聚焦：分层羽化的柔和光晕（hairline 描边 + 阶梯式光环 + 光晕扩散），
   底色浮起为卡片色，边框染上主题色，营造真实“发光”而非硬环 */
.search-box:focus-within {
  border-color: color-mix(in srgb, var(--accent) 38%, transparent);
  background: var(--card-bg);
  box-shadow:
    0 0 0 1px color-mix(in srgb, var(--accent) 12%, transparent),
    0 0 0 3px color-mix(in srgb, var(--accent) 8%, transparent),
    0 0 0 6px color-mix(in srgb, var(--accent) 4%, transparent),
    0 8px 24px -8px color-mix(in srgb, var(--accent) 40%, transparent);
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
  transition:
    background 0.15s ease-out,
    color 0.15s ease-out,
    box-shadow 0.15s ease-out,
    transform 0.12s var(--ease-out);
}

.icon-btn:hover {
  background: var(--hover-bg);
}

/* 按压即时反馈（apple-design §1） */
.icon-btn:active {
  transform: scale(0.94);
}

.icon-btn.on {
  color: var(--accent);
  background: var(--accent-bg);
}

.add-btn {
  color: var(--accent-contrast);
  background: var(--accent);
  box-shadow: 0 2px 6px color-mix(in srgb, var(--accent) 30%, transparent);
}

.add-btn:hover {
  color: var(--accent-contrast);
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
  background: var(--header-bg);
  backdrop-filter: blur(20px) saturate(1.8);
  -webkit-backdrop-filter: blur(20px) saturate(1.8);
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

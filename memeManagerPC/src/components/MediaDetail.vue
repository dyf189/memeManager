<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { revealItemInDir } from "@tauri-apps/plugin-opener";
import { dragMediaOut } from "../utils/drag";
import { fileSrc } from "../utils/asset";
import type { Media } from "../types";
import { useTagStore } from "../stores/tags";
import { api } from "../api";
import { formatDateTime, formatResolution, formatSize } from "../utils/format";
import { SOURCE_LABELS } from "../utils/constants";
import TagChip from "./TagChip.vue";

const props = defineProps<{
  visible: boolean;
  media: Media | null;
  /** 当前媒体在相册中的位置（用于前后切换） */
  index: number;
  total: number;
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (e: "prev"): void;
  (e: "next"): void;
  (e: "changed"): void; // 描述/标签变更后，通知父级刷新
  (e: "export"): void; // 请求导出当前媒体
}>();

const tagStore = useTagStore();

const stageSrc = computed(() =>
  props.media ? fileSrc(props.media.filePath) : ""
);
const isVisual = computed(() => props.media?.mediaType !== "video");

// 点击大图：切换 原始尺寸 / 适应窗口
const zoomed = ref(false);
watch(
  () => props.media,
  () => {
    zoomed.value = false;
    editingDesc.value = false;
    descDraft.value = props.media?.description ?? "";
  }
);

// 键盘导航：←/→ 切换上一张/下一张，Esc 关闭（输入框聚焦时不拦截）
function onKeydown(e: KeyboardEvent) {
  const t = e.target as HTMLElement | null;
  if (t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.isContentEditable)) return;
  if (e.key === "ArrowLeft") emit("prev");
  else if (e.key === "ArrowRight") emit("next");
  else if (e.key === "Escape" && !tagDialog.value) emit("close");
}

watch(
  () => props.visible,
  (v) => {
    if (v) window.addEventListener("keydown", onKeydown);
    else window.removeEventListener("keydown", onKeydown);
  },
  { immediate: true }
);

onBeforeUnmount(() => window.removeEventListener("keydown", onKeydown));

// 编辑描述
const editingDesc = ref(false);
const descDraft = ref("");

// 打标签对话框
const tagDialog = ref(false);
const tagDraft = ref<number[]>([]);

const mediaTags = computed(() => {
  if (!props.media) return [];
  return tagStore.sorted.filter((t) => props.media!.tagIds.includes(t.id));
});

/** 弹窗内当前已勾选的标签（按全局顺序展示） */
const draftTags = computed(() =>
  tagStore.sorted.filter((t) => tagDraft.value.includes(t.id))
);

function toggleTag(id: number) {
  const i = tagDraft.value.indexOf(id);
  if (i >= 0) tagDraft.value.splice(i, 1);
  else tagDraft.value.push(id);
}

const sourceLabel = computed(() =>
  props.media ? SOURCE_LABELS[props.media.source as keyof typeof SOURCE_LABELS] ?? props.media.source : ""
);

function startEditDesc() {
  descDraft.value = props.media?.description ?? "";
  editingDesc.value = true;
}

async function saveDesc() {
  if (!props.media) return;
  await api.setDescription(props.media.id, descDraft.value.trim());
  props.media.description = descDraft.value.trim();
  editingDesc.value = false;
  ElMessage.success("描述已保存");
  emit("changed");
}

function openTagDialog() {
  tagDraft.value = props.media ? [...props.media.tagIds] : [];
  tagDialog.value = true;
}

async function saveTags() {
  if (!props.media) return;
  await api.replaceMediaTags([props.media.id], tagDraft.value);
  props.media.tagIds = [...tagDraft.value];
  tagDialog.value = false;
  ElMessage.success("标签已更新");
  emit("changed");
}

async function removeTag(tagId: number) {
  if (!props.media) return;
  const next = props.media.tagIds.filter((id) => id !== tagId);
  await api.replaceMediaTags([props.media.id], next);
  props.media.tagIds = next;
  emit("changed");
}

/** 复制当前媒体图像到剪贴板（失败时降级复制文件路径） */
async function copyToClipboard() {
  if (!props.media) return;
  const path = props.media.filePath;
  try {
    const msg = await api.copyToClipboard(path);
    ElMessage.success(msg);
  } catch (e) {
    try {
      await navigator.clipboard.writeText(path);
      ElMessage.warning(`复制图像失败（${e}），已降级复制文件路径`);
    } catch {
      ElMessage.error(`复制失败：${e}`);
    }
  }
}

// 右上角菜单操作
function menuAction(action: string) {
  if (action === "copy") copyToClipboard();
  else if (action === "export") emit("export");
  else if (action === "reveal") {
    // 在文件管理器中显示该文件
    revealItemInDir(props.media!.filePath).catch((e) =>
      ElMessage.error(`打开所在文件夹失败：${e}`)
    );
  } else if (action === "delete") {
    // 删除（进回收站）后不直接关闭：父级刷新列表并校正索引，自动展示下一张
    api
      .deleteMedia([props.media!.id])
      .then(async () => {
        ElMessage.success("已移入回收站");
        emit("changed");
      })
      .catch((e) => ElMessage.error(`删除失败：${e}`));
  }
}
</script>

<template>
  <teleport to="body">
    <transition name="fade">
      <div v-if="visible && media" class="detail-overlay">
        <!-- 顶部栏 -->
        <header class="detail-top">
          <button class="icon-btn" @click="emit('close')">
            <el-icon :size="18"><ArrowLeft /></el-icon>
          </button>
          <span class="detail-title text-ellipsis">{{ media.fileName }}</span>
          <button class="icon-btn" title="复制到剪贴板" @click="copyToClipboard">
            <el-icon :size="18"><CopyDocument /></el-icon>
          </button>
          <el-dropdown trigger="click" @command="menuAction">
            <button class="icon-btn">
              <el-icon :size="18"><MoreFilled /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="copy">复制到剪贴板</el-dropdown-item>
                <el-dropdown-item command="export">导出为 .mpak</el-dropdown-item>
                <el-dropdown-item command="reveal">打开所在文件夹</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除（进回收站）</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </header>

        <!-- 大图区（可直接拖拽到其它应用/桌面；点击切换 原始尺寸/适应窗口） -->
        <div class="detail-stage" :class="{ zoomed }">
          <img
            v-if="isVisual"
            :src="stageSrc"
            class="stage-img"
            alt=""
            draggable="true"
            :title="zoomed ? '点击缩小' : '点击查看原始尺寸'"
            @click="zoomed = !zoomed"
            @dragstart="(e: DragEvent) => media && dragMediaOut(e, media)"
          />
          <span v-else class="stage-placeholder">🎬 视频预览（待接入）</span>
          <span v-if="media.mediaType === 'gif'" class="stage-badge">GIF</span>
          <span v-else-if="media.mediaType === 'video'" class="stage-badge">▶ 视频</span>
        </div>

        <!-- 信息区 -->
        <section class="detail-info">
          <div class="info-row">
            <span class="info-label">描述</span>
            <template v-if="editingDesc">
              <el-input
                v-model="descDraft"
                type="textarea"
                :rows="2"
                placeholder="添加描述..."
                class="info-desc-input"
              />
              <div class="info-actions">
                <el-button size="small" type="primary" @click="saveDesc">保存</el-button>
                <el-button size="small" @click="editingDesc = false">取消</el-button>
              </div>
            </template>
            <template v-else>
              <span v-if="media.description" class="info-value">{{ media.description }}</span>
              <span v-else class="info-placeholder">暂无描述</span>
              <el-button size="small" link type="primary" @click="startEditDesc">
                {{ media.description ? "编辑" : "添加" }}
              </el-button>
            </template>
          </div>

          <div class="info-row">
            <span class="info-label">标签</span>
            <div class="info-tags">
              <TagChip v-for="t in mediaTags" :key="t.id" :tag="t" closable @close="removeTag(t.id)" />
              <el-button size="small" link type="primary" @click="openTagDialog">+ 添加标签</el-button>
            </div>
          </div>

          <div class="info-grid">
            <div><span class="info-label">来源</span>{{ sourceLabel }}</div>
            <div><span class="info-label">时间</span>{{ formatDateTime(media.takenTime) }}</div>
            <div><span class="info-label">大小</span>{{ formatSize(media.fileSize) }}</div>
            <div><span class="info-label">分辨率</span>{{ formatResolution(media.width ?? 0, media.height ?? 0) }}</div>
          </div>

          <!-- 位置：完整路径（便于多目录管理时确认来源） -->
          <div class="info-location">
            <span class="info-label">位置</span>
            <span class="info-path text-ellipsis">{{ media.filePath }}</span>
          </div>
        </section>

        <!-- 底部操作条 -->
        <footer class="detail-bottom">
          <button class="nav-btn" :disabled="index <= 0" @click="emit('prev')">
            <el-icon><ArrowLeft /></el-icon> 上一张
          </button>
          <div class="detail-center">
            <span class="detail-pos" :title="'← / → 键快速切换'">{{ index + 1 }} / {{ total }}</span>
            <el-button type="primary" round @click="menuAction('export')">
              <el-icon><Download /></el-icon>
              <span>导出</span>
            </el-button>
          </div>
          <button class="nav-btn" :disabled="index >= total - 1" @click="emit('next')">
            下一张 <el-icon><ArrowRight /></el-icon>
          </button>
        </footer>

        <!-- 打标签对话框 -->
        <el-dialog v-model="tagDialog" title="编辑标签" width="min(480px, 92%)" align-center>
          <!-- 已选标签：彩色胶囊，可直接移除 -->
          <div class="tag-section">
            <div class="tag-section-head">
              <span class="tag-section-title">已选标签</span>
              <span class="tag-section-count">{{ tagDraft.length }}</span>
            </div>
            <div v-if="draftTags.length" class="tag-selected">
              <TagChip
                v-for="t in draftTags"
                :key="t.id"
                :tag="t"
                closable
                @close="toggleTag(t.id)"
              />
            </div>
            <div v-else class="tag-hint">尚未选择标签，从下方标签中挑选</div>
          </div>

          <!-- 全部标签：点击切换选中 -->
          <div class="tag-section">
            <div class="tag-section-head">
              <span class="tag-section-title">全部标签</span>
            </div>
            <div v-if="tagStore.sorted.length" class="tag-picker">
              <TagChip
                v-for="t in tagStore.sorted"
                :key="t.id"
                :tag="t"
                ghost
                :active="tagDraft.includes(t.id)"
                @click="toggleTag(t.id)"
              />
            </div>
            <div v-else class="tag-hint">暂无标签，可到「标签管理」页创建</div>
          </div>

          <template #footer>
            <el-button @click="tagDialog = false">取消</el-button>
            <el-button type="primary" @click="saveTags">保存</el-button>
          </template>
        </el-dialog>
      </div>
    </transition>
  </teleport>
</template>

<style scoped>
.detail-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: var(--card-bg);
  display: flex;
  flex-direction: column;
}

.detail-top {
  height: var(--app-topbar-height);
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border-bottom: 1px solid var(--divider);
}

.icon-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  border-radius: 8px;
  cursor: pointer;
  color: var(--text-main);
  transition: background 0.15s ease-out, transform 0.12s var(--ease-out);
}

.icon-btn:hover {
  background: var(--hover-bg);
}

.icon-btn:active {
  transform: scale(0.94);
}

.detail-title {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.detail-stage {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  margin: 10px;
  border-radius: 12px;
  overflow: hidden;
  background: var(--input-bg);
  /* 棋盘格：透明 PNG 边界可见，也让大图区与信息区形成层次 */
  background-image: conic-gradient(
    var(--checker) 0 25%,
    transparent 0 50%,
    var(--checker) 0 75%,
    transparent 0
  );
  background-size: 16px 16px;
}

/* 放大模式：容器滚动，图片按原始像素显示 */
.detail-stage.zoomed {
  align-items: flex-start;
  justify-content: flex-start;
  overflow: auto;
  cursor: zoom-out;
}

.stage-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  display: block;
  cursor: zoom-in;
}

.detail-stage.zoomed .stage-img {
  max-width: none;
  max-height: none;
  width: auto;
  height: auto;
  cursor: zoom-out;
}

.stage-placeholder {
  color: var(--text-muted);
  font-size: 14px;
}

.stage-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
}

.detail-info {
  flex: none;
  padding: 10px 14px 6px;
  border-top: 1px solid var(--divider);
  max-height: 38%;
  overflow-y: auto;
}

.info-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 0;
}

.info-label {
  width: 44px;
  flex: none;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 24px;
}

.info-value {
  flex: 1;
  font-size: 13px;
  line-height: 24px;
}

.info-placeholder {
  flex: 1;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 24px;
}

.info-desc-input {
  flex: 1;
}

.info-actions {
  display: flex;
  gap: 6px;
  margin-top: 6px;
}

.info-tags {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 12px;
  padding: 8px 0 4px 52px;
  font-size: 12px;
  color: var(--text-secondary);
}

.info-grid .info-label {
  display: inline;
  margin-right: 6px;
}

.info-location {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0 4px;
  font-size: 12px;
  color: var(--text-muted);
  border-top: 1px dashed var(--divider);
  margin-top: 4px;
}

.info-path {
  flex: 1;
  min-width: 0;
  direction: rtl; /* 长路径省略号靠左显示，保留目录尾部可读 */
  text-align: left;
}

.detail-bottom {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px calc(10px + env(safe-area-inset-bottom));
  border-top: 1px solid var(--divider);
}

.nav-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: none;
  font-size: 14px;
  color: var(--text-main);
  cursor: pointer;
  padding: 9px 14px;
  border-radius: 9px;
  transition: background 0.15s ease-out, transform 0.12s var(--ease-out);
}

.nav-btn:not(:disabled):hover {
  background: var(--hover-bg);
}

.nav-btn:not(:disabled):active {
  transform: scale(0.96);
}

.nav-btn:disabled {
  color: var(--text-muted);
  cursor: not-allowed;
}

.detail-center {
  display: flex;
  align-items: center;
  gap: 14px;
}

.detail-pos {
  font-size: 12px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

/* ===== 打标签弹窗 ===== */
.tag-section + .tag-section {
  margin-top: 16px;
}

.tag-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.tag-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0.01em;
}

.tag-section-count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--accent-bg);
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-variant-numeric: tabular-nums;
}

/* 已选标签容器：虚线框 + 浅底，空时给出提示 */
.tag-selected {
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 6px;
  min-height: 46px;
  max-height: 108px;
  overflow-y: auto;
  padding: 9px 10px;
  background: var(--input-bg);
  border: 1px dashed var(--divider);
  border-radius: 10px;
}

.tag-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 46px;
  padding: 9px 10px;
  background: var(--input-bg);
  border: 1px dashed var(--divider);
  border-radius: 10px;
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
}

/* 可选标签网格：点击切换选中 */
.tag-picker {
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
  padding: 2px;
}

/* 进场：从 0.985 缩放 + 淡入（真实物体不会从虚无中出现） */
.fade-enter-active {
  transition: opacity 0.2s var(--ease-out), transform 0.2s var(--ease-out);
}

.fade-leave-active {
  transition: opacity 0.13s ease-out;
}

.fade-enter-from {
  opacity: 0;
  transform: scale(0.985);
}

.fade-leave-to {
  opacity: 0;
}
</style>

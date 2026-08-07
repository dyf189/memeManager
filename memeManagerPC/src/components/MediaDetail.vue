<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { convertFileSrc } from "@tauri-apps/api/core";
import { revealItemInDir } from "@tauri-apps/plugin-opener";
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
  props.media ? convertFileSrc(props.media.filePath) : ""
);
const isVisual = computed(() => props.media?.mediaType !== "video");

// 编辑描述
const editingDesc = ref(false);
const descDraft = ref("");

watch(
  () => props.media,
  (m) => {
    editingDesc.value = false;
    descDraft.value = m?.description ?? "";
  }
);

// 打标签对话框
const tagDialog = ref(false);
const tagDraft = ref<number[]>([]);

const mediaTags = computed(() => {
  if (!props.media) return [];
  return tagStore.sorted.filter((t) => props.media!.tagIds.includes(t.id));
});

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

// 右上角菜单操作
function menuAction(action: string) {
  if (action === "copy") {
    const path = props.media!.filePath;
    api
      .copyToClipboard(path)
      .then((msg) => ElMessage.success(msg))
      .catch(async (e) => {
        // 兜底：Web 剪贴板复制路径文本
        try {
          await navigator.clipboard.writeText(path);
          ElMessage.warning(`复制图像失败（${e}），已降级复制文件路径`);
        } catch {
          ElMessage.error(`复制失败：${e}`);
        }
      });
  } else if (action === "export") emit("export");
  else if (action === "reveal") {
    // 在文件管理器中显示该文件
    revealItemInDir(props.media!.filePath).catch((e) =>
      ElMessage.error(`打开所在文件夹失败：${e}`)
    );
  } else if (action === "delete") {
    // 删除（进回收站）
    api
      .deleteMedia([props.media!.id])
      .then(async () => {
        ElMessage.success("已移入回收站");
        emit("changed");
        emit("close");
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

        <!-- 大图区 -->
        <div class="detail-stage">
          <img v-if="isVisual" :src="stageSrc" class="stage-img" alt="" />
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
          <el-button type="primary" round @click="menuAction('export')">导出</el-button>
          <button class="nav-btn" :disabled="index >= total - 1" @click="emit('next')">
            下一张 <el-icon><ArrowRight /></el-icon>
          </button>
        </footer>

        <!-- 打标签对话框 -->
        <el-dialog v-model="tagDialog" title="管理标签" width="80%">
          <el-checkbox-group v-model="tagDraft" class="tag-checkboxes">
            <el-checkbox v-for="t in tagStore.sorted" :key="t.id" :value="t.id">
              {{ t.name }}
            </el-checkbox>
          </el-checkbox-group>
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
}

.icon-btn:hover {
  background: var(--hover-bg);
}

.detail-title {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
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
}

.stage-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  display: block;
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
  gap: 4px;
  border: none;
  background: none;
  font-size: 13px;
  color: var(--text-main);
  cursor: pointer;
  padding: 6px 8px;
}

.nav-btn:disabled {
  color: var(--text-muted);
  cursor: not-allowed;
}

.tag-checkboxes {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

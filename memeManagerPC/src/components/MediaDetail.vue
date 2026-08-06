<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import type { Media } from "../types";
import { useTagStore } from "../stores/tags";
import { formatDateTime, formatResolution, formatSize } from "../utils/format";
import { SOURCE_LABELS } from "../mock/data";
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
}>();

const tagStore = useTagStore();

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
  props.media ? SOURCE_LABELS[props.media.source] : ""
);

function startEditDesc() {
  descDraft.value = props.media?.description ?? "";
  editingDesc.value = true;
}

function saveDesc() {
  if (props.media) props.media.description = descDraft.value.trim();
  editingDesc.value = false;
  ElMessage.success("描述已保存");
}

function openTagDialog() {
  tagDraft.value = props.media ? [...props.media.tagIds] : [];
  tagDialog.value = true;
}

function saveTags() {
  if (props.media) props.media.tagIds = [...tagDraft.value];
  tagDialog.value = false;
  ElMessage.success("标签已更新");
}

function removeTag(tagId: number) {
  if (!props.media) return;
  props.media.tagIds = props.media.tagIds.filter((id) => id !== tagId);
}

// 右上角菜单操作（桌面端占位）
function menuAction(action: string) {
  if (action === "export") ElMessage.info("导出功能：待接入 Rust 后端");
  else if (action === "reveal") ElMessage.info("打开所在文件夹：待接入");
  else if (action === "delete") ElMessage.warning("删除（进回收站）：待接入");
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
                <el-dropdown-item command="export">导出为 .emp</el-dropdown-item>
                <el-dropdown-item command="reveal">打开所在文件夹</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除（进回收站）</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </header>

        <!-- 大图区（mock 占位） -->
        <div class="detail-stage" :style="{ background: `linear-gradient(135deg, ${media.thumbFrom}, ${media.thumbTo})` }">
          <span class="stage-emoji">{{ media.emoji }}</span>
          <span v-if="media.type === 'gif'" class="stage-badge">GIF</span>
          <span v-else-if="media.type === 'video'" class="stage-badge">▶ 视频</span>
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
            <div><span class="info-label">分辨率</span>{{ formatResolution(media.width, media.height) }}</div>
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
}

.stage-emoji {
  font-size: 120px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.25));
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

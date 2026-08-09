<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { useTagStore } from "../stores/tags";
import { PRESET_TAG_COLORS } from "../utils/color";
import TagChip from "../components/TagChip.vue";
import ColorPickerPop from "../components/ColorPickerPop.vue";

const tagStore = useTagStore();

onMounted(() => {
  tagStore.loadTags();
  // 拖拽中窗口失焦（如 Alt+Tab）时重置状态，避免 drag-lock 残留
  window.addEventListener("blur", onWindowBlur);
});

onUnmounted(() => window.removeEventListener("blur", onWindowBlur));

// —— 新建对话框 ——
const createDialog = ref(false);
const createName = ref("");
const createColor = ref(PRESET_TAG_COLORS[0]);

// —— 重命名对话框 ——
const renameDialog = ref(false);
const renameId = ref(0);
const renameName = ref("");

function openCreate() {
  createName.value = "";
  createColor.value = tagStore.nextColor;
  createDialog.value = true;
}

async function submitCreate() {
  const ok = await tagStore.addTag(createName.value, createColor.value);
  if (!ok) {
    ElMessage.warning("标签名不能为空或已存在");
    return;
  }
  createDialog.value = false;
  ElMessage.success("标签已创建");
}

function openRename(tagId: number) {
  const t = tagStore.tags.find((x) => x.id === tagId);
  if (!t) return;
  renameId.value = tagId;
  renameName.value = t.name;
  renameDialog.value = true;
}

async function submitRename() {
  const name = renameName.value.trim();
  if (!name) return;
  const ok = await tagStore.renameTag(renameId.value, name);
  if (!ok) {
    ElMessage.warning("标签名不能为空、已存在或为保留名");
    return;
  }
  renameDialog.value = false;
  ElMessage.success("已重命名");
}

// —— 拖拽排序（Pointer Events 实现，兼容 WebView2；HTML5 drag 被 Tauri OLE 拖放系统劫持，见 tauri#13171）——
const dragId = ref(0);
const dragStartY = ref(0);
const dragging = ref(false); // 是否已越过启动阈值
const dropTargetId = ref(0); // 当前悬停目标行 id（高亮提示放置位置）

function onPointerDown(e: PointerEvent, id: number) {
  // 从按钮/输入框等交互控件按下时不启动拖拽
  const target = e.target as HTMLElement;
  if (target.closest("button, input, textarea, select, a, .el-input")) return;
  dragId.value = id;
  dragStartY.value = e.clientY;
  dragging.value = false;
  dropTargetId.value = 0;
  // 捕获指针：移动/松开事件持续送达源行，即使移出列表
  (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
}

function onPointerMove(e: PointerEvent) {
  if (dragId.value === 0) return;
  const dy = e.clientY - dragStartY.value;
  if (!dragging.value) {
    if (Math.abs(dy) < 6) return; // 小于阈值为普通点击
    dragging.value = true;
    document.body.classList.add("drag-lock");
  }
  // 命中测试：当前指针落在哪一行
  const el = document.elementFromPoint(e.clientX, e.clientY);
  const row = el?.closest<HTMLElement>(".tag-row");
  dropTargetId.value = row ? Number(row.dataset.id) : 0;
}

function onPointerUp() {
  if (dragId.value === 0) return;
  document.body.classList.remove("drag-lock");  const fromId = dragId.value;
  const targetId = dropTargetId.value;
  dragId.value = 0;
  dropTargetId.value = 0;
  dragging.value = false;
  if (fromId === 0 || fromId === targetId || targetId === 0) return;
  const arr = tagStore.sorted;
  const from = arr.findIndex((t) => t.id === fromId);
  const to = arr.findIndex((t) => t.id === targetId);
  if (from < 0 || to < 0) return;
  // 重新编号 sortOrder = 数组索引（目标位置顺序）并持久化
  const reordered = [...arr];
  const [moved] = reordered.splice(from, 1);
  reordered.splice(to, 0, moved);
  tagStore.setOrder(reordered.map((t) => t.id));
}

/** 窗口失焦时中断拖拽 */
function onWindowBlur() {
  if (dragId.value !== 0) onPointerUp();
}

async function removeTag(id: number) {
  const t = tagStore.tags.find((x) => x.id === id);
  if (!t) return;
  if (t.isReserved) {
    ElMessage.warning("保留标签不可删除");
    return;
  }
  const ok = await tagStore.removeTag(id);
  ElMessage.success(ok ? "标签已删除" : "删除失败");
}
</script>

<template>
  <div class="tag-view">
    <header class="tag-top">
      <span class="tag-title">标签管理器</span>
      <el-button size="small" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        <span>新建</span>
      </el-button>
    </header>

    <div class="tag-body">
      <div
        class="tag-list"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
      >
        <div
          v-for="t in tagStore.sorted"
          :key="t.id"
          class="tag-row"
          :data-id="t.id"
          :class="{ dragging: t.id === dragId, 'drag-over': t.id === dropTargetId }"
          @pointerdown="onPointerDown($event, t.id)"
        >
          <el-icon class="drag-handle"><Rank /></el-icon>
          <TagChip :tag="t" />
          <ColorPickerPop
            :model-value="t.bgColor"
            class="row-color"
            @update:model-value="(c: string) => tagStore.setColor(t.id, c)"
          />
          <div class="row-spacer" />
          <template v-if="t.isReserved">
            <el-tooltip content="保留标签：不可删除、不可重名">
              <el-icon class="lock-icon"><Lock /></el-icon>
            </el-tooltip>
            <el-button size="small" link type="primary" @click="openRename(t.id)">编辑</el-button>
          </template>
          <template v-else>
            <el-button size="small" link type="primary" @click="openRename(t.id)">编辑</el-button>
            <el-button size="small" link type="danger" @click="removeTag(t.id)">删除</el-button>
          </template>
        </div>
      </div>
      <p class="tag-hint">拖拽行可调整排序（决定缩略图圆点与筛选栏顺序）</p>
    </div>

    <!-- 新建标签 -->
    <el-dialog v-model="createDialog" title="新建标签" width="80%" align-center>
      <el-form label-width="60px">
        <el-form-item label="名称">
          <el-input v-model="createName" placeholder="标签名（唯一）" maxlength="12" />
        </el-form-item>
        <el-form-item label="颜色">
          <div class="color-options">
            <button
              v-for="c in PRESET_TAG_COLORS"
              :key="c"
              class="color-swatch"
              :class="{ active: createColor === c }"
              :style="{ background: c }"
              @click="createColor = c"
            />
            <ColorPickerPop v-model="createColor" />
          </div>
        </el-form-item>
        <el-form-item label="">
          <TagChip :tag="{ id: -1, name: createName || '预览', bgColor: createColor, sortOrder: 0 }" class="color-preview" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 重命名 -->
    <el-dialog v-model="renameDialog" title="重命名标签" width="80%" align-center>
      <el-input v-model="renameName" maxlength="12" @keyup.enter="submitRename" />
      <template #footer>
        <el-button @click="renameDialog = false">取消</el-button>
        <el-button type="primary" @click="submitRename">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tag-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--card-bg);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.tag-top {
  height: var(--app-topbar-height);
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--divider);
}

.tag-title {
  font-size: 15px;
  font-weight: 600;
}

.tag-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.tag-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tag-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  background: var(--card-bg);
  border-radius: 10px;
  border: 1px solid var(--divider);
  cursor: grab;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.tag-row:hover {
  border-color: var(--border);
  box-shadow: var(--shadow-sm);
}

.tag-row:active {
  cursor: grabbing;
}

/* 拖拽中的源行：半透明 + 轻微抬起 */
.tag-row.dragging {
  opacity: 0.5;
  position: relative;
  z-index: 10;
  box-shadow: var(--shadow-sm);
  transform: scale(1.02);
}

/* 悬停目标行：高亮边框提示放置位置 */
.tag-row.drag-over {
  border-color: var(--text-main);
  background: var(--input-bg);
}

.drag-handle {
  color: var(--text-muted);
  cursor: grab;
}

.row-color {
  flex: none;
}

.row-spacer {
  flex: 1;
}

.lock-icon {
  color: var(--text-muted);
}

.tag-hint {
  margin-top: 16px;
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
}

.color-options {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px 0;
}

.color-swatch {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
}

.color-swatch.active {
  border-color: var(--text-main);
}

.color-preview {
  margin-top: 10px;
}
</style>

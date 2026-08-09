<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { useTagStore } from "../stores/tags";
import { PRESET_TAG_COLORS } from "../utils/color";
import TagChip from "../components/TagChip.vue";
import ColorPickerPop from "../components/ColorPickerPop.vue";

const tagStore = useTagStore();

onMounted(() => tagStore.loadTags());

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

// —— 拖拽排序（HTML5 drag）——
let dragId = 0;

function onDragStart(id: number) {
  dragId = id;
}

function onDrop(targetId: number) {
  if (dragId === 0 || dragId === targetId) return;
  const arr = tagStore.sorted;
  const from = arr.findIndex((t) => t.id === dragId);
  const to = arr.findIndex((t) => t.id === targetId);
  if (from < 0 || to < 0) return;
  // 重新编号 sortOrder = 数组索引（目标位置顺序）并持久化
  const reordered = [...arr];
  const [moved] = reordered.splice(from, 1);
  reordered.splice(to, 0, moved);
  tagStore.setOrder(reordered.map((t) => t.id));
  dragId = 0;
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
      <div class="tag-list">
        <div
          v-for="t in tagStore.sorted"
          :key="t.id"
          class="tag-row"
          draggable="true"
          @dragstart="onDragStart(t.id)"
          @dragover.prevent
          @drop.prevent="onDrop(t.id)"
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
      <el-form label-width="60px" @submit.prevent>
        <el-form-item label="名称">
          <el-input v-model="createName" placeholder="标签名（唯一）" maxlength="12" />
        </el-form-item>
        <el-form-item label="颜色">
          <div class="color-options">
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

.color-preview {
  margin-top: 10px;
}
</style>

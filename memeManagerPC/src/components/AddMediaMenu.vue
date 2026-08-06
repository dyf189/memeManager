<script setup lang="ts">
import { computed } from "vue";
import { ElMessage } from "element-plus";

const props = defineProps<{ visible: boolean }>();

const emit = defineEmits<{
  (e: "update:visible", v: boolean): void;
}>();

const dialogVisible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit("update:visible", v),
});

const items = [
  { icon: "Picture", label: "选择图片/视频", desc: "从本地文件选择" },
  { icon: "FolderOpened", label: "选择文件", desc: "单个文件导入" },
  { icon: "Folder", label: "导入整个文件夹", desc: "批量索引（可包含子目录）" },
  { icon: "Upload", label: "导入 .emp 分片", desc: "从表情包备份恢复" },
];

function pick(i: number) {
  ElMessage.info(`「${items[i].label}」待接入 Rust 后端`);
  emit("update:visible", false);
}
</script>

<template>
  <teleport to="body">
    <el-dialog v-model="dialogVisible" title="添加媒体" width="80%" align-center>
      <div class="add-menu">
        <button v-for="(it, i) in items" :key="it.label" class="add-item" @click="pick(i)">
          <el-icon :size="22" class="add-icon"><component :is="it.icon" /></el-icon>
          <div class="add-text">
            <div class="add-label">{{ it.label }}</div>
            <div class="add-desc">{{ it.desc }}</div>
          </div>
        </button>
      </div>
    </el-dialog>
  </teleport>
</template>

<style scoped>
.add-menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.add-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: none;
  background: none;
  border-radius: 10px;
  cursor: pointer;
  text-align: left;
}

.add-item:hover {
  background: var(--hover-bg);
}

.add-icon {
  color: var(--accent);
}

.add-text {
  flex: 1;
}

.add-label {
  font-size: 14px;
  color: var(--text-main);
}

.add-desc {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}
</style>

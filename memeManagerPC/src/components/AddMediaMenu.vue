<script setup lang="ts">
import { computed } from "vue";
import { api } from "../api";

const props = defineProps<{ visible: boolean }>();

const emit = defineEmits<{
  (e: "update:visible", v: boolean): void;
  (e: "import-folder"): void;
  (e: "import-mpak"): void;
  (e: "import-files", paths: string[]): void;
}>();

const dialogVisible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit("update:visible", v),
});

const items = [
  { icon: "Picture", label: "选择图片/视频", desc: "从本地文件选择（可多选）" },
  { icon: "FolderOpened", label: "选择文件", desc: "单个文件导入（可多选）" },
  { icon: "Folder", label: "导入整个文件夹", desc: "批量索引（可包含子目录）" },
  { icon: "Upload", label: "导入 .mpak 分片", desc: "从表情包备份恢复" },
];

async function pick(i: number) {
  if (i === 2) {
    // 导入整个文件夹 → 交给相册页执行目录选择 + 扫描
    emit("import-folder");
    emit("update:visible", false);
    return;
  }
  if (i === 3) {
    // 导入 .mpak 分片 → 交给相册页执行文件选择 + 导入
    emit("import-mpak");
    emit("update:visible", false);
    return;
  }
  // 单张/批量文件选择
  const paths = i === 0 ? await api.pickImageFiles() : await api.pickAnyFiles();
  if (paths.length > 0) emit("import-files", paths);
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

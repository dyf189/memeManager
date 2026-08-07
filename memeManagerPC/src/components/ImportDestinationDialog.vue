<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  visible: boolean;
  /** 程序默认媒体目录路径 */
  defaultDir: string;
}>();

const emit = defineEmits<{
  (e: "update:visible", v: boolean): void;
  (e: "choose-default"): void;
  (e: "choose-custom"): void;
}>();

const dialogVisible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit("update:visible", v),
});
</script>

<template>
  <teleport to="body">
    <el-dialog v-model="dialogVisible" title="选择导入位置" width="80%" align-center>
      <p class="hint">媒体将解包写入以下位置：</p>

      <!-- 默认文件夹卡片 -->
      <div class="dest-card" @click="emit('choose-default')">
        <el-icon :size="20" class="dest-icon"><Folder /></el-icon>
        <div class="dest-text">
          <div class="dest-name">程序默认文件夹</div>
          <div class="dest-path text-ellipsis">{{ defaultDir || "加载中…" }}</div>
        </div>
        <el-button size="small" type="primary" @click.stop="emit('choose-default')">
          使用默认
        </el-button>
      </div>

      <!-- 自定义文件夹 -->
      <div class="dest-card" @click="emit('choose-custom')">
        <el-icon :size="20" class="dest-icon"><FolderOpened /></el-icon>
        <div class="dest-text">
          <div class="dest-name">选择其他文件夹</div>
          <div class="dest-path">手动指定目标目录</div>
        </div>
        <el-button size="small" @click.stop="emit('choose-custom')">选择…</el-button>
      </div>
    </el-dialog>
  </teleport>
</template>

<style scoped>
.hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--text-secondary);
}

.dest-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--divider);
  border-radius: 10px;
  cursor: pointer;
  margin-bottom: 10px;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.dest-card:hover {
  border-color: var(--accent);
  box-shadow: var(--shadow-sm);
}

.dest-icon {
  color: var(--accent);
  flex: none;
}

.dest-text {
  flex: 1;
  min-width: 0;
}

.dest-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}

.dest-path {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-muted);
}
</style>

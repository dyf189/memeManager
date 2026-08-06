<script setup lang="ts">
import { ElMessage } from "element-plus";

defineProps<{
  count: number;
}>();

const emit = defineEmits<{
  (e: "exit"): void;
  (e: "tag"): void;
  (e: "export"): void;
  (e: "delete"): void;
}>();

// 桌面端无系统分享，占位提示（后续接复制/打开目录）
function share() {
  ElMessage.info("桌面端分享：待接入（复制到剪贴板 / 打开所在文件夹）");
}
</script>

<template>
  <div class="batch-bar">
    <button class="batch-back" @click="emit('exit')">
      <el-icon><ArrowLeft /></el-icon>
      <span>取消</span>
    </button>
    <span class="batch-count">已选 {{ count }} 项</span>
    <div class="batch-actions">
      <el-tooltip content="批量打标签">
        <button class="batch-btn" @click="emit('tag')">
          <el-icon><CollectionTag /></el-icon>
        </button>
      </el-tooltip>
      <el-tooltip content="导出">
        <button class="batch-btn" @click="emit('export')">
          <el-icon><Download /></el-icon>
        </button>
      </el-tooltip>
      <el-tooltip content="分享">
        <button class="batch-btn" @click="share">
          <el-icon><Share /></el-icon>
        </button>
      </el-tooltip>
      <el-tooltip content="删除">
        <button class="batch-btn danger" @click="emit('delete')">
          <el-icon><Delete /></el-icon>
        </button>
      </el-tooltip>
    </div>
  </div>
</template>

<style scoped>
.batch-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 20;
  height: var(--app-topbar-height);
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 10px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--divider);
}

.batch-back {
  display: flex;
  align-items: center;
  gap: 2px;
  border: none;
  background: none;
  font-size: 14px;
  color: var(--text-main);
  cursor: pointer;
  padding: 4px 6px;
}

.batch-count {
  font-size: 14px;
  font-weight: 600;
}

.batch-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

.batch-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--text-main);
  font-size: 18px;
  cursor: pointer;
}

.batch-btn:hover {
  background: var(--hover-bg);
}

.batch-btn.danger:hover {
  color: #f56c6c;
  background: rgba(245, 108, 108, 0.12);
}
</style>

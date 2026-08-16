<script setup lang="ts">
defineProps<{
  count: number;
  /** 当前可见列表总数（用于显示 N/M 和全选） */
  total: number;
}>();

const emit = defineEmits<{
  (e: "exit"): void;
  (e: "tag"): void;
  (e: "export"): void;
  (e: "delete"): void;
  (e: "select-all"): void;
  (e: "share"): void;
}>();
</script>

<template>
  <div class="batch-bar">
    <button class="batch-back" @click="emit('exit')">
      <el-icon><ArrowLeft /></el-icon>
      <span>取消</span>
    </button>
    <span class="batch-count">已选 <strong>{{ count }}</strong> / {{ total }} 项</span>
    <button class="batch-select-all" @click="emit('select-all')">
      <el-icon><Finished /></el-icon>
      <span>全选</span>
    </button>
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
      <el-tooltip content="分享（复制到剪贴板）">
        <button class="batch-btn" @click="emit('share')">
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
  gap: 8px;
  padding: 0 10px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--divider);
  box-shadow: var(--shadow-sm);
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
  padding: 6px 8px;
  border-radius: 8px;
  transition: background 0.15s;
}

.batch-back:hover {
  background: var(--hover-bg);
}

.batch-count {
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.batch-count strong {
  color: var(--accent);
  font-size: 15px;
}

.batch-select-all {
  display: flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: var(--hover-bg);
  color: var(--text-main);
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  cursor: pointer;
  white-space: nowrap;
}

.batch-select-all:hover {
  background: var(--active-bg);
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

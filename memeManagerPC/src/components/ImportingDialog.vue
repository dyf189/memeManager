<script setup lang="ts">
defineProps<{
  visible: boolean;
  /** 当前阶段文案（导入/扫描中） */
  stage: string;
}>();
</script>

<template>
  <teleport to="body">
    <el-dialog
      :model-value="visible"
      width="80%"
      align-center
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
    >
      <div class="importing-body">
        <el-icon class="is-loading" :size="30"><Loading /></el-icon>
        <span class="importing-text">{{ stage }}</span>
      </div>
    </el-dialog>
  </teleport>
</template>

<style scoped>
.importing-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 10px 0;
}

.importing-text {
  font-size: 14px;
  color: var(--text-secondary);
}
</style>

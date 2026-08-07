<script setup lang="ts">
import { computed } from "vue";

/**
 * 导出进度对话框：受控组件，进度由 Rust 端 export-progress 事件驱动。
 */
const props = defineProps<{
  visible: boolean;
  /** 已处理媒体数 */
  current: number;
  /** 媒体总数 */
  total: number;
}>();

const emit = defineEmits<{
  (e: "update:visible", v: boolean): void;
  (e: "cancel"): void;
}>();

const dialogVisible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit("update:visible", v),
});

const percent = computed(() =>
  props.total > 0 ? Math.round((props.current / props.total) * 100) : 0
);
</script>

<template>
  <teleport to="body">
    <el-dialog
      v-model="dialogVisible"
      title="正在导出..."
      width="80%"
      align-center
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
    >
      <el-progress :percentage="percent" :stroke-width="14" />
      <div class="export-info">
        <div class="export-line">已处理：{{ current }} / {{ total }} 张</div>
      </div>
      <template #footer>
        <el-button @click="emit('cancel')">取消</el-button>
      </template>
    </el-dialog>
  </teleport>
</template>

<style scoped>
.export-info {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}
</style>

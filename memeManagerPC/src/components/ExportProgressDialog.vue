<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";

const props = defineProps<{ visible: boolean }>();

const emit = defineEmits<{
  (e: "update:visible", v: boolean): void;
}>();

const dialogVisible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit("update:visible", v),
});

// mock 导出进度：UI 占位，后续由 Rust 后端事件驱动
const progress = ref(0);
const curShard = ref("分片_001.emp");
const exportedCount = ref(0);

let timer: number | undefined;

watch(
  () => props.visible,
  (v) => {
    if (v) {
      progress.value = 0;
      exportedCount.value = 0;
      timer = window.setInterval(() => {
        progress.value = Math.min(100, progress.value + 4);
        exportedCount.value = Math.floor((progress.value / 100) * 67);
        curShard.value = `分片_${String(Math.min(8, Math.ceil((progress.value / 100) * 8))).padStart(3, "0")}.emp`;
        if (progress.value >= 100 && timer !== undefined) {
          clearInterval(timer);
          timer = undefined;
        }
      }, 120);
    } else if (timer !== undefined) {
      clearInterval(timer);
      timer = undefined;
    }
  }
);

onBeforeUnmount(() => {
  if (timer !== undefined) clearInterval(timer);
});
</script>

<template>
  <teleport to="body">
    <el-dialog v-model="dialogVisible" title="正在导出..." width="80%" align-center :show-close="false">
      <el-progress :percentage="progress" :stroke-width="14" />
      <div class="export-info">
        <div class="export-line">{{ curShard }}</div>
        <div class="export-line">已导出：{{ exportedCount }} 张图片</div>
      </div>
      <template #footer>
        <el-button @click="emit('update:visible', false)">后台运行</el-button>
        <el-button type="danger" @click="emit('update:visible', false)">取消</el-button>
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

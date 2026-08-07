<script setup lang="ts">
import { computed } from "vue";
import type { Media } from "../types";
import { groupByTime } from "../utils/time";
import MediaThumb from "./MediaThumb.vue";

const props = defineProps<{
  items: Media[];
  multiSelect: boolean;
  selectedIds: Set<number>;
  /** 固定列数（来自设置）；缺省时自适应 */
  columns?: number;
}>();

const emit = defineEmits<{
  (e: "open", media: Media): void;
  (e: "select", media: Media): void;
  (e: "multi", media: Media): void; // 右键进入多选
}>();

// 必须用 computed：items 是异步加载的，普通变量不会随 props 更新
const groups = computed(() => groupByTime(props.items));
const gridStyle = computed(() =>
  props.columns
    ? `repeat(${props.columns}, minmax(0, 1fr))`
    : "repeat(auto-fill, minmax(96px, 1fr))"
);

function onItemClick(m: Media) {
  if (props.multiSelect) emit("select", m);
  else emit("open", m);
}

function onContextMenu(m: Media) {
  if (!props.multiSelect) emit("multi", m);
}
</script>

<template>
  <div class="media-grid">
    <template v-for="(g, gi) in groups" :key="gi">
      <!-- 粘性时间分组头 -->
      <div class="grid-header">{{ g.label }}</div>
      <div class="grid-row" :class="{ 'row-separated': gi > 0 }" :style="{ gridTemplateColumns: gridStyle }">
        <div
          v-for="m in g.items"
          :key="m.id"
          class="grid-cell"
          :class="{ selected: selectedIds.has(m.id) }"
          @click="onItemClick(m)"
          @contextmenu.prevent="onContextMenu(m)"
        >
          <MediaThumb :media="m" show-dots show-badge />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.media-grid {
  padding: 4px 8px 12px;
}

.grid-header {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 10px 4px 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--header-bg);
  backdrop-filter: blur(6px);
}

.grid-row {
  display: grid;
  gap: 6px;
  margin-bottom: 6px;
}

.row-separated {
  margin-top: 2px;
}

.grid-cell {
  aspect-ratio: 1;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  position: relative;
  border: 2.5px solid transparent;
  transition: transform 0.12s ease, border-color 0.12s ease;
  /* 长列表渲染优化：视口外的项跳过布局/绘制 */
  content-visibility: auto;
  contain-intrinsic-size: 110px;
}

.grid-cell:hover {
  transform: scale(1.03);
}

.grid-cell.selected {
  border-color: var(--accent);
  background: var(--selected-bg);
}

/* 多选模式下未选中的做压暗处理 */
</style>

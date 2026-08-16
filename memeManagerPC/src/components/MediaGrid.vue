<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { VueDraggable } from "vue-draggable-plus";
import type { Media } from "../types";
import { groupByTime } from "../utils/time";
import { dragMediaOut } from "../utils/drag";
import MediaThumb from "./MediaThumb.vue";

const props = defineProps<{
  items: Media[];
  multiSelect: boolean;
  selectedIds: Set<number>;
  /** 固定列数（来自设置）；缺省时自适应 */
  columns?: number;
  /** 拖拽排序模式：开启时拖拽 = 排序，关闭时拖拽 = 拖出文件 */
  sortMode?: boolean;
}>();

const emit = defineEmits<{
  (e: "open", media: Media): void;
  (e: "select", media: Media): void;
  (e: "multi", media: Media): void; // 右键进入多选
  (e: "reorder", ids: number[]): void; // 排序模式拖拽结束
}>();

// 必须用 computed：items 是异步加载的，普通变量不会随 props 更新
const groups = computed(() => groupByTime(props.items));
const gridStyle = computed(() =>
  props.columns
    ? `repeat(${props.columns}, minmax(0, 1fr))`
    : "repeat(auto-fill, minmax(96px, 1fr))"
);

// —— 排序模式：平铺列表（无时间分组），SortableJS 拖拽重排 ——
const sortItems = ref<Media[]>([]);
watch(
  () => props.items,
  (v) => {
    sortItems.value = [...v];
  },
  { immediate: true }
);

function onSortEnd() {
  if (sortItems.value.length === 0) return;
  emit("reorder", sortItems.value.map((m) => m.id));
}

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
    <!-- 排序模式：平铺网格，拖拽重排（forceFallback 指针模式，不触发原生拖拽） -->
    <VueDraggable
      v-if="sortMode"
      v-model="sortItems"
      class="sort-grid"
      :style="{ gridTemplateColumns: gridStyle }"
      :animation="150"
      :force-fallback="true"
      ghost-class="grid-ghost"
      chosen-class="grid-chosen"
      fallback-class="grid-fallback"
      @end="onSortEnd"
    >
      <div
        v-for="m in sortItems"
        :key="m.id"
        class="grid-cell"
        :class="{ selected: selectedIds.has(m.id) }"
        @click="onItemClick(m)"
        @contextmenu.prevent="onContextMenu(m)"
      >
        <MediaThumb :media="m" show-dots show-badge />
        <span class="select-check"><el-icon :size="12"><Check /></el-icon></span>
      </div>
    </VueDraggable>

    <!-- 默认模式：时间分组视图，拖拽 = 拖出文件 -->
    <template v-else>
      <template v-for="(g, gi) in groups" :key="gi">
        <div class="grid-header">
          <span class="grid-header-label">{{ g.label }}</span>
          <span class="grid-header-count">{{ g.items.length }} 张</span>
        </div>
        <div class="grid-row" :class="{ 'row-separated': gi > 0 }" :style="{ gridTemplateColumns: gridStyle }">
          <div
            v-for="m in g.items"
            :key="m.id"
            class="grid-cell"
            :class="{ selected: selectedIds.has(m.id) }"
            draggable="true"
            @click="onItemClick(m)"
            @contextmenu.prevent="onContextMenu(m)"
            @dragstart="(e: DragEvent) => dragMediaOut(e, m)"
          >
            <MediaThumb :media="m" show-dots show-badge />
            <span class="select-check"><el-icon :size="12"><Check /></el-icon></span>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.media-grid {
  padding: 4px 8px 12px;
}

.sort-grid {
  display: grid;
  gap: 6px;
  padding: 4px 4px 8px;
}

.grid-header {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 6px 8px;
  background: var(--header-bg);
  backdrop-filter: blur(6px);
}

.grid-header-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  line-height: 1.2;
}

.grid-header-count {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  transform: translateY(0.5px);
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
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  position: relative;
  border: 2.5px solid transparent;
  transition: transform 0.14s var(--ease-out), border-color 0.12s ease-out, box-shadow 0.18s ease-out;
  /* 长列表渲染优化：视口外的项跳过布局/绘制 */
  content-visibility: auto;
  contain-intrinsic-size: 110px;
}

/* 悬停位移仅限精确指针设备 */
@media (hover: hover) and (pointer: fine) {
  .grid-cell:hover {
    transform: scale(1.03);
    box-shadow: var(--shadow-md);
    z-index: 1;
  }
}

/* 按压反馈：手指/鼠标按下即缩小（apple-design §1） */
.grid-cell:active {
  transform: scale(0.97);
}

.grid-cell.selected {
  border-color: var(--accent);
  background: var(--selected-bg);
}

/* 选中角标：仅选中时显示，与橙色边框形成双重确认 */
.select-check {
  position: absolute;
  top: 6px;
  left: 6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: var(--accent);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
  opacity: 0;
  transform: scale(0.6);
  transition: opacity 0.12s ease, transform 0.12s ease;
  pointer-events: none;
}

.grid-cell.selected .select-check {
  opacity: 1;
  transform: scale(1);
}

/* SortableJS 拖拽反馈样式 */
.grid-ghost {
  opacity: 0.4;
  border-color: var(--accent) !important;
}

.grid-chosen {
  border-color: var(--accent) !important;
  transform: scale(1.05);
}

.grid-fallback {
  box-shadow: var(--shadow-md);
}
</style>

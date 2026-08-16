<script setup lang="ts">
import { computed } from "vue";
import type { Media } from "../types";
import { groupByTime } from "../utils/time";
import { dragMediaOut } from "../utils/drag";
import { dirName, formatDateTime, formatSize } from "../utils/format";
import MediaThumb from "./MediaThumb.vue";
import TagDots from "./TagDots.vue";

const props = defineProps<{
  items: Media[];
  multiSelect: boolean;
  selectedIds: Set<number>;
}>();

const emit = defineEmits<{
  (e: "open", media: Media): void;
  (e: "select", media: Media): void;
  (e: "multi", media: Media): void;
}>();

// 必须用 computed：items 是异步加载的，普通变量不会随 props 更新
const groups = computed(() => groupByTime(props.items));

function onItemClick(m: Media) {
  if (props.multiSelect) emit("select", m);
  else emit("open", m);
}
</script>

<template>
  <div class="media-list">
    <template v-for="(g, gi) in groups" :key="gi">
      <div class="list-header">
        <span class="list-header-label">{{ g.label }}</span>
        <span class="list-header-count">{{ g.items.length }} 张</span>
      </div>
      <div
        v-for="m in g.items"
        :key="m.id"
        class="list-item"
        :class="{ selected: selectedIds.has(m.id) }"
        draggable="true"
        @click="onItemClick(m)"
        @contextmenu.prevent="emit('multi', m)"
        @dragstart="(e: DragEvent) => dragMediaOut(e, m)"
      >
        <div class="item-thumb">
          <MediaThumb :media="m" show-badge />
        </div>
        <div class="item-info">
          <div class="item-name text-ellipsis">{{ m.fileName }}</div>
          <div class="item-meta text-ellipsis">
            {{ dirName(m.filePath) }} · {{ formatSize(m.fileSize) }} · {{ formatDateTime(m.takenTime) }}
          </div>
        </div>
        <div class="item-tags"><TagDots :media="m" /></div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.media-list {
  padding: 4px 12px 12px;
}

.list-header {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 14px 6px 8px;
  background: var(--header-bg);
  backdrop-filter: blur(6px);
}

.list-header-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}

.list-header-count {
  font-size: 11px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.list-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border-radius: 10px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: background 0.12s;
  /* 长列表渲染优化 */
  content-visibility: auto;
  contain-intrinsic-size: 60px;
}

.list-item:hover {
  background: var(--hover-bg);
}

.list-item.selected {
  border-color: var(--accent);
  background: var(--selected-bg);
}

.item-thumb {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  overflow: hidden;
  flex: none;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 14px;
}

.item-meta {
  margin-top: 3px;
  font-size: 11px;
  color: var(--text-muted);
}

.item-tags {
  flex: none;
  padding-right: 4px;
}
</style>

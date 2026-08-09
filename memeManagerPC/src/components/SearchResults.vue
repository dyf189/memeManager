<script setup lang="ts">
import type { Media } from "../types";
import { dragMediaOut } from "../utils/drag";
import { formatDateTime, formatSize } from "../utils/format";
import { splitByMatches } from "../utils/highlight";
import MediaThumb from "./MediaThumb.vue";

const props = defineProps<{
  items: Media[];
  query: string;
}>();

const emit = defineEmits<{
  (e: "open", media: Media): void;
}>();

/** 渲染片段：匹配部分加 <mark class="hl"> */
function renderParts(text: string) {
  const parts = splitByMatches(text, props.query);
  return parts.map((p, i) =>
    p.matched
      ? `<mark class="hl">${escapeHtml(p.text)}</mark>`
      : escapeHtml(p.text)
  ).join("");
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}
</script>

<template>
  <div class="search-results">
    <div
      v-for="m in items"
      :key="m.id"
      class="result-item"
      draggable="true"
      @click="emit('open', m)"
      @dragstart="(e: DragEvent) => dragMediaOut(e, m)"
    >
      <div class="result-thumb">
        <MediaThumb :media="m" show-dots show-badge />
      </div>
      <div class="result-text">
        <!-- 标题行：单行，匹配加粗高亮 -->
        <div class="result-title text-ellipsis" v-html="renderParts(m.fileName)" />
        <!-- 描述行：最多两行，灰色，匹配加粗高亮 -->
        <div v-if="m.description" class="result-desc clamp-2" v-html="renderParts(m.description)" />
        <!-- 信息行：大小 + 日期，浅灰，不参与高亮 -->
        <div class="result-meta">{{ formatSize(m.fileSize) }} · {{ formatDateTime(m.takenTime) }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.search-results {
  padding: 4px 12px 12px;
}

.result-item {
  display: flex;
  gap: 10px;
  padding: 8px 4px;
  border-radius: 10px;
  cursor: pointer;
}

.result-item:hover {
  background: var(--hover-bg);
}

.result-thumb {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  overflow: hidden;
  flex: none;
}

.result-text {
  flex: 1;
  min-width: 0;
}

.result-title {
  font-size: 14px;
  line-height: 1.4;
}

.result-desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 2px;
  line-height: 1.4;
}

.result-meta {
  margin-top: 3px;
  font-size: 11px;
  color: var(--text-muted);
}
</style>

<script setup lang="ts">
import { computed } from "vue";
import type { Media } from "../types";
import { useTagStore } from "../stores/tags";

const props = defineProps<{ media: Media }>();

const tagStore = useTagStore();

/** 按标签排序取前 4 个圆点，超出显示灰色 +N（goals.md 缩略图圆点图例） */
const dots = computed(() => {
  const sorted = [...tagStore.tags]
    .filter((t) => props.media.tagIds.includes(t.id))
    .sort((a, b) => a.sortOrder - b.sortOrder);
  const shown = sorted.slice(0, 4);
  const rest = sorted.length - shown.length;
  return { shown, rest };
});
</script>

<template>
  <span class="tag-dots">
    <span
      v-for="t in dots.shown"
      :key="t.id"
      class="tag-dot"
      :style="{ background: t.bgColor }"
    />
    <span v-if="dots.rest > 0" class="tag-dot-more">+{{ dots.rest }}</span>
  </span>
</template>

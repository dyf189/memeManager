<script setup lang="ts">
import { computed } from "vue";
import type { Media } from "../types";
import TagDots from "./TagDots.vue";

const props = defineProps<{
  media: Media;
  /** 是否显示标签圆点 */
  showDots?: boolean;
  /** 是否显示类型角标（GIF / 视频） */
  showBadge?: boolean;
}>();

const gradient = computed(
  () => `linear-gradient(135deg, ${props.media.thumbFrom}, ${props.media.thumbTo})`
);

const badge = computed(() => {
  if (props.media.type === "gif") return "GIF";
  if (props.media.type === "video") return "▶";
  return "";
});
</script>

<template>
  <div class="media-thumb" :style="{ background: gradient }">
    <span class="thumb-emoji">{{ media.emoji }}</span>
    <span v-if="showBadge && badge" class="type-badge">{{ badge }}</span>
    <!-- 仅当媒体确实带有标签时才显示圆点块，避免空标签时出现空白透明框 -->
    <div v-if="showDots && media.tagIds.length > 0" class="thumb-dots">
      <TagDots :media="media" />
    </div>
  </div>
</template>

<style scoped>
.media-thumb {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.thumb-emoji {
  font-size: 34px;
  user-select: none;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.25));
}

.type-badge {
  position: absolute;
  top: 5px;
  right: 5px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 9px;
  font-weight: 600;
  padding: 1px 5px;
  border-radius: 4px;
  letter-spacing: 0.5px;
}

.thumb-dots {
  position: absolute;
  right: 5px;
  bottom: 5px;
  padding: 0 6px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.75);
}
</style>

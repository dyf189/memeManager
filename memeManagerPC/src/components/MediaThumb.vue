<script setup lang="ts">
import { computed, ref } from "vue";
import { convertFileSrc } from "@tauri-apps/api/core";
import type { Media } from "../types";
import TagDots from "./TagDots.vue";
import GifThumb from "./GifThumb.vue";

const props = defineProps<{
  media: Media;
  /** 是否显示标签圆点 */
  showDots?: boolean;
  /** 是否显示类型角标（GIF / 视频） */
  showBadge?: boolean;
}>();

/** 真实文件 → asset 协议 URL（Tauri 本地图片显示） */
const src = computed(() => convertFileSrc(props.media.filePath));

/** 图片加载失败时显示占位（不再显示破图问号） */
const imgFailed = ref(false);

function onImgError() {
  imgFailed.value = true;
  console.warn("[MediaThumb] 图片加载失败:", props.media.filePath);
}

const badge = computed(() => {
  if (props.media.mediaType === "gif") return "GIF";
  if (props.media.mediaType === "video") return "▶";
  return "";
});
</script>

<template>
  <div class="media-thumb">
    <!-- 静态图直接渲染 -->
    <img
      v-if="media.mediaType === 'image' && src && !imgFailed"
      :src="src"
      class="thumb-img"
      alt=""
      loading="lazy"
      decoding="async"
      @error="onImgError"
    />
    <!-- GIF 缩略图只显示第一帧（canvas），避免网格内动画播放 -->
    <GifThumb v-else-if="media.mediaType === 'gif'" :src="src" />
    <!-- 视频 / 加载失败占位 -->
    <template v-else>
      <span class="thumb-emoji">{{ media.mediaType === "video" ? "🎬" : "🖼️" }}</span>
    </template>

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
  background: var(--input-bg);
}

.thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumb-emoji {
  font-size: 30px;
  opacity: 0.6;
  user-select: none;
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
  padding: 1px 4px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.75);
}
</style>

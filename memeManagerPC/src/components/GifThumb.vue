<script setup lang="ts">
import { onMounted, ref } from "vue";

/**
 * GIF 静态缩略图：用 canvas 只绘制第一帧，
 * 避免网格里所有 GIF 同时播放动画导致卡顿（点开详情页才播放）。
 */
const props = defineProps<{ src: string }>();

const canvasRef = ref<HTMLCanvasElement | null>(null);

onMounted(() => {
  const img = new Image();
  img.decoding = "async";
  img.onload = () => {
    const cv = canvasRef.value;
    if (!cv) return;
    const w = img.naturalWidth || 1;
    const h = img.naturalHeight || 1;
    cv.width = w;
    cv.height = h;
    const ctx = cv.getContext("2d");
    ctx?.drawImage(img, 0, 0, w, h);
  };
  // 解码失败时留空 canvas（显示占位背景）
  img.src = props.src;
});
</script>

<template>
  <canvas ref="canvasRef" class="gif-canvas" />
</template>

<style scoped>
.gif-canvas {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
</style>

<script setup lang="ts">
import { onBeforeUnmount, ref } from "vue";
import { Sketch } from "@ckpack/vue-color";
import { PRESET_TAG_COLORS } from "../utils/color";

const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{ (e: "update:modelValue", v: string): void }>();

const open = ref(false);
const pos = ref({ top: 0, left: 0 });
const triggerEl = ref<HTMLButtonElement | null>(null);
const panelEl = ref<HTMLDivElement | null>(null);

const PANEL_W = 228; // Sketch 面板宽度（含边框）
const PANEL_H = 320; // 估算面板高度，用于防止超出视口底部

function toggle(e: MouseEvent) {
  e.stopPropagation();
  if (open.value) {
    open.value = false;
    return;
  }
  const r = triggerEl.value!.getBoundingClientRect();
  pos.value = {
    top: Math.min(r.bottom + 6, window.innerHeight - PANEL_H - 8),
    left: Math.max(4, Math.min(r.left, window.innerWidth - PANEL_W - 8)),
  };
  open.value = true;
}

function onSketchUpdate(p: { hex: string }) {
  emit("update:modelValue", p.hex);
}

/** 点击面板外任意处关闭 */
function onDocClick(e: MouseEvent) {
  const t = e.target as Node;
  if (panelEl.value?.contains(t) || triggerEl.value?.contains(t)) return;
  open.value = false;
}

/** 滚动时关闭，避免 fixed 面板与触发器错位 */
function onScroll() {
  open.value = false;
}

window.addEventListener("click", onDocClick);
window.addEventListener("scroll", onScroll, true);
window.addEventListener("resize", onScroll);
onBeforeUnmount(() => {
  window.removeEventListener("click", onDocClick);
  window.removeEventListener("scroll", onScroll, true);
  window.removeEventListener("resize", onScroll);
});
</script>

<template>
  <button
    ref="triggerEl"
    type="button"
    class="color-trigger"
    :title="modelValue"
    @click="toggle"
  >
    <span class="trigger-dot" :style="{ background: modelValue }" />
    <span class="trigger-text">自定义</span>
  </button>
  <Teleport to="body">
    <div
      v-if="open"
      ref="panelEl"
      class="color-panel"
      :style="{ top: pos.top + 'px', left: pos.left + 'px' }"
    >
      <Sketch
        :model-value="modelValue"
        :preset-colors="PRESET_TAG_COLORS"
        :disable-alpha="true"
        @update:model-value="onSketchUpdate"
      />
    </div>
  </Teleport>
</template>

<style scoped>
.color-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 9px 3px 5px;
  border-radius: 6px;
  border: 1px solid var(--divider);
  background: var(--input-bg);
  color: var(--text-main);
  font-size: 12px;
  cursor: pointer;
  flex: none;
  transition: transform 0.12s ease, border-color 0.12s ease;
}

.color-trigger:hover {
  transform: scale(1.05);
  border-color: var(--border);
}

.trigger-dot {
  width: 16px;
  height: 16px;
  border-radius: 5px;
  border: 2px solid rgba(255, 255, 255, 0.25);
  flex: none;
}

.color-panel {
  position: fixed;
  z-index: 3000;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
}
</style>

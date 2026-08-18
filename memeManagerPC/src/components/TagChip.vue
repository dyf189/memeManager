<script setup lang="ts">
import { computed } from "vue";
import type { Tag } from "../types";
import { textColorFor } from "../utils/color";

const props = withDefaults(
  defineProps<{
    tag: Tag;
    active?: boolean;
    closable?: boolean;
    /** 中性胶囊（如「全部」）：使用主题色而非标签色 */
    neutral?: boolean;
    /** 幽灵态（标签选择器）：未选中时仅显示色点，选中时填满标签色 */
    ghost?: boolean;
  }>(),
  { active: false, closable: false, neutral: false, ghost: false }
);

const emit = defineEmits<{
  (e: "click"): void;
  (e: "close"): void;
}>();

const fg = computed(() => textColorFor(props.tag.bgColor));
const chipStyle = computed(() =>
  props.neutral || (props.ghost && !props.active)
    ? undefined
    : { background: props.tag.bgColor, color: fg.value }
);
</script>

<template>
  <span
    class="tag-chip"
    :class="{ active, neutral, ghost: ghost && !active }"
    :style="chipStyle"
    @click.stop="emit('click')"
  >
    <el-icon v-if="active" :size="11" class="tag-chip-check"><Check /></el-icon>
    <span v-else-if="ghost" class="tag-chip-dot" :style="{ background: tag.bgColor }"></span>
    {{ tag.name }}
    <el-icon v-if="closable" :size="11" class="tag-chip-close" @click.stop="emit('close')">
      <Close />
    </el-icon>
  </span>
</template>

<style scoped>
.tag-chip-check {
  flex: none;
}

.tag-chip-close {
  cursor: pointer;
  opacity: 0.7;
}

.tag-chip-close:hover {
  opacity: 1;
}

/* 中性胶囊（「全部」等）：随主题切换 */
.tag-chip.neutral {
  background: var(--input-bg);
  color: var(--text-main);
}

/* 幽灵态（标签选择器）：未选中 = 中性底色 + 色点 */
.tag-chip.ghost {
  background: var(--input-bg);
  color: var(--text-secondary);
  border-color: var(--divider);
}

.tag-chip.ghost:hover {
  color: var(--text-main);
  border-color: var(--text-muted);
  /* 选择器内不位移，仅靠边框/颜色反馈，避免网格晃动 */
  transform: none;
  box-shadow: none;
}

.tag-chip-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
  box-shadow: 0 0 0 1px rgba(120, 120, 130, 0.25);
}
</style>

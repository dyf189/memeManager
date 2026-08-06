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
  }>(),
  { active: false, closable: false, neutral: false }
);

const emit = defineEmits<{
  (e: "click"): void;
  (e: "close"): void;
}>();

const fg = computed(() => textColorFor(props.tag.bgColor));
const chipStyle = computed(() =>
  props.neutral
    ? undefined
    : { background: props.tag.bgColor, color: fg.value }
);
</script>

<template>
  <span
    class="tag-chip"
    :class="{ active, neutral }"
    :style="chipStyle"
    @click.stop="emit('click')"
  >
    {{ tag.name }}
    <el-icon v-if="closable" :size="11" class="tag-chip-close" @click.stop="emit('close')">
      <Close />
    </el-icon>
  </span>
</template>

<style scoped>
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
</style>

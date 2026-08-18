<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import type { FilterState, MediaType, SourceType } from "../types";
import { SOURCE_LABELS } from "../utils/constants";
import { useTagStore } from "../stores/tags";
import { useAlbumStore } from "../stores/album";

const props = defineProps<{
  modelValue: FilterState;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: FilterState): void;
}>();

const tagStore = useTagStore();
const albumStore = useAlbumStore();

// 本地编辑副本：任何更改立即生效（实时筛选，无「应用筛选」）
const draft = reactive<FilterState>({ ...props.modelValue });
const startDate = ref<Date | null>(null);
const endDate = ref<Date | null>(null);
const sizeMin = ref<number | null>(null);
const sizeMax = ref<number | null>(null);

/** 把本地状态合成 FilterState 并立即广播 */
function pushUpdate() {
  const next: FilterState = { ...draft };
  // 时间/大小需两端都填写才生效，否则视为不筛选
  next.timeRange =
    startDate.value && endDate.value
      ? [startDate.value.getTime(), endDate.value.getTime() + 86_399_999]
      : null;
  next.sizeRange =
    sizeMin.value != null && sizeMax.value != null
      ? [sizeMin.value * 1024 * 1024, sizeMax.value * 1024 * 1024]
      : null;
  emit("update:modelValue", next);
}

// 面板内任意条件变化 → 立即筛选
watch(draft, pushUpdate, { deep: true });
watch([startDate, endDate, sizeMin, sizeMax], pushUpdate);

function clear() {
  draft.type = "all";
  draft.source = "all";
  draft.dir = "all";
  draft.hasDescription = "all";
  draft.tagIds = [];
  draft.timeRange = null;
  draft.sizeRange = null;
  startDate.value = null;
  endDate.value = null;
  sizeMin.value = null;
  sizeMax.value = null;
  // pushUpdate 由上面的 watch 自动触发
}

const typeOptions: { label: string; value: MediaType | "all" }[] = [
  { label: "全部", value: "all" },
  { label: "图片", value: "image" },
  { label: "GIF", value: "gif" },
  { label: "视频", value: "video" },
];

const sourceOptions: { label: string; value: SourceType | "all" }[] = [
  { label: "全部", value: "all" },
  ...(Object.keys(SOURCE_LABELS) as SourceType[]).map((s) => ({
    label: SOURCE_LABELS[s],
    value: s,
  })),
];
</script>

<template>
  <div class="filter-panel">
    <div class="filter-row">
      <span class="filter-label">类型</span>
      <el-radio-group v-model="draft.type" size="small">
        <el-radio-button v-for="o in typeOptions" :key="o.value" :value="o.value">
          {{ o.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <div class="filter-row">
      <span class="filter-label">来源</span>
      <el-select v-model="draft.source" size="small" style="width: 120px">
        <el-option v-for="o in sourceOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
    </div>

    <div class="filter-row">
      <span class="filter-label">目录</span>
      <el-select v-model="draft.dir" size="small" style="max-width: 200px">
        <el-option label="全部目录" value="all" />
        <el-option v-for="d in albumStore.dirList" :key="d" :label="d" :value="d" />
      </el-select>
    </div>

    <div class="filter-row">
      <span class="filter-label">描述</span>
      <el-radio-group v-model="draft.hasDescription" size="small">
        <el-radio-button value="all">不限</el-radio-button>
        <el-radio-button value="yes">有</el-radio-button>
        <el-radio-button value="no">无</el-radio-button>
      </el-radio-group>
    </div>

    <div class="filter-row">
      <span class="filter-label">标签</span>
      <el-select
        v-model="draft.tagIds"
        multiple
        size="small"
        placeholder="选择标签（多选，同时满足）"
        collapse-tags
        style="flex: 1; min-width: 0"
      >
        <el-option v-for="t in tagStore.sorted" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
    </div>

    <div class="filter-row">
      <span class="filter-label">时间</span>
      <el-date-picker
        v-model="startDate"
        type="date"
        size="small"
        placeholder="起始日期"
        style="width: 130px"
      />
      <span class="filter-sep">—</span>
      <el-date-picker
        v-model="endDate"
        type="date"
        size="small"
        placeholder="截止日期"
        style="width: 130px"
      />
    </div>

    <div class="filter-row">
      <span class="filter-label">大小(MB)</span>
      <el-input-number v-model="sizeMin" :min="0" :controls="false" size="small" style="width: 90px" />
      <span class="filter-sep">—</span>
      <el-input-number v-model="sizeMax" :min="0" :controls="false" size="small" style="width: 90px" />
    </div>

    <div class="filter-actions">
      <el-button size="small" @click="clear">清除全部</el-button>
    </div>
  </div>
</template>

<style scoped>
.filter-panel {
  padding: 10px 16px 14px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--divider);
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 0;
}

.filter-label {
  width: 64px;
  flex: none;
  font-size: 12px;
  color: var(--text-secondary);
}

.filter-sep {
  color: var(--text-muted);
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 10px;
}
</style>

import { computed, ref } from "vue";
import { defineStore } from "pinia";
import type { Tag } from "../types";
import { api } from "../api";
import { PRESET_TAG_COLORS } from "../utils/color";

/** 标签管理器状态 */
export const useTagStore = defineStore("tags", () => {
  const tags = ref<Tag[]>([]);
  const loaded = ref(false);

  const sorted = computed(() => [...tags.value].sort((a, b) => a.sortOrder - b.sortOrder));

  /** 新建标签时按预设色循环分配（goals.md：删除后不影响循环） */
  const nextColor = computed(() => {
    if (tags.value.length === 0) return PRESET_TAG_COLORS[0];
    const last = tags.value[tags.value.length - 1];
    const idx = PRESET_TAG_COLORS.indexOf(last.bgColor);
    return PRESET_TAG_COLORS[(idx + 1) % PRESET_TAG_COLORS.length];
  });

  async function loadTags() {
    tags.value = await api.listTags();
    loaded.value = true;
  }

  async function addTag(name: string, bgColor: string): Promise<boolean> {
    const trimmed = name.trim();
    if (!trimmed) return false;
    if (tags.value.some((t) => t.name === trimmed)) return false; // 标签名唯一
    const t = await api.addTag({ name: trimmed, bgColor });
    tags.value.push(t);
    return true;
  }

  function updateTag(id: number, patch: Partial<Tag>) {
    const t = tags.value.find((x) => x.id === id);
    if (!t) return;
    Object.assign(t, patch);
  }

  function removeTag(id: number) {
    const t = tags.value.find((x) => x.id === id);
    if (t?.isReserved) return; // 保留标签不可删除
    tags.value = tags.value.filter((x) => x.id !== id);
  }

  function moveTag(id: number, dir: -1 | 1) {
    const arr = sorted.value;
    const idx = arr.findIndex((t) => t.id === id);
    const target = idx + dir;
    if (idx < 0 || target < 0 || target >= arr.length) return;
    const cur = arr[idx];
    const other = arr[target];
    const tmp = cur.sortOrder;
    cur.sortOrder = other.sortOrder;
    other.sortOrder = tmp;
  }

  return {
    tags,
    loaded,
    sorted,
    nextColor,
    loadTags,
    addTag,
    updateTag,
    removeTag,
    moveTag,
  };
});

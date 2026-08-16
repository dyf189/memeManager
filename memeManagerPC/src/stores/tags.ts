import { computed, ref } from "vue";
import { defineStore } from "pinia";
import type { Tag } from "../types";
import { api } from "../api";
import { PRESET_TAG_COLORS } from "../utils/color";
import { useSettingsStore } from "./settings";

/** 标签管理器状态（读写均持久化到 SQLite） */
export const useTagStore = defineStore("tags", () => {
  const tags = ref<Tag[]>([]);
  const loaded = ref(false);

  const settingsStore = useSettingsStore();

  const sorted = computed(() => [...tags.value].sort((a, b) => a.sortOrder - b.sortOrder));

  /** 预设色（来自设置，用户清空时回退内置色） */
  const presetColors = computed(() => {
    const c = settingsStore.settings.presetColors;
    return c.length > 0 ? c : PRESET_TAG_COLORS;
  });

  /** 新建标签时按预设色循环分配（goals.md：删除后不影响循环） */
  const nextColor = computed(() => {
    const colors = presetColors.value;
    if (tags.value.length === 0) return colors[0];
    const last = tags.value[tags.value.length - 1];
    const idx = colors.indexOf(last.bgColor);
    return colors[(idx + 1) % colors.length];
  });

  async function loadTags() {
    tags.value = await api.listTags();
    loaded.value = true;
  }

  async function addTag(name: string, bgColor: string): Promise<boolean> {
    const trimmed = name.trim();
    if (!trimmed) return false;
    try {
      const t = await api.addTag(trimmed, bgColor);
      tags.value.push(t);
      return true;
    } catch {
      return false;
    }
  }

  /** 重命名（后端校验唯一性与保留标签） */
  async function renameTag(id: number, name: string): Promise<boolean> {
    const trimmed = name.trim();
    if (!trimmed) return false;
    try {
      await api.renameTag(id, trimmed);
      const t = tags.value.find((x) => x.id === id);
      if (t) t.name = trimmed;
      return true;
    } catch {
      return false;
    }
  }

  /** 改色（实时持久化） */
  async function setColor(id: number, color: string) {
    await api.setTagColor(id, color);
    const t = tags.value.find((x) => x.id === id);
    if (t) t.bgColor = color;
  }

  async function removeTag(id: number) {
    const t = tags.value.find((x) => x.id === id);
    if (t?.isReserved) return false; // 保留标签不可删除
    try {
      await api.deleteTag(id);
      tags.value = tags.value.filter((x) => x.id !== id);
      return true;
    } catch {
      return false;
    }
  }

  /** 拖拽后按新顺序持久化 */
  async function setOrder(ids: number[]) {
    try {
      await api.setTagOrder(ids);
      ids.forEach((id, i) => {
        const t = tags.value.find((x) => x.id === id);
        if (t) t.sortOrder = i + 1;
      });
    } catch {
      /* 静默失败，下次刷新恢复 */
    }
  }

  return {
    tags,
    loaded,
    sorted,
    presetColors,
    nextColor,
    loadTags,
    addTag,
    renameTag,
    setColor,
    removeTag,
    setOrder,
  };
});

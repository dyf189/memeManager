import { computed, ref } from "vue";
import { defineStore } from "pinia";
import type { FilterState, Media, ViewMode } from "../types";
import { emptyFilter } from "../types";
import { api } from "../api";
import { useTagStore } from "./tags";
import { useSettingsStore } from "./settings";
import { groupByTime } from "../utils/time";
import { dirName } from "../utils/format";

/**
 * 相册状态：媒体列表、筛选、搜索、视图模式、多选。
 * 对应 goals.md 的 AlbumUiState。
 */
export const useAlbumStore = defineStore("album", () => {
  const mediaList = ref<Media[]>([]);
  const loaded = ref(false);
  const loading = ref(false);

  const viewMode = ref<ViewMode>("grid");
  const filter = ref<FilterState>(emptyFilter());
  /** 顶部标签栏多选（空数组 = 全部）；筛选逻辑为「或」：命中任一标签即显示 */
  const tagFilterIds = ref<number[]>([]);
  const searchQuery = ref("");
  const isSearchActive = computed(() => searchQuery.value.trim().length > 0);

  // —— 多选状态 ——
  const multiSelect = ref(false);
  const selectedIds = ref<Set<number>>(new Set());

  const tagStore = useTagStore();
  const settingsStore = useSettingsStore();

  async function loadMedia() {
    loading.value = true;
    try {
      mediaList.value = await api.listMedia();
    } finally {
      loading.value = false;
      loaded.value = true;
    }
  }

  function setFilter(f: FilterState) {
    filter.value = f;
  }

  function clearFilter() {
    filter.value = emptyFilter();
  }

  // —— 筛选 + 标签胶囊 + 搜索 组合过滤 ——
  const filteredMedia = computed<Media[]>(() => {
    let list = mediaList.value.filter((m) => !m.isDeleted);
    const f = filter.value;

    // 顶部标签胶囊：多选，「或」逻辑（命中任一标签即通过）
    if (tagFilterIds.value.length > 0) {
      list = list.filter((m) => tagFilterIds.value.some((id) => m.tagIds.includes(id)));
    }
    if (f.type !== "all") list = list.filter((m) => m.mediaType === f.type);
    if (f.source !== "all") list = list.filter((m) => m.source === f.source);
    if (f.dir !== "all") list = list.filter((m) => dirName(m.filePath) === f.dir);
    if (f.hasDescription === "yes") list = list.filter((m) => m.description.trim().length > 0);
    if (f.hasDescription === "no") list = list.filter((m) => m.description.trim().length === 0);
    if (f.tagIds.length > 0) {
      list = list.filter((m) => f.tagIds.every((t) => m.tagIds.includes(t)));
    }
    if (f.timeRange) {
      list = list.filter((m) => m.takenTime >= f.timeRange![0] && m.takenTime <= f.timeRange![1]);
    }
    if (f.sizeRange) {
      list = list.filter((m) => m.fileSize >= f.sizeRange![0] && m.fileSize <= f.sizeRange![1]);
    }

    if (isSearchActive.value) {
      const q = searchQuery.value.trim().toLowerCase();
      list = list.filter((m) => {
        // 搜索范围：文件名、描述、标签名（goals.md）
        const tagNames = tagStore.tags
          .filter((t) => m.tagIds.includes(t.id))
          .map((t) => t.name);
        return (
          m.fileName.toLowerCase().includes(q) ||
          m.description.toLowerCase().includes(q) ||
          tagNames.some((n) => n.toLowerCase().includes(q))
        );
      });
    }

    return list;
  });

  /** 时间分组（依据设置：拍摄日期 / 导入日期） */
  const grouped = computed(() => {
    const field =
      settingsStore.settings.groupBy === "import" ? "importTime" : "takenTime";
    return groupByTime(filteredMedia.value, Date.now(), field);
  });

  /** 库中所有来源目录（供筛选器选择） */
  const dirList = computed(() => {
    const set = new Set<string>();
    for (const m of mediaList.value) {
      const d = dirName(m.filePath);
      if (d) set.add(d);
    }
    return [...set].sort();
  });

  // —— 多选操作 ——
  function enterMultiSelect(id?: number) {
    multiSelect.value = true;
    if (id !== undefined) selectedIds.value.add(id);
  }

  function toggleSelect(id: number) {
    const s = new Set(selectedIds.value);
    if (s.has(id)) s.delete(id);
    else s.add(id);
    selectedIds.value = s;
    if (s.size === 0) multiSelect.value = false;
  }

  function exitMultiSelect() {
    multiSelect.value = false;
    selectedIds.value = new Set();
  }

  /** 全选/取消全选（传入当前可见列表的 id 集合） */
  function selectAll(ids: number[]) {
    const next = new Set(ids);
    // 若当前已全选，则视为取消全选
    if (next.size > 0 && selectedIds.value.size === next.size && [...selectedIds.value].every((id) => next.has(id))) {
      selectedIds.value = new Set();
      multiSelect.value = false;
      return;
    }
    selectedIds.value = next;
    if (next.size > 0) multiSelect.value = true;
  }

  return {
    mediaList,
    loaded,
    loading,
    viewMode,
    filter,
    tagFilterIds,
    searchQuery,
    isSearchActive,
    multiSelect,
    selectedIds,
    loadMedia,
    setFilter,
    clearFilter,
    filteredMedia,
    grouped,
    dirList,
    enterMultiSelect,
    toggleSelect,
    exitMultiSelect,
    selectAll,
  };
});

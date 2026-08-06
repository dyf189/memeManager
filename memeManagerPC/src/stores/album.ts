import { computed, ref } from "vue";
import { defineStore } from "pinia";
import type { FilterState, Media, ViewMode } from "../types";
import { emptyFilter } from "../types";
import { api } from "../api";
import { groupByTime } from "../utils/time";

/**
 * 相册状态：媒体列表、筛选、搜索、视图模式、多选。
 * 对应 goals.md 的 AlbumUiState。
 */
export const useAlbumStore = defineStore("album", () => {
  const mediaList = ref<Media[]>([]);
  const loaded = ref(false);

  const viewMode = ref<ViewMode>("grid");
  const filter = ref<FilterState>(emptyFilter());
  const tagFilterId = ref<number | "all">("all");
  const searchQuery = ref("");
  const isSearchActive = computed(() => searchQuery.value.trim().length > 0);

  // —— 多选状态 ——
  const multiSelect = ref(false);
  const selectedIds = ref<Set<number>>(new Set());

  async function loadMedia() {
    mediaList.value = await api.listMedia();
    loaded.value = true;
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

    if (tagFilterId.value !== "all") {
      list = list.filter((m) => m.tagIds.includes(tagFilterId.value));
    }
    if (f.type !== "all") list = list.filter((m) => m.type === f.type);
    if (f.source !== "all") list = list.filter((m) => m.source === f.source);
    if (f.hasDescription === "yes") list = list.filter((m) => m.description.trim().length > 0);
    if (f.hasDescription === "no") list = list.filter((m) => m.description.trim().length === 0);
    if (f.exported === "exported") list = list.filter((m) => m.tagIds.includes(6)); // [已导出] tag id
    if (f.exported === "not") list = list.filter((m) => !m.tagIds.includes(6));
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
        return (
          m.fileName.toLowerCase().includes(q) ||
          m.description.toLowerCase().includes(q)
        );
      });
    }

    return list;
  });

  /** 时间降序分组（分组头 = 导出顺序） */
  const grouped = computed(() => groupByTime(filteredMedia.value));

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

  function selectAll(ids: number[]) {
    selectedIds.value = new Set(ids);
  }

  return {
    mediaList,
    loaded,
    viewMode,
    filter,
    tagFilterId,
    searchQuery,
    isSearchActive,
    multiSelect,
    selectedIds,
    loadMedia,
    setFilter,
    clearFilter,
    filteredMedia,
    grouped,
    enterMultiSelect,
    toggleSelect,
    exitMultiSelect,
    selectAll,
  };
});

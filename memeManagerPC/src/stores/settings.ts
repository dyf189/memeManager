import { reactive, ref, watch } from "vue";
import { defineStore } from "pinia";
import { api } from "../api";
import { PRESET_TAG_COLORS } from "../utils/color";

export interface AppSettings {
  defaultStorage: string; // user | app | custom
  customDir: string;
  shardSize: number; // MB
  groupBy: string; // taken | import
  gridCols: number;
  recycleDays: number;
  jsonSync: boolean;
  presetColors: string[];
}

const defaults: AppSettings = {
  defaultStorage: "user",
  customDir: "",
  shardSize: 100,
  groupBy: "taken",
  gridCols: 6,
  recycleDays: 30,
  jsonSync: false,
  presetColors: [...PRESET_TAG_COLORS],
};

/** 应用设置：加载自 SQLite settings 表，任何变更自动持久化 */
export const useSettingsStore = defineStore("settings", () => {
  const settings = reactive<AppSettings>({
    ...defaults,
    presetColors: [...defaults.presetColors],
  });
  const loaded = ref(false);

  async function load() {
    const saved = await api.getSettings();
    if (saved.defaultStorage) settings.defaultStorage = saved.defaultStorage;
    if (saved.customDir) settings.customDir = saved.customDir;
    if (saved.shardSize) settings.shardSize = Number(saved.shardSize) || defaults.shardSize;
    if (saved.groupBy) settings.groupBy = saved.groupBy;
    if (saved.gridCols) settings.gridCols = Number(saved.gridCols) || defaults.gridCols;
    if (saved.recycleDays) settings.recycleDays = Number(saved.recycleDays) || defaults.recycleDays;
    if (saved.jsonSync) settings.jsonSync = saved.jsonSync === "true";
    if (saved.presetColors) {
      try {
        const arr = JSON.parse(saved.presetColors);
        if (Array.isArray(arr)) settings.presetColors = arr;
      } catch {
        /* 忽略坏数据，用默认 */
      }
    }
    loaded.value = true;
  }

  /** 变更即保存（防抖避免连续写入） */
  let saveTimer: number | undefined;
  function scheduleSave() {
    if (saveTimer !== undefined) window.clearTimeout(saveTimer);
    saveTimer = window.setTimeout(save, 300);
  }

  function save() {
    api.setSetting("defaultStorage", settings.defaultStorage);
    api.setSetting("customDir", settings.customDir);
    api.setSetting("shardSize", String(settings.shardSize));
    api.setSetting("groupBy", settings.groupBy);
    api.setSetting("gridCols", String(settings.gridCols));
    api.setSetting("recycleDays", String(settings.recycleDays));
    api.setSetting("jsonSync", String(settings.jsonSync));
    api.setSetting("presetColors", JSON.stringify(settings.presetColors));
  }

  watch(settings, scheduleSave, { deep: true });

  return { settings, loaded, load, save };
});

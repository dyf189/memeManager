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
    // 数值解析：空/缺失用默认值，合法数字（含 0）原样保留
    const numOr = (raw: string | undefined, def: number): number => {
      if (raw === undefined || raw === "") return def;
      const n = Number(raw);
      return Number.isFinite(n) ? n : def;
    };
    if (saved.defaultStorage) settings.defaultStorage = saved.defaultStorage;
    if (saved.customDir) settings.customDir = saved.customDir;
    settings.shardSize = Math.max(1, numOr(saved.shardSize, defaults.shardSize));
    if (saved.groupBy) settings.groupBy = saved.groupBy;
    settings.gridCols = Math.max(4, numOr(saved.gridCols, defaults.gridCols));
    settings.recycleDays = numOr(saved.recycleDays, defaults.recycleDays);
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
    const pairs: [string, string][] = [
      ["defaultStorage", settings.defaultStorage],
      ["customDir", settings.customDir],
      ["shardSize", String(settings.shardSize)],
      ["groupBy", settings.groupBy],
      ["gridCols", String(settings.gridCols)],
      ["recycleDays", String(settings.recycleDays)],
      ["jsonSync", String(settings.jsonSync)],
      ["presetColors", JSON.stringify(settings.presetColors)],
    ];
    for (const [key, value] of pairs) {
      api.setSetting(key, value).catch((e) => console.warn(`[settings] 保存 ${key} 失败:`, e));
    }
  }

  watch(settings, scheduleSave, { deep: true });

  return { settings, loaded, load, save };
});

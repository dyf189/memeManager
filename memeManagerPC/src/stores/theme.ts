import { ref } from "vue";
import { defineStore } from "pinia";
import { alphaHex, hexToRgb, mixHex, rgbToHex, textColorFor } from "../utils/color";

export type ThemeMode = "light" | "dark" | "system";

const STORAGE_KEY = "mm-theme";
const ACCENT_KEY = "mm-accent";

/** 预设主题色（必备：Apple 蓝 + Cloudflare 橙，其余为 iOS 系统色系） */
export const ACCENT_PRESETS = [
  "#007aff", // Apple 蓝（默认）
  "#f38020", // Cloudflare 橙
  "#5856d6", // 靛
  "#af52de", // 紫
  "#ff2d55", // 玫红
  "#ff3b30", // 红
  "#ffcc00", // 黄
  "#34c759", // 绿
  "#5ac8fa", // 青
  "#8e8e93", // 石墨
];

export const DEFAULT_ACCENT = "#007aff";

/**
 * 主题管理：
 * - 明暗：light / dark / system（<html class="dark"> + data-theme 驱动 CSS 变量）
 * - 主题色：自定义强调色，以 documentElement 内联变量覆盖 :root，
 *   并按明暗派生 --accent-strong / --accent-bg / --selected-bg /
 *   --accent-contrast 与 Element Plus 主色阶梯（浅色向白混合、深色向黑混合）
 */
export const useThemeStore = defineStore("theme", () => {
  const mode = ref<ThemeMode>(
    (localStorage.getItem(STORAGE_KEY) as ThemeMode) ?? "system"
  );
  const dark = ref(false);

  const storedAccent = localStorage.getItem(ACCENT_KEY);
  const accent = ref<string>(
    storedAccent && hexToRgb(storedAccent) ? storedAccent.toLowerCase() : DEFAULT_ACCENT
  );

  const mq = window.matchMedia("(prefers-color-scheme: dark)");

  /** 计算主题色及其全部派生色 */
  function accentVars(hex: string, isDark: boolean): Record<string, string> {
    const vars: Record<string, string> = {};
    const set = (name: string, v: string | null) => {
      if (v) vars[name] = v;
    };

    set("--accent", hex);
    set("--accent-contrast", textColorFor(hex));
    // 深浅派生：浅色模式 strong 更深，深色模式 strong 更亮
    set("--accent-strong", mixHex(hex, isDark ? "#ffffff" : "#000000", isDark ? 0.33 : 0.15));
    set("--accent-bg", alphaHex(hex, isDark ? 0.18 : 0.1));
    set("--selected-bg", alphaHex(hex, isDark ? 0.16 : 0.08));

    // Element Plus 主色阶梯：浅色向白混合，深色向黑混合
    set("--el-color-primary", hex);
    const base = isDark ? "#000000" : "#ffffff";
    for (const [step, t] of [[3, 0.3], [5, 0.5], [7, 0.7], [8, 0.8], [9, 0.9]] as const) {
      set(`--el-color-primary-light-${step}`, mixHex(hex, base, t));
    }
    set("--el-color-primary-dark-2", mixHex(hex, isDark ? "#ffffff" : "#000000", 0.2));
    return vars;
  }

  function applyAccent() {
    for (const [key, value] of Object.entries(accentVars(accent.value, dark.value))) {
      document.documentElement.style.setProperty(key, value);
    }
  }

  function apply() {
    dark.value = mode.value === "dark" || (mode.value === "system" && mq.matches);
    document.documentElement.classList.toggle("dark", dark.value);
    document.documentElement.setAttribute("data-theme", dark.value ? "dark" : "light");
    applyAccent();
  }

  function init() {
    apply();
    mq.addEventListener("change", () => {
      if (mode.value === "system") apply();
    });
  }

  function setMode(m: ThemeMode) {
    mode.value = m;
    localStorage.setItem(STORAGE_KEY, m);
    apply();
  }

  function setAccent(color: string) {
    const rgb = hexToRgb(color);
    if (!rgb) return;
    accent.value = rgbToHex(rgb);
    localStorage.setItem(ACCENT_KEY, accent.value);
    applyAccent();
  }

  return { mode, dark, accent, init, setMode, setAccent, apply };
});

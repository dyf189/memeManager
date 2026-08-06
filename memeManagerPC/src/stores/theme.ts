import { ref } from "vue";
import { defineStore } from "pinia";

export type ThemeMode = "light" | "dark" | "system";

const STORAGE_KEY = "mm-theme";

/**
 * 主题管理：light / dark / system。
 * - 通过 <html class="dark"> 驱动 Element Plus 暗色变量
 * - 通过 <html data-theme> 驱动自定义 CSS 变量
 * - system 模式跟随系统偏好并实时响应变化
 */
export const useThemeStore = defineStore("theme", () => {
  const mode = ref<ThemeMode>(
    (localStorage.getItem(STORAGE_KEY) as ThemeMode) ?? "system"
  );
  const dark = ref(false);

  const mq = window.matchMedia("(prefers-color-scheme: dark)");

  function apply() {
    dark.value = mode.value === "dark" || (mode.value === "system" && mq.matches);
    document.documentElement.classList.toggle("dark", dark.value);
    document.documentElement.setAttribute("data-theme", dark.value ? "dark" : "light");
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

  return { mode, dark, init, setMode, apply };
});

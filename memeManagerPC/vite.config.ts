import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

// Tauri 开发时前端由 Vite 提供，端口固定 1420（见 tauri.conf.json 的 devUrl）
export default defineConfig({
  plugins: [vue()],
  clearScreen: false,
  server: {
    port: 1420,
    strictPort: true,
    watch: {
      // 不要监听 Rust 侧的文件变化
      ignored: ["**/src-tauri/**"],
    },
  },
});

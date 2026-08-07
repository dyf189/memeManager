import { createRouter, createWebHashHistory } from "vue-router";

// Tauri 打包后是 file:// 协议，必须用 hash 模式路由
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/", redirect: "/album" },
    { path: "/album", name: "album", component: () => import("../views/AlbumView.vue") },
    { path: "/recycle", name: "recycle", component: () => import("../views/RecycleView.vue") },
    { path: "/tags", name: "tags", component: () => import("../views/TagManagerView.vue") },
    { path: "/settings", name: "settings", component: () => import("../views/SettingsView.vue") },
  ],
});

export default router;

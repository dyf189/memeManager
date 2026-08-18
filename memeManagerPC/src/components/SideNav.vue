<script setup lang="ts">
// 侧边导航栏（Cloudflare Dashboard 风格）
const items = [
  { path: "/album", label: "相册", icon: "Picture" },
  { path: "/recycle", label: "回收站", icon: "Delete" },
  { path: "/tags", label: "标签", icon: "Collection" },
  { path: "/settings", label: "设置", icon: "Setting" },
];
</script>

<template>
  <aside class="side-nav">
    <div class="brand">
      <span class="brand-text">表情包管理器</span>
    </div>

    <nav class="nav-list">
      <router-link
        v-for="item in items"
        :key="item.path"
        :to="item.path"
        class="nav-item"
        active-class="nav-active"
      >
        <el-icon :size="17"><component :is="item.icon" /></el-icon>
        <span class="nav-label">{{ item.label }}</span>
      </router-link>
    </nav>

    <div class="nav-footer">v0.1.0 · Tauri</div>
  </aside>
</template>

<style scoped>
.side-nav {
  width: 216px;
  flex: none;
  display: flex;
  flex-direction: column;
  background: var(--sidebar-bg);
  backdrop-filter: blur(20px) saturate(1.8);
  -webkit-backdrop-filter: blur(20px) saturate(1.8);
  border-right: 1px solid var(--divider);
  transition: background 0.2s ease;
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 20px 16px 16px;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
}

.nav-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  color: var(--text-main);
  text-decoration: none;
  font-size: 13.5px;
  font-weight: 500;
  transition: background 0.15s ease-out, color 0.15s ease-out;
}

.nav-item:hover {
  background: var(--hover-bg);
}

.nav-item:active {
  background: var(--active-bg);
}

/* 激活项：macOS 侧栏选中 —— 蓝色着色（图标+文字+浅蓝底） */
.nav-active,
.nav-active:hover {
  background: var(--accent-bg);
  color: var(--accent);
}

.nav-active :deep(.el-icon) {
  color: var(--accent);
}

.nav-footer {
  padding: 14px 16px;
  font-size: 11px;
  color: var(--text-muted);
  border-top: 1px solid var(--divider);
}
</style>

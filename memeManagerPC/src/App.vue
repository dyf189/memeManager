<script setup lang="ts">
import SideNav from "./components/SideNav.vue";
</script>

<template>
  <div class="app-shell">
    <!-- 左侧导航 + 右侧面板式内容区（桌面布局） -->
    <SideNav />
    <main class="app-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  height: 100%;
}

.app-content {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  position: relative;
  padding: 12px;
  background: var(--app-bg);
}
</style>

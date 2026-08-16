<script setup lang="ts">
import SideNav from "./components/SideNav.vue";
</script>

<template>
  <div class="app-shell">
    <!-- 左侧导航 + 右侧面板式内容区（桌面布局） -->
    <SideNav />
    <main class="app-content">
      <router-view v-slot="{ Component }">
        <transition name="view-fade" mode="out-in">
          <component :is="Component" :key="$route.path" />
        </transition>
      </router-view>
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

/* 页面切换：轻微淡入上移（ease-out 即时响应感） */
.view-fade-enter-active {
  transition: opacity 0.16s var(--ease-out), transform 0.16s var(--ease-out);
}

.view-fade-leave-active {
  transition: opacity 0.1s ease-out;
}

.view-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.view-fade-leave-to {
  opacity: 0;
}
</style>

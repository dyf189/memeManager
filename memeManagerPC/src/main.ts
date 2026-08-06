import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import zhCn from "element-plus/dist/locale/zh-cn.mjs";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";
import "element-plus/dist/index.css";
// Element Plus 暗色主题变量（配合 <html class="dark"> 生效）
import "element-plus/theme-chalk/dark/css-vars.css";

import App from "./App.vue";
import router from "./router";
import { useThemeStore } from "./stores/theme";
import "./styles/main.css";

const app = createApp(App);

// 全局注册所有 Element Plus 图标，模板里可直接用 <Picture /> 等
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

const pinia = createPinia();
app.use(pinia);

// 挂载前先应用主题，避免暗色模式首屏闪烁
useThemeStore(pinia).init();

app.use(router);
app.use(ElementPlus, { locale: zhCn });
app.mount("#app");

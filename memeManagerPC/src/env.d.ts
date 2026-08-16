/// <reference types="vite/client" />

declare module "*.vue" {
  import type { DefineComponent } from "vue";
  const component: DefineComponent<{}, {}, any>;
  export default component;
}

// element-plus 的 locale 子模块不带类型声明
declare module "element-plus/dist/locale/zh-cn.mjs" {
  import type { Language } from "element-plus/es/locale";
  const locale: Language;
  export default locale;
}

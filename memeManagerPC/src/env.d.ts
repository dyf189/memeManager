/// <reference types="vite/client" />

// element-plus 中文语言包为纯 JS 的 .mjs，无自带类型声明
declare module "element-plus/dist/locale/zh-cn.mjs" {
  import type { Language } from "element-plus/es/locale";
  const locale: Language;
  export default locale;
}

declare module "*.vue" {
  import type { DefineComponent } from "vue";
  const component: DefineComponent<{}, {}, any>;
  export default component;
}

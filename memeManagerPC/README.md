# memeManager PC（表情包管理器 · 桌面版）

将 Android 原项目（见根目录 `goals.md` 的功能规划）用 **Tauri 2 + Vue 3** 重写的桌面版。

> 当前阶段：**UI 骨架**（mock 数据驱动，尚未接入 Rust 后端）。界面布局、交互均按 `goals.md` 实现，方便先调整视觉再接入真实数据。

## 技术栈

- 前端：Vite + Vue 3 + TypeScript + Element Plus + Pinia + Vue Router
- 后端：Tauri 2（Rust），数据层后续用 SQLite (rusqlite) + FTS5

## 本地运行

```bash
npm install        # 安装前端依赖
npm run tauri dev  # 启动桌面开发环境（Vite + 编译 Rust）
```

仅看网页效果（不启动桌面窗口）：

```bash
npm run dev        # http://localhost:1420
```

## 目录结构

```
memeManagerPC/
├── index.html                 # Vite 入口
├── vite.config.ts             # Vite 配置（端口 1420，供 tauri dev 使用）
├── src/
│   ├── main.ts                # 应用入口（注册 Element Plus / Pinia / Router）
│   ├── App.vue                # 主布局（内容区 + 底部导航）
│   ├── types.ts               # 领域类型（Media / Tag / FilterState）
│   ├── api/index.ts           # ★ API 层：目前返回 mock，后续替换为 Tauri invoke
│   ├── mock/data.ts           # mock 媒体与标签数据
│   ├── stores/                # Pinia：album（相册）、tags（标签）
│   ├── components/            # 网格/列表/详情/批量栏/筛选面板等 UI 组件
│   ├── views/                 # 三个页面：相册、标签管理、设置
│   └── utils/                 # 时间分组、颜色、格式化、高亮
└── src-tauri/                 # Tauri/Rust 侧（暂为默认模板）
```

## 下一步

1. 调整 UI 到你满意的效果
2. Rust 侧实现数据层（SQLite + 文件扫描），替换 `src/api/index.ts` 中的 mock
3. 按 `goals.md` 依次实现：导入文件夹 → 标签 → 搜索 → 筛选 → 回收站 → `.emp` 导入导出

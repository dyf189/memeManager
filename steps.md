## 五、基础架构搭建记录（已完成）

### 2026-06-22 环境配置与基础架构

#### 1. 依赖版本更新

全部第三方库已更新至当前最新稳定版，通过 Sync 验证。

| 库 | 版本 | 类型 |
|---|---|---|
| AGP | 9.2.1 | 最新稳定（保留原版本）|
| Kotlin | 2.2.10 | 最新稳定（保留原版本）|
| Gradle | 9.4.1 | 最新稳定（保留原版本）|
| KSP | 2.3.9 | 新增 — Room/Hilt 注解处理 |
| Compose BOM | 2026.06.00 | 升级（原 2026.02.01）|
| core-ktx | 1.19.0 | 升级（原 1.10.1）|
| lifecycle-runtime-ktx | 2.11.0 | 升级（原 2.6.1）|
| activity-compose | 1.13.0 | 升级（原 1.8.0）|
| Navigation Compose | 2.9.8 | 新增 |
| Room | 2.8.4 | 新增 |
| Paging Compose | 3.5.0 | 新增 |
| Coil + Coil Compose | 2.7.0 | 新增 |
| Hilt + Hilt Compiler | 2.59.2 | 新增 |
| Hilt Navigation Compose | 1.3.0 | 新增 |
| DataStore Preferences | 1.2.1 | 新增 |
| Okio | 3.17.0 | 新增 |
| WorkManager | 2.11.2 | 新增 |
| AndroidX JUnit ext | 1.3.0 | 升级（原 1.1.5）|
| espresso-core | 3.7.0 | 升级（原 3.5.1）|
| JUnit | 4.13.2 | 保留不变 |

兼容性验证：
- 所有依赖版本均由 Google Maven / Maven Central 的 `maven-metadata.xml` 确认为最新 `<release>` 版本，无 alpha/beta/rc 后缀。
- Kotlin 2.2.10 + Compose Compiler Extension（`org.jetbrains.kotlin.plugin.compose`）与 Compose BOM 2026.06.00 兼容。
- AGP 9.2.1 是截至查询时的最新稳定版（9.3.x 仅 RC，9.4.x 仅 alpha）。

#### 2. Hilt 依赖注入

创建文件：
- **`MemeManagerApp.kt`**：`@HiltAndroidApp` 注解的 Application 类。
- **`AndroidManifest.xml`**：`android:name=".MemeManagerApp"` 注册。
- **`di/DatabaseModule.kt`**：`@Module @InstallIn(SingletonComponent::class)`，提供 `AppDatabase`、`MediaDao`、`TagDao` 三个 `@Provides @Singleton` 实例。

#### 3. Room 数据库结构

`data/local/` 包下：

- **Entity**：
  - `MediaEntity`（表名 `media`）：id, name, filePath, type（MediaType 枚举：IMAGE/VIDEO/GIF）, size, width, height, createdAt, updatedAt。
  - `TagEntity`（表名 `tags`，name 唯一索引）：id, name。
  - `MediaTagCrossRef`（表名 `media_tag_cross_ref`，双主键 + 双外键 CASCADE 删除）：mediaId, tagId。
- **DAO**：
  - `MediaDao`：insert/update/delete/deleteById，getById(Flow)、getAll(Flow)、getByType(Flow)。
  - `TagDao`：insert/update/delete，getById(Flow)、getAll(Flow)、search(Flow)、getByName(suspend)。
- **Database**：
  - `AppDatabase`（version=1, exportSchema=false），注册 3 实体 + 2 DAO。

#### 4. 导航框架

- **`MainActivity.kt`**：添加 `@AndroidEntryPoint`，使用 `rememberNavController()` + `NavHost`。
- **三个路由**：
  | 路由 | 标签 | 图标 | Composable |
  |------|------|------|------------|
  | `album` | 相册 | `Icons.Filled.Home` | `AlbumScreen` |
  | `tags` | 标签 | `Icons.Filled.Star` | `TagsScreen` |
  | `settings` | 设置 | `Icons.Filled.Settings` | `SettingsScreen` |
- **底部导航栏**：`NavigationBar` + `NavigationBarItem`，图标来自 `material-icons-core`。
- **`ui/screen/Screens.kt`**：三个占位 Composable，居中显示标题文字。

#### 5. 当前项目结构

```
app/src/main/java/com/mememanager/
├── MemeManagerApp.kt              # @HiltAndroidApp
├── MainActivity.kt                # @AndroidEntryPoint + NavHost + 底部导航
├── di/
│   └── DatabaseModule.kt          # Hilt Module
├── data/local/
│   ├── AppDatabase.kt             # Room Database
│   ├── dao/
│   │   ├── MediaDao.kt
│   │   └── TagDao.kt
│   └── entity/
│       ├── MediaEntity.kt
│       ├── TagEntity.kt
│       └── MediaTagCrossRef.kt
└── ui/
    ├── screen/
    │   └── Screens.kt             # 占位页面
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

项目已通过 AS Sync，可在此基础上按 `goals.md` 第四节的 MVVM + Repository 架构继续开发。实体设计已与规划文档中的核心实体保持对应（后续可按需补充 FTS 虚拟表、source/description/exportedHash 等字段）。

---

## 第一步：数据层完善

### 1.1 更新 Entity

**MediaEntity** — 补充字段：
- `storageType: StorageType`（PRIVATE / PUBLIC / EXTERNAL）
- `source: String?`（来源：相机、微信、QQ 等）
- `description: String?`（纯文本描述）
- `takenTime: Long?`（拍摄时间，优先读 EXIF）
- `isDeleted: Boolean = false`（软删除标记）
- `deletedTime: Long? = null`（删除时间）
- `exportedHash: String? = null`（导出哈希标记）

**TagEntity** — 补充字段：
- `bgColor: Int`（标签背景颜色）
- `isReserved: Boolean = false`（保留标签，如[已导出]不可删除）
- `sortOrder: Int = 0`（排序序号）

**新建类：**
- `StorageType` 枚举（与 MediaEntity 同文件）
- `Converters.kt` — Room TypeConverter（枚举 ↔ String）
- `MediaWithTags.kt` — @Relation 封装类（Media + 关联 Tag 列表）

### 1.2 完善 DAO

**MediaDao** — 扩展：
- `getAlbumPagingSource()` / `getAlbumPagingSourceByType()` — Paging 3 分页支持
- `softDelete()` / `restore()` — 回收站软删除/还原
- `getDeletedPagingSource()` — 回收站列表
- `getMediaWithTagsById()` — 详情页用
- `getByIdSuspend()` — 非 Flow 版本

**MediaTagRefDao** — 新建：
- 增删改查 media ↔ tag 关联关系

**完成文件列表：**

| 文件 | 操作 | 说明 |
|------|------|------|
| `data/local/entity/MediaEntity.kt` | 修改 | 补充 storageType/source/description/takenTime/isDeleted/deletedTime/exportedHash 字段，添加 StorageType 枚举 |
| `data/local/entity/TagEntity.kt` | 修改 | 补充 bgColor/isReserved/sortOrder 字段 |
| `data/local/entity/MediaWithTags.kt` | 新建 | @Relation 封装类（Media + 关联 Tag 列表）|
| `data/local/Converters.kt` | 新建 | Room TypeConverter（枚举 ↔ String）|
| `data/local/dao/MediaDao.kt` | 重写 | 增加 PagingSource / 软删除 / MediaWithTags 关系查询 |
| `data/local/dao/MediaTagRefDao.kt` | 新建 | 多对多关联 CRUD |
| `data/local/AppDatabase.kt` | 修改 | version 1→2，注册 TypeConverters，暴露 mediaTagRefDao |
| `di/DatabaseModule.kt` | 修改 | 添加 provideMediaTagRefDao，fallbackToDestructiveMigration |

### 1.3 更新 AppDatabase（已完成）

- 注册新 Entity（StorageType 需 TypeConverter）
- 注册 MediaTagRefDao
- version 1→2（实体新增字段）

### 1.4 创建 Repository 层

- `MediaRepository` — 封装媒体增删改查/分页/筛选
- `TagRepository` — 封装标签管理

**完成文件：**

| 文件 | 说明 |
|------|------|
| `data/repository/MediaRepository.kt` | 新建 — 分页查询、CRUD、回收站、标签关联操作，Paging 3 Pager 封装 |
| `data/repository/TagRepository.kt` | 新建 — 标签 CRUD、搜索、预设颜色分配 |

### 1.5 创建 AlbumViewModel

- Paging 3 提供分页相册数据
- `StateFlow<AlbumUiState>`
- 筛选条件管理

**完成文件：**

| 文件 | 说明 |
|------|------|
| `ui/viewmodel/AlbumUiState.kt` | 新建 — FilterState（7 种筛选条件）、ViewMode、AlbumUiState |
| `ui/viewmodel/AlbumViewModel.kt` | 新建 — @HiltViewModel，筛选变动自动重建 PagingData 流，多选模式管理，批量删除/标签操作 |

---

## 当前项目结构

```
app/src/main/java/com/mememanager/
├── MemeManagerApp.kt              # @HiltAndroidApp
├── MainActivity.kt                # @AndroidEntryPoint + NavHost + 底部导航
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt         # Room Database (version 2)
│   │   ├── Converters.kt          # TypeConverter (枚举↔String)
│   │   ├── dao/
│   │   │   ├── MediaDao.kt        # PagingSource / 软删除 / 关系查询
│   │   │   ├── MediaTagRefDao.kt  # 多对多关联 CRUD
│   │   │   └── TagDao.kt
│   │   └── entity/
│   │       ├── MediaEntity.kt     # 完整字段 + MediaType/StorageType 枚举
│   │       ├── MediaTagCrossRef.kt
│   │       ├── MediaWithTags.kt   # @Relation 封装
│   │       └── TagEntity.kt       # bgColor/isReserved/sortOrder
│   └── repository/
│       ├── MediaRepository.kt     # 媒体业务封装
│       └── TagRepository.kt       # 标签业务封装
├── di/
│   └── DatabaseModule.kt          # Hilt Module (含 fallbackToDestructiveMigration)
└── ui/
    ├── screen/
    │   ├── Screens.kt             # TagsScreen / SettingsScreen 占位
    │   └── album/
    │       ├── AlbumItem.kt       # AlbumItem sealed class
    │       ├── AlbumScreen.kt     # 主相册页面（假数据驱动）
    │       ├── AlbumGridItem.kt   # 网格缩略图
    │       ├── BatchActionBar.kt  # 多选操作栏
    │       ├── FilterPanel.kt     # 筛选面板
    │       ├── TagChipRow.kt      # 标签胶囊栏
    │       └── TimeGroupHeader.kt # 时间分组头
    ├── theme/
    │   ├── Color.kt
    │   ├── Theme.kt
    │   └── Type.kt
    ├── util/
    │   └── TimeGroupUtil.kt       # 时间分组工具
    └── viewmodel/
        ├── AlbumUiState.kt        # 相册 UI 状态 + FilterState
        └── AlbumViewModel.kt      # @HiltViewModel + Paging 3
```

---

## 第二步：搭建相册 UI 页面（假数据驱动）

### 2.1 创建假数据源

在 `data/fake/` 下创建 `FakeDataSource.kt`：
- 生成 30+ 条 `MediaWithTags` 假数据（不同时间、类型、标签组合）
- 包含若干 `TagEntity` 假标签（带颜色）
- 模拟时间分布：刚刚、几小时前、昨天、前几天、上个月、去年

### 2.2 创建时间分组工具 ✅

`ui/util/TimeGroupUtil.kt`：
- 根据时间戳计算分组标签（刚刚 / X分钟前 / X小时前 / 昨天 / 前天 / 三天前 / MM-dd / yyyy-MM-dd）
- 分组 ID 生成（用于 LazyVerticalGrid stickyHeader 排序）

### 2.3 创建 AlbumScreen UI 组件 ✅

新建 `ui/screen/album/` 包，拆分以下组件：

| 组件 | 文件 | 说明 |
|------|------|------|
| `AlbumScreen` | `AlbumScreen.kt` | 主页面编排（标签栏+筛选面板+网格+批量操作栏），内嵌 16 条样本数据 |
| `AlbumGridItem` | `AlbumGridItem.kt` | 单个缩略图卡片（占位色块按类型区分：蓝=图片/绿=GIF/粉=视频，右下角标签圆点最多4个+N） |
| `TimeGroupHeader` | `TimeGroupHeader.kt` | 时间分组粘性头（stickyHeader） |
| `TagChipRow` | `TagChipRow.kt` | 横向滑动标签胶囊筛选栏（全部+6个标签，选中高亮） |
| `FilterPanel` | `FilterPanel.kt` | 下拉展开的筛选面板（类型 FilterChip + 应用/清除按钮） |
| `BatchActionBar` | `BatchActionBar.kt` | 多选模式顶部操作栏（取消+已选数量+打标签/导出/删除/分享图标） |
| `AlbumItem` | `AlbumItem.kt` | AlbumItem sealed class（Header / Media）用于时间分组列表 |

**完成文件列表：**

| 文件 | 操作 | 说明 |
|------|------|------|
| `ui/util/TimeGroupUtil.kt` | 新建 | 时间分组工具（TimeGroup 模型 + getGroup 方法） |
| `ui/screen/album/AlbumItem.kt` | 新建 | AlbumItem sealed class |
| `ui/screen/album/AlbumScreen.kt` | 新建 | 主相册页面，内嵌 16 条样本数据，时间分组 + stickyHeader 网格 |
| `ui/screen/album/AlbumGridItem.kt` | 新建 | 网格缩略图（类型色块 + 标签圆点 + 选中蒙层 + 存储类型标记） |
| `ui/screen/album/TimeGroupHeader.kt` | 新建 | 时间分组粘性头 |
| `ui/screen/album/TagChipRow.kt` | 新建 | 标签胶囊筛选栏 |
| `ui/screen/album/FilterPanel.kt` | 新建 | 下拉筛选面板 |
| `ui/screen/album/BatchActionBar.kt` | 新建 | 多选批量操作栏 |
| `ui/screen/Screens.kt` | 修改 | 移除 AlbumScreen 占位，改为引用 album 包实现 |

### 2.4 更新 AlbumViewModel 支持假数据

- 在 ViewModel 中创建 `fakePagingDataFlow`，将假数据转为 `Flow<PagingData<MediaWithTags>>`
- 提供 `useFakeData` 开关，方便后续切换为真实数据库

### 2.5 时间分组 Paging 适配

由于 Paging 3 默认按 createdAt DESC 分页，时间分组头需要在 UI 层计算：
- 在 `AlbumViewModel` 中将 `MediaWithTags` 映射为带分组信息的 UI 模型 `AlbumItem`
- `AlbumItem` sealed class：`HeaderItem(groupLabel)` + `MediaItem(mediaWithTags)`
- `LazyVerticalGrid` 通过 `items(..., key = ...)` 交替渲染 Header/Media

### 依赖关系

2.1 ← 2.4 ← 2.5 ← 2.3（组件需要 ViewModel 的数据来渲染）
2.2 ← 2.5（时间分组逻辑被 Paging 适配层使用）

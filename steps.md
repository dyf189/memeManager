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

## 第一步：数据层完善 ✅

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

### 1.3 更新 AppDatabase ✅

- 注册新 Entity（StorageType 需 TypeConverter）
- 注册 MediaTagRefDao
- version 1→2（实体新增字段）

### 1.4 创建 Repository 层 ✅

- `MediaRepository` — 封装媒体增删改查/分页/筛选
- `TagRepository` — 封装标签管理

**完成文件：**

| 文件 | 说明 |
|------|------|
| `data/repository/MediaRepository.kt` | 新建 — 分页查询、CRUD、回收站、标签关联操作，Paging 3 Pager 封装 |
| `data/repository/TagRepository.kt` | 新建 — 标签 CRUD、搜索、预设颜色分配 |

### 1.5 创建 AlbumViewModel ✅

- Paging 3 提供分页相册数据
- `StateFlow<AlbumUiState>`
- 筛选条件管理

**完成文件：**

| 文件 | 说明 |
|------|------|
| `ui/viewmodel/AlbumUiState.kt` | 新建 — FilterState（7 种筛选条件）、ViewMode、AlbumUiState |
| `ui/viewmodel/AlbumViewModel.kt` | 新建 — @HiltViewModel，筛选变动自动重建 PagingData 流，多选模式管理，批量删除/标签操作 |

---

## 第二步：搭建 UI 页面 ✅

### 2.1 假数据源 — 跳过

假数据已通过各页面的 `@Preview` 函数覆盖（仅在 `main-ui-preview` 分支），不需要独立的 `FakeDataSource.kt`。

### 2.2 时间分组工具 ✅

`ui/util/TimeGroupUtil.kt`：
- 根据时间戳计算分组标签（刚刚 / X分钟前 / X小时前 / 昨天 / 前天 / 三天前 / MM-dd / yyyy-MM-dd）
- 分组 ID 生成（用于 LazyVerticalGrid stickyHeader 排序）

### 2.3 AlbumScreen UI 组件 ✅

新建 `ui/screen/album/` 包，拆分以下组件：

| 组件 | 文件 | 说明 |
|------|------|------|
| `AlbumScreen` | `AlbumScreen.kt` | 主页面编排（标签栏+筛选面板+网格+批量操作栏），由 AlbumViewModel 驱动 |
| `AlbumGridItem` | `AlbumGridItem.kt` | 单个缩略图卡片（Coil 加载 + 右下角标签圆点最多4个+N + 类型角标） |
| `TimeGroupHeader` | `TimeGroupHeader.kt` | 时间分组粘性头（stickyHeader） |
| `TagChipRow` | `TagChipRow.kt` | 横向滑动标签胶囊筛选栏（全部+各标签，选中高亮） |
| `FilterPanel` | `FilterPanel.kt` | 覆盖式筛选面板（类型 FilterChip，即时应用） |
| `BatchActionBar` | `BatchActionBar.kt` | 多选模式顶部操作栏（取消+已选数量+打标签/导出/删除/分享图标） |
| `AlbumItem` | `AlbumItem.kt` | AlbumItem sealed class（Header / Media）用于时间分组列表 |

**完成文件列表：**

| 文件 | 操作 | 说明 |
|------|------|------|
| `ui/util/TimeGroupUtil.kt` | 新建 | 时间分组工具（TimeGroup 模型 + getGroup 方法） |
| `ui/screen/album/AlbumItem.kt` | 新建 | AlbumItem sealed class |
| `ui/screen/album/AlbumScreen.kt` | 新建 | 主相册页面，消费 AlbumViewModel 的 PagingData + stickyHeader |
| `ui/screen/album/AlbumGridItem.kt` | 新建 | 网格缩略图（Coil 图片 + 标签圆点 + 选中蒙层 + 存储类型标记 + 长按多选） |
| `ui/screen/album/TimeGroupHeader.kt` | 新建 | 时间分组粘性头 |
| `ui/screen/album/TagChipRow.kt` | 新建 | 标签胶囊筛选栏 |
| `ui/screen/album/FilterPanel.kt` | 新建 | 覆盖式筛选面板（类型 FilterChip，即时应用无按钮） |
| `ui/screen/album/BatchActionBar.kt` | 新建 | 多选批量操作栏 |
| `ui/screen/Screens.kt` | 修改 | 移除 AlbumScreen 占位，改为引用 album 包实现 |

### 2.4 AlbumViewModel 接入真实数据 ✅

- AlbumViewModel 新增 `TagRepository` 注入 + `tags: StateFlow<List<TagEntity>>`
- AlbumScreen 改为 `hiltViewModel()` 获取，消费 `pagingDataFlow.collectAsLazyPagingItems()`
- 时间分组头在 UI 层计算：for 循环遍历 `itemSnapshotList`，检测分组边界插入 `stickyHeader`
- 筛选面板的媒体类型选择联动 ViewModel 的 `setTypeFilter()`
- 多选模式：长按 → `enterMultiSelectMode()`，点击 → `toggleSelection()`，批量删除 → `softDeleteSelected()`
- AlbumGridItem 长按改为 `combinedClickable`（原为单独 `clickable`，未接线 onLongClick）

### 2.5 时间分组 Paging 适配 ✅

- 方案：在 AlbumScreen 的 `LazyVerticalGrid` 中直接 for 循环遍历 `lazyPagingItems.itemSnapshotList`
- 每项检测时间分组是否变化（与前一项比较 `sortKey`），变化时插入 `stickyHeader`
- 无需 ViewModel 层映射为 AlbumItem（避免了 PagingData.map 无法插入新 item 的限制）

### 2.6 TagsScreen + TagsViewModel ✅

- 新建 `ui/viewmodel/TagsViewModel.kt`：@HiltViewModel，注入 TagRepository，提供 `tags: StateFlow<List<TagEntity>>` 及 add/update/delete 方法
- TagsScreen 改为 `hiltViewModel()` 获取，消费 `viewModel.tags.collectAsStateWithLifecycle()`
- 新建标签：AlertDialog + OutlinedTextField，默认颜色 0xFF2196F3，确认后写入 Room
- 删除标签：保留标签（`isReserved`）显示锁图标不可删除

---

## 第三步：设置页持久化 ✅

### 3.1 DataStore Preferences 配置

- DataStore 依赖已在 gradle 中配置（`androidx.datastore.preferences`）
- `di/DatabaseModule.kt` 新增 `provideDataStore()` 提供 `DataStore<Preferences>` 单例
- 使用 `preferencesDataStore(name = "settings")` 委托创建

**完成文件：**

| 文件 | 操作 | 说明 |
|------|------|------|
| `data/settings/AppSettings.kt` | 新建 | 6 个设置项数据类 + Preferences Key 常量 |
| `di/DatabaseModule.kt` | 修改 | 添加 DataStore provider |
| `ui/viewmodel/SettingsViewModel.kt` | 新建 | @HiltViewModel，DataStore 读写封装，settings StateFlow |
| `ui/screen/settings/SettingsScreen.kt` | 修改 | 改为 hiltViewModel() 驱动，所有设置项读写 DataStore |

### 3.2 持久化的设置项

| 设置项 | Key | 类型 | 默认值 |
|--------|-----|------|--------|
| 默认存储类型 | `storage_type` | String | "私有内部" |
| 分片大小 | `shard_size_mb` | Int | 100 |
| 主题模式 | `theme_mode` | String | "跟随系统" |
| 每行列数 | `grid_columns` | Int | 3 |
| 回收站保留天数 | `trash_days` | Int | 30 |
| JSON 同步开关 | `json_sync_enabled` | Boolean | false |

关闭 App 重启后设置不丢失。

---

## 第四步：媒体导入 ✅

### 4.1 文件导入工具

`util/MediaImporter.kt`：
- 从 `content://` URI 读取文件信息（文件名、大小、MIME 类型）
- 自动推断媒体类型（IMAGE / GIF / VIDEO）
- 根据存储类型决定复制策略：
  - PRIVATE → 复制到 `filesDir/media/`
  - PUBLIC → 复制到 `externalFilesDir/media/`
  - EXTERNAL → 不复制，仅记录 URI
- 自动处理重名文件（追加 `_1`, `_2` …）

### 4.2 AlbumViewModel 导入支持

- 构造函数新增 `Application` 注入（用于文件操作）
- 新增 `importMedia(uris: List<Uri>)` 方法：
  - 遍历 URI，调用 `MediaImporter.importFromUri()`
  - 每条结果 `mediaRepository.insert()` 写入 Room
  - Paging 3 自动刷新无需手动触发

### 4.3 AlbumScreen 导入入口

- 右下角添加 `FloatingActionButton`（多选模式下隐藏）
- 使用 `ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)` 系统相册选择器
- 选择完成后调用 `viewModel.importMedia(uris)`
- 免权限（系统选择器隔离，无需 READ_MEDIA_*）

**完成文件：**

| 文件 | 操作 | 说明 |
|------|------|------|
| `util/MediaImporter.kt` | 新建 | URI → MediaEntity 导入工具 |
| `ui/viewmodel/AlbumViewModel.kt` | 修改 | 新增 importMedia() + Application 注入 |
| `ui/screen/album/AlbumScreen.kt` | 修改 | 添加 FAB + PickMultipleVisualMedia 选择器 |

---

## 当前项目结构

```
app/src/main/java/com/mememanager/
├── MemeManagerApp.kt
├── MainActivity.kt
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── Converters.kt
│   │   ├── dao/
│   │   │   ├── MediaDao.kt
│   │   │   ├── MediaTagRefDao.kt
│   │   │   └── TagDao.kt
│   │   └── entity/
│   │       ├── MediaEntity.kt
│   │       ├── MediaTagCrossRef.kt
│   │       ├── MediaWithTags.kt
│   │       └── TagEntity.kt
│   ├── repository/
│   │   ├── MediaRepository.kt
│   │   └── TagRepository.kt
│   └── settings/
│       └── AppSettings.kt
├── di/
│   └── DatabaseModule.kt
└── ui/
    ├── screen/
    │   ├── Screens.kt
    │   ├── album/
    │   │   ├── AlbumItem.kt
    │   │   ├── AlbumScreen.kt      # 含 FAB 图片导入
    │   │   ├── AlbumGridItem.kt
    │   │   ├── BatchActionBar.kt
    │   │   ├── FilterPanel.kt
    │   │   ├── TagChipRow.kt
    │   │   └── TimeGroupHeader.kt
    │   ├── detail/
    │   │   └── MediaDetailScreen.kt
    │   ├── settings/
    │   │   └── SettingsScreen.kt
    │   └── tags/
    │       └── TagsScreen.kt
    ├── theme/
    ├── util/
    │   ├── MediaImporter.kt        # 媒体导入工具
    │   └── TimeGroupUtil.kt
    └── viewmodel/
        ├── AlbumUiState.kt
        ├── AlbumViewModel.kt       # 含 importMedia()
        ├── SettingsViewModel.kt
        └── TagsViewModel.kt
```

---

## 第五步：详情页导航 + 数据绑定 ✅

### 5.1 导航架构

- NavHost 新增 `detail/{index}` 路由
- AlbumViewModel 提升为 Activity 作用域，相册和详情页共享
- AlbumScreen 新增 `onNavigateToDetail(index)` 回调
- 单击缩略图：收集当前 `itemSnapshotList` → `setCurrentItems()` → 导航
- 详情页底部导航栏自动隐藏

### 5.2 数据编辑回写 Room

AlbumViewModel 新增三个方法：
- `updateDescription(mediaId, description)`
- `addTag(mediaId, tagId)`
- `removeTag(mediaId, tagId)`

### 5.3 完成文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `ui/viewmodel/AlbumViewModel.kt` | 修改 | currentItems 缓存 + 3 编辑方法 |
| `ui/screen/album/AlbumScreen.kt` | 修改 | onNavigateToDetail 回调 |
| `MainActivity.kt` | 修改 | detail 路由 + 共享 ViewModel + 回调绑定 |

---

## 第六步：设置联动 + 外部导入 ✅

### 6.1 设置联动

- AlbumScreen 新增 `SettingsViewModel` 读取 `gridColumns`
- 移除硬编码 `columns` 参数
- 在设置页改列数，回到相册网格立刻生效

### 6.2 分享接收

- `AndroidManifest.xml` 添加 `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intent-filter
- `MainActivity` 改为 `singleTask`，`onNewIntent` 处理分享
- 从微信/QQ/相册分享图片到本 App，自动导入 Room

### 6.3 SAF 文件导入

- AlbumScreen FAB 改为 `DropdownMenu`：从相册导入 / 从文件导入
- `OpenMultipleDocuments` 支持从文件管理器批量选图

**完成文件：**

| 文件 | 操作 | 说明 |
|------|------|------|
| `ui/screen/album/AlbumScreen.kt` | 修改 | SettingsViewModel 列数 + SAF 文件选择器 + FAB 菜单 |
| `AndroidManifest.xml` | 修改 | ACTION_SEND intent-filter |
| `MainActivity.kt` | 修改 | singleTask + onNewIntent + LaunchedEffect 导入 |

---

## 第七步：待定

可选方向：搜索功能 / 编辑后刷新缓存 / 回收站功能

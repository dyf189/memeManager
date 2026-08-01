# memeManager — 表情包管理器

一款 Android 表情包/媒体管理应用，支持图片、GIF、视频的分类、标签、搜索和多维度筛选。

## 功能

- **媒体管理**：从相册/文件管理器导入，时间分组展示（刚刚/昨天/MM-dd/yyyy-MM-dd）
- **标签系统**：自定义标签 + 颜色，拖拽排序，AND/OR 筛选
- **搜索**：结巴分词 + FTS5 全文搜索，支持智能/普通模式切换，搜索历史
- **回收站**：软删除 + 定时自动清理，可恢复
- **主题**：浅色/深色/跟随系统
- **分享**：系统分享 + 一键保存到相册
- **设置持久化**：DataStore Preferences 保存所有配置项

## 技术栈

| 模块 | 技术 |
|------|------|
| 语言 | Kotlin 100% |
| UI | Jetpack Compose + Material3 |
| 架构 | MVVM + Repository |
| 依赖注入 | Hilt |
| 数据库 | Room (FTS5 + Paging 3) |
| 中文分词 | jieba-analysis |
| 图片加载 | Coil |
| 配置持久化 | DataStore Preferences |
| 导航 | Navigation Compose |

## 项目结构

```
app/src/main/java/com/mememanager/
├── MemeManagerApp.kt            # Application
├── MainActivity.kt              # 单 Activity + NavHost
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt       # Room 数据库
│   │   ├── Converters.kt        # TypeConverter
│   │   ├── dao/                 # DAO 层
│   │   └── entity/              # Entity + Relation
│   ├── repository/              # Repository 层
│   └── settings/                # DataStore 配置
├── di/                          # Hilt 模块
├── ui/
│   ├── components/              # 可复用组件
│   ├── screen/
│   │   ├── album/               # 相册主页
│   │   ├── detail/              # 媒体详情
│   │   ├── search/              # 搜索
│   │   ├── settings/            # 设置
│   │   ├── tags/                # 标签管理器
│   │   └── trash/               # 回收站
│   ├── theme/                   # Material3 主题
│   ├── util/                    # 工具类
│   └── viewmodel/               # ViewModel
└── util/                        # 应用级工具
```

## 运行

Android Studio 打开项目 → Sync Gradle → Run。

最低 SDK: 26 | 目标 SDK: 36

## 许可

MIT License

## 链接

GitHub: https://github.com/dyf189/memeManager

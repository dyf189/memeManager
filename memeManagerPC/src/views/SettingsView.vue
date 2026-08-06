<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { PRESET_TAG_COLORS } from "../utils/color";
import { useThemeStore, type ThemeMode } from "../stores/theme";

// —— 设置值（后续迁入 Pinia + Rust 持久化）——
const settings = reactive({
  defaultStorage: "public", // private | public | external
  shardSize: 100, // MB
  groupBy: "taken", // taken | import
  gridCols: 4,
  recycleDays: 30,
  jsonSync: false,
});

const storageLabels: Record<string, string> = {
  private: "应用私有目录",
  public: "公共目录",
  external: "外部索引（不复制）",
};

const themeLabels: Record<string, string> = {
  light: "浅色",
  dark: "深色",
  system: "跟随系统",
};

const groupLabels: Record<string, string> = {
  taken: "拍摄日期",
  import: "导入日期",
};

const panelTitles: Record<string, string> = {
  storage: "默认存储类型",
  shard: "分片大小上限",
  groupBy: "时间分组依据",
  theme: "主题",
  grid: "网格列数",
  recycle: "回收站保留天数",
  colors: "预设颜色管理",
  about: "关于",
};

// —— 对话框面板管理 ——
const openPanel = ref<string | null>(null);
const panelTitle = computed(() => (openPanel.value ? panelTitles[openPanel.value] : ""));
const dialogVisible = computed({
  get: () => openPanel.value !== null,
  set: (v: boolean) => {
    if (!v) openPanel.value = null;
  },
});
const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${settings.gridCols}, 1fr)`,
}));

const presetColors = ref<string[]>([...PRESET_TAG_COLORS]);

// —— 主题（接入 theme store，选择即时生效）——
const themeStore = useThemeStore();
const themeMode = computed<ThemeMode>({
  get: () => themeStore.mode,
  set: (v: ThemeMode) => themeStore.setMode(v),
});

async function clearRecycle() {
  try {
    await ElMessageBox.confirm("确定清空回收站吗？删除后不可恢复。", "清空回收站", {
      type: "warning",
      confirmButtonText: "清空",
      cancelButtonText: "取消",
    });
  } catch {
    return;
  }
  ElMessage.success("回收站已清空（mock）");
}

function restoreColors() {
  presetColors.value = [...PRESET_TAG_COLORS];
  ElMessage.success("已恢复默认颜色");
}

function todo() {
  ElMessage.info("待接入 Rust 后端");
}
</script>

<template>
  <div class="settings-view">
    <header class="settings-top">
      <span class="settings-title">设置</span>
    </header>

    <div class="settings-body">
      <!-- 存储 -->
      <section class="group">
        <h3 class="group-title">存储</h3>
        <div class="item" @click="openPanel = 'storage'">
          <span class="item-label">默认存储类型</span>
          <span class="item-value">{{ storageLabels[settings.defaultStorage] }}</span>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="item" @click="openPanel = 'shard'">
          <span class="item-label">分片大小上限</span>
          <span class="item-value">{{ settings.shardSize }} MB</span>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="item" @click="todo()">
          <span class="item-label">存储空间占用</span>
          <span class="item-value">—</span>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
      </section>

      <!-- 显示 -->
      <section class="group">
        <h3 class="group-title">显示</h3>
        <div class="item" @click="openPanel = 'groupBy'">
          <span class="item-label">时间分组依据</span>
          <span class="item-value">{{ groupLabels[settings.groupBy] }}</span>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="item" @click="openPanel = 'theme'">
          <span class="item-label">主题</span>
          <span class="item-value">{{ themeLabels[themeStore.mode] }}</span>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="item" @click="openPanel = 'grid'">
          <span class="item-label">网格列数</span>
          <span class="item-value">{{ settings.gridCols }} 列</span>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
      </section>

      <!-- 回收站 -->
      <section class="group">
        <h3 class="group-title">回收站</h3>
        <div class="item" @click="openPanel = 'recycle'">
          <span class="item-label">保留天数</span>
          <span class="item-value">{{ settings.recycleDays }} 天</span>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="item" @click="clearRecycle">
          <span class="item-label danger-text">立即清空回收站</span>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
      </section>

      <!-- 标签 -->
      <section class="group">
        <h3 class="group-title">标签</h3>
        <div class="item" @click="openPanel = 'colors'">
          <span class="item-label">预设颜色管理</span>
          <span class="item-value">{{ presetColors.length }} 种</span>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="item" @click="restoreColors">
          <span class="item-label">恢复默认颜色</span>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
      </section>

      <!-- 数据 -->
      <section class="group">
        <h3 class="group-title">数据</h3>
        <div class="item" @click="todo()">
          <span class="item-label">导出数据库</span>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="item" @click="todo()">
          <span class="item-label">从备份恢复</span>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="item">
          <span class="item-label">同步维护 JSON 数据文件</span>
          <el-switch v-model="settings.jsonSync" size="small" />
        </div>
      </section>

      <!-- 关于 -->
      <section class="group">
        <h3 class="group-title">关于</h3>
        <div class="item" @click="openPanel = 'about'">
          <span class="item-label">版本 / 开源许可</span>
          <span class="item-value">v0.1.0</span>
          <el-icon class="item-arrow"><ArrowRight /></el-icon>
        </div>
      </section>
    </div>

    <!-- ===== 设置面板对话框 ===== -->
    <el-dialog v-model="dialogVisible" :title="panelTitle" width="80%" align-center>
      <!-- 默认存储类型 -->
      <el-radio-group v-if="openPanel === 'storage'" v-model="settings.defaultStorage" class="panel-options">
        <el-radio value="private">应用私有目录（数据安全，卸载即删）</el-radio>
        <el-radio value="public">公共目录（方便手动备份）</el-radio>
        <el-radio value="external">外部索引（不复制文件，仅记录路径）</el-radio>
      </el-radio-group>

      <!-- 分片大小 -->
      <el-radio-group v-else-if="openPanel === 'shard'" v-model="settings.shardSize" class="panel-options">
        <el-radio :value="50">50 MB</el-radio>
        <el-radio :value="100">100 MB</el-radio>
        <el-radio :value="200">200 MB</el-radio>
        <el-radio :value="500">500 MB</el-radio>
        <el-radio :value="1024">1 GB</el-radio>
      </el-radio-group>

      <!-- 时间分组依据 -->
      <el-radio-group v-else-if="openPanel === 'groupBy'" v-model="settings.groupBy" class="panel-options">
        <el-radio value="taken">拍摄日期（优先读 EXIF）</el-radio>
        <el-radio value="import">导入日期</el-radio>
      </el-radio-group>

      <!-- 主题 -->
      <el-radio-group v-else-if="openPanel === 'theme'" v-model="themeMode" class="panel-options">
        <el-radio value="light">浅色</el-radio>
        <el-radio value="dark">深色</el-radio>
        <el-radio value="system">跟随系统</el-radio>
      </el-radio-group>

      <!-- 网格列数 -->
      <template v-else-if="openPanel === 'grid'">
        <el-slider v-model="settings.gridCols" :min="2" :max="6" show-stops />
        <div class="grid-preview" :style="gridStyle">
          <span v-for="n in settings.gridCols" :key="n" class="grid-preview-cell" />
        </div>
      </template>

      <!-- 回收站天数 -->
      <template v-else-if="openPanel === 'recycle'">
        <el-input-number v-model="settings.recycleDays" :min="0" :max="365" />
        <p class="panel-hint">0 表示删除时直接永久删除，不进回收站</p>
      </template>

      <!-- 预设颜色 -->
      <div v-else-if="openPanel === 'colors'" class="color-grid">
        <div v-for="(c, i) in presetColors" :key="i" class="color-cell">
          <el-color-picker v-model="presetColors[i]" size="small" />
          <el-button size="small" link type="danger" @click="presetColors.splice(i, 1)">
            删除
          </el-button>
        </div>
        <el-button size="small" @click="presetColors.push('#409eff')">+ 添加颜色</el-button>
      </div>

      <!-- 关于 -->
      <div v-else-if="openPanel === 'about'" class="about">
        <div class="about-head">
          <b>表情包管理器</b>
          <span class="about-version">v0.1.0</span>
        </div>
        <p class="about-desc">基于 Tauri 2 + Vue 3 的桌面版（Android 原项目移植）</p>

        <h4 class="lic-title">开源许可</h4>
        <div class="licenses">
          <div class="license-item">
            <span>本项目 (mememanager)</span>
            <span class="lic-val">MIT</span>
          </div>
          <div class="license-item">
            <span>Tauri 2（桌面框架）</span>
            <span class="lic-val">MIT / Apache-2.0</span>
          </div>
          <div class="license-item">
            <span>Vue 3（前端框架）</span>
            <span class="lic-val">MIT</span>
          </div>
          <div class="license-item">
            <span>Element Plus（UI 组件库）</span>
            <span class="lic-val">MIT</span>
          </div>
          <div class="license-item">
            <span>Vite（构建工具）</span>
            <span class="lic-val">MIT</span>
          </div>
          <div class="license-item">
            <span>Rust（后端语言）</span>
            <span class="lic-val">MIT / Apache-2.0</span>
          </div>
        </div>
        <p class="lic-foot">各依赖的完整许可文本请参阅对应项目源码仓库。</p>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.settings-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--card-bg);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.settings-top {
  height: var(--app-topbar-height);
  flex: none;
  display: flex;
  align-items: center;
  padding: 0 14px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--divider);
}

.settings-title {
  font-size: 15px;
  font-weight: 600;
}

.settings-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.group {
  margin-bottom: 16px;
}

.group-title {
  margin: 0 0 6px;
  padding-left: 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
}

.item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 14px;
  background: var(--card-bg);
  cursor: pointer;
  border-bottom: 1px solid var(--divider);
  transition: background 0.15s;
}

.item:hover {
  background: var(--hover-bg);
}

.item:first-child {
  border-radius: 10px 10px 0 0;
}

.item:last-child {
  border-bottom: none;
  border-radius: 0 0 10px 10px;
}

.item-label {
  flex: 1;
  font-size: 14px;
}

.item-value {
  font-size: 12px;
  color: var(--text-secondary);
}

.item-arrow {
  color: var(--text-muted);
  font-size: 13px;
}

.danger-text {
  color: #f56c6c;
}

.panel-options {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}

.panel-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 10px;
}

.grid-preview {
  display: grid;
  gap: 6px;
  margin-top: 16px;
}

.grid-preview-cell {
  aspect-ratio: 1;
  background: var(--input-bg);
  border-radius: 6px;
}

.color-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.color-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.about {
  font-size: 13px;
}

.about-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 15px;
}

.about-version {
  font-size: 12px;
  color: var(--text-muted);
}

.about-desc {
  margin: 6px 0 4px;
  color: var(--text-secondary);
}

.lic-title {
  margin: 14px 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
}

.licenses {
  border: 1px solid var(--divider);
  border-radius: 8px;
  overflow: hidden;
}

.license-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 12px;
  color: var(--text-main);
  border-bottom: 1px solid var(--divider);
}

.license-item:last-child {
  border-bottom: none;
}

.lic-val {
  flex: none;
  font-size: 12px;
  color: var(--accent);
  font-weight: 600;
}

.lic-foot {
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-muted);
}
</style>

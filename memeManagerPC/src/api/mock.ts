/**
 * 浏览器开发模式 mock 后端（vite dev 直接打开页面时使用，
 * Tauri WebView 内由 api/index.ts 自动切回真实 invoke）。
 * 数据全部保存在内存里，刷新即重置，用于 UI / 交互开发调试。
 */
import type { Media, Tag } from "../types";
import type {
  Api,
  ExportResult,
  ImportFilesResult,
  ImportResult,
  ScanSummary,
} from "./index";

// —— mock 图片：SVG data URL（渐变底 + emoji），无网络依赖 ——
const MOODS: [string, string, string][] = [
  ["#f6821f", "#fbbf24", "😂"],
  ["#6366f1", "#8b5cf6", "🤣"],
  ["#10b981", "#34d399", "😅"],
  ["#f43f5e", "#fb7185", "😭"],
  ["#0ea5e9", "#38bdf8", "🥰"],
  ["#8b5cf6", "#a78bfa", "😎"],
  ["#f59e0b", "#fcd34d", "🤔"],
  ["#14b8a6", "#5eead4", "🙃"],
  ["#ec4899", "#f472b6", "🥺"],
  ["#64748b", "#94a3b8", "😤"],
  ["#22c55e", "#86efac", "🤝"],
  ["#eab308", "#fde047", "🐱"],
];

function svgImage(mood: [string, string, string], size = 240): string {
  const [c1, c2, emoji] = mood;
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}">` +
    `<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">` +
    `<stop offset="0" stop-color="${c1}"/><stop offset="1" stop-color="${c2}"/>` +
    `</linearGradient></defs>` +
    `<rect width="${size}" height="${size}" fill="url(#g)"/>` +
    `<circle cx="${size * 0.5}" cy="${size * 0.42}" r="${size * 0.22}" fill="rgba(255,255,255,0.18)"/>` +
    `<text x="50%" y="52%" font-size="${size * 0.3}" text-anchor="middle" dominant-baseline="middle">${emoji}</text>` +
    `</svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

const MIN = 60_000;
const HOUR = 3_600_000;
const DAY = 86_400_000;

interface MediaSeed {
  name: string;
  type: Media["mediaType"];
  tags: number[];
  ago: number; // 距今毫秒
  desc?: string;
  source?: string;
}

const MEDIA_SEEDS: MediaSeed[] = [
  { name: "笑不活了.png", type: "image", tags: [1, 2], ago: 5 * MIN, desc: "群友的日常发疯" },
  { name: "猫猫头.gif", type: "gif", tags: [1], ago: 40 * MIN, desc: "经典猫猫" },
  { name: "我裂开了.png", type: "image", tags: [2, 4], ago: 2 * HOUR, desc: "破防专用" },
  { name: "缓缓打出一个问号.png", type: "image", tags: [4], ago: 4 * HOUR },
  { name: "阴阳怪气.gif", type: "gif", tags: [2, 3], ago: 7 * HOUR, source: "wechat" },
  { name: "猫猫震惊.jpg", type: "image", tags: [1, 5], ago: 9 * HOUR, desc: "配文：哈？" },
  { name: "乐.jpg", type: "image", tags: [2, 4], ago: 26 * HOUR },
  { name: "流泪猫猫头.png", type: "image", tags: [1, 4], ago: 27 * HOUR, desc: "哭了" },
  { name: "好好好.png", type: "image", tags: [4], ago: 30 * HOUR, source: "qq" },
  { name: "打工人.gif", type: "gif", tags: [3, 6], ago: 50 * HOUR, desc: "上班摸鱼必备" },
  { name: "干饭人干饭魂.png", type: "image", tags: [6], ago: 3 * DAY, source: "download" },
  { name: "小狗勾.jpg", type: "image", tags: [5, 6], ago: 3 * DAY + 4 * HOUR },
  { name: "摊手.gif", type: "gif", tags: [2], ago: 4 * DAY },
  { name: "确实.png", type: "image", tags: [4, 6], ago: 5 * DAY, desc: "万能回复" },
  { name: "妙啊.png", type: "image", tags: [2, 5], ago: 5 * DAY + 6 * HOUR },
  { name: "吵架没输过.png", type: "image", tags: [2, 3], ago: 6 * DAY, source: "wechat" },
  { name: "猫猫拳.mp4", type: "video", tags: [1, 5], ago: 8 * DAY, desc: "猫猫出拳视频" },
  { name: "下头男行为.mp4", type: "video", tags: [3], ago: 9 * DAY },
  { name: "抱抱.png", type: "image", tags: [5], ago: 12 * DAY },
  { name: "突然自信.gif", type: "gif", tags: [2, 4], ago: 13 * DAY, source: "qq" },
  { name: "awsl.jpg", type: "image", tags: [1, 5], ago: 15 * DAY, desc: "awsl" },
  { name: "带专人专带.png", type: "image", tags: [4], ago: 20 * DAY, source: "download" },
  { name: "偷感很重.png", type: "image", tags: [2, 3], ago: 34 * DAY, desc: "偷感" },
  { name: "精神状态良好.gif", type: "gif", tags: [3, 6], ago: 40 * DAY },
  { name: "啊对对对.png", type: "image", tags: [4], ago: 60 * DAY, source: "wechat" },
  { name: "尊嘟假嘟.jpg", type: "image", tags: [2, 5], ago: 75 * DAY },
  { name: "鼠鼠我啊.png", type: "image", tags: [3], ago: 120 * DAY, source: "download" },
  { name: "遥遥领先.gif", type: "gif", tags: [6], ago: 200 * DAY },
  { name: "城市套路深.jpg", type: "image", tags: [4, 6], ago: 400 * DAY, desc: "我想回农村" },
  { name: "退退退.png", type: "image", tags: [2, 3], ago: 420 * DAY },
  // 回收站两项
  { name: "过期梗.png", type: "image", tags: [], ago: 10 * DAY, desc: "已过气" },
  { name: "重复保存.jpg", type: "image", tags: [1], ago: 22 * DAY },
];

const TAGS: Tag[] = [
  { id: 1, name: "猫猫", bgColor: "#f59e0b", sortOrder: 1 },
  { id: 2, name: "搞笑", bgColor: "#10b981", sortOrder: 2 },
  { id: 3, name: "吐槽", bgColor: "#6366f1", sortOrder: 3 },
  { id: 4, name: "万能回复", bgColor: "#0ea5e9", sortOrder: 4 },
  { id: 5, name: "可爱", bgColor: "#ec4899", sortOrder: 5 },
  { id: 6, name: "日常", bgColor: "#8b5cf6", sortOrder: 6 },
];

const settingsMap: Record<string, string> = {
  defaultStorage: "user",
  customDir: "",
  shardSize: "100",
  groupBy: "taken",
  gridCols: "6",
  recycleDays: "30",
  jsonSync: "false",
};

// —— 内存库 ——
const now = Date.now();
let nextMediaId = 1;
let mediaList: Media[] = [];

function buildMedia(seed: MediaSeed, index: number, deleted = false): Media {
  const mood = MOODS[index % MOODS.length];
  const taken = now - seed.ago;
  const id = nextMediaId++;
  return {
    id,
    fileName: seed.name,
    // mock 用 data URL 直接当“文件路径”，utils/asset 的 fileSrc 会原样放行
    filePath: svgImage(mood),
    storageType: "user",
    mediaType: seed.type,
    mimeType: null,
    source: seed.source ?? "screenshot",
    description: seed.desc ?? "",
    takenTime: taken,
    importTime: taken - Math.floor(Math.random() * 3 * DAY),
    fileSize: 50_000 + ((index * 37) % 40) * 30_000,
    width: 240,
    height: 240,
    sha256: `mock-${id}`,
    isDeleted: deleted,
    deletedTime: deleted ? now - 2 * DAY : null,
    sortOrder: 0,
    tagIds: [...seed.tags],
  };
}

MEDIA_SEEDS.forEach((seed, i) => {
  mediaList.push(buildMedia(seed, i, i >= MEDIA_SEEDS.length - 2));
});

const delay = <T,>(v: T, ms = 120): Promise<T> =>
  new Promise((r) => setTimeout(() => r(v), ms));

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** mock 实现：用 Api 类型约束，与真实后端签名保持同步 */
export const mockApi: Api = {
  // —— 媒体 ——
  listMedia: () => delay(mediaList.filter((m) => !m.isDeleted)),
  listRecycle: () => delay(mediaList.filter((m) => m.isDeleted)),
  scanFolder: async (): Promise<ScanSummary> => {
    await sleep(600);
    const names = ["新导入的梗.png", "新导入的猫.gif", "新导入的吐槽.jpg"];
    names.forEach((name, i) =>
      mediaList.unshift(
        buildMedia({ name, type: i === 1 ? "gif" : "image", tags: [], ago: i * HOUR }, i)
      )
    );
    return { added: names.length, updated: 0, skipped: 0, removed: 0 };
  },
  setDescription: async (id: number, description: string) => {
    const m = mediaList.find((x) => x.id === id);
    if (m) m.description = description;
  },
  replaceMediaTags: async (mediaIds: number[], tagIds: number[]) => {
    mediaList
      .filter((m) => mediaIds.includes(m.id))
      .forEach((m) => (m.tagIds = [...tagIds]));
  },
  deleteMedia: async (ids: number[]) => {
    let n = 0;
    mediaList
      .filter((m) => ids.includes(m.id) && !m.isDeleted)
      .forEach((m) => {
        m.isDeleted = true;
        m.deletedTime = Date.now();
        n++;
      });
    return n;
  },
  deleteMediaOrPurge: async (ids: number[], permanent: boolean) => {
    if (permanent) {
      mediaList = mediaList.filter((m) => !ids.includes(m.id));
      return ids.length;
    }
    let n = 0;
    mediaList
      .filter((m) => ids.includes(m.id) && !m.isDeleted)
      .forEach((m) => {
        m.isDeleted = true;
        m.deletedTime = Date.now();
        n++;
      });
    return n;
  },
  restoreMedia: async (ids: number[]) => {
    let n = 0;
    mediaList
      .filter((m) => ids.includes(m.id) && m.isDeleted)
      .forEach((m) => {
        m.isDeleted = false;
        m.deletedTime = null;
        n++;
      });
    return n;
  },
  purgeMedia: async (ids: number[]) => {
    mediaList = mediaList.filter((m) => !ids.includes(m.id));
    return ids.length;
  },
  setMediaOrder: async (ids: number[]) => {
    const byId = new Map(mediaList.map((m) => [m.id, m]));
    ids.forEach((id, i) => {
      const m = byId.get(id);
      if (m) m.sortOrder = i + 1;
    });
    mediaList.sort((a, b) => (a.sortOrder || 1e9) - (b.sortOrder || 1e9));
  },

  // —— 标签 ——
  listTags: () => delay(TAGS),
  addTag: async (name: string, bgColor: string): Promise<Tag> => {
    const t: Tag = {
      id: Math.max(0, ...TAGS.map((x) => x.id)) + 1,
      name,
      bgColor,
      sortOrder: TAGS.length + 1,
    };
    TAGS.push(t);
    return t;
  },
  renameTag: async (id: number, name: string) => {
    const t = TAGS.find((x) => x.id === id);
    if (t) t.name = name;
  },
  setTagColor: async (id: number, color: string) => {
    const t = TAGS.find((x) => x.id === id);
    if (t) t.bgColor = color;
  },
  deleteTag: async (id: number) => {
    const i = TAGS.findIndex((x) => x.id === id);
    if (i >= 0) TAGS.splice(i, 1);
    mediaList.forEach((m) => (m.tagIds = m.tagIds.filter((t) => t !== id)));
  },
  setTagOrder: async (ids: number[]) => {
    const byId = new Map(TAGS.map((t) => [t.id, t]));
    ids.forEach((id, i) => {
      const t = byId.get(id);
      if (t) t.sortOrder = i + 1;
    });
    TAGS.sort((a, b) => a.sortOrder - b.sortOrder);
  },

  // —— 设置 ——
  getSettings: async () => ({ ...settingsMap }),
  setSetting: async (key: string, value: string) => {
    settingsMap[key] = value;
  },
  defaultMediaDir: async () => "D:/MockMedia",

  // —— .mpak ——
  exportPak: async (items: unknown[]): Promise<ExportResult> => {
    await sleep(900);
    return { shards: ["D:/MockMedia/meme-001.mpak"], totalMedia: items.length };
  },
  cancelExport: async () => {},
  importPak: async (): Promise<ImportResult> => {
    await sleep(900);
    return { succeeded: 3, skipped: 0, failed: 0, failedNames: [] };
  },

  // —— 系统对话框（浏览器里返回假路径，便于走通交互流程）——
  pickDirectory: async () => "D:/MockMedia/导入测试",
  pickMpakFile: async () => "D:/MockMedia/backup.mpak",
  pickImageFiles: async () => ["D:/MockMedia/a.png", "D:/MockMedia/b.gif"],
  pickAnyFiles: async () => ["D:/MockMedia/c.png"],
  importFiles: async (paths: string[]): Promise<ImportFilesResult> => {
    await sleep(500);
    paths.forEach((p, i) => {
      const name = p.split(/[\\/]/).pop() ?? `文件${i}.png`;
      mediaList.unshift(
        buildMedia(
          {
            name,
            type: name.endsWith(".gif") ? "gif" : "image",
            tags: [],
            ago: MIN * (i + 1),
            source: "custom",
          },
          i + 3
        )
      );
    });
    return {
      copied: paths.length,
      scan: { added: paths.length, updated: 0, skipped: 0, removed: 0 },
    };
  },
  copyToClipboard: async () => "已复制到剪贴板（mock）",
};

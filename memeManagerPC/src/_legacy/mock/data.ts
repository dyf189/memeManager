import type { Media, SourceType, Tag } from "../types";

// ===== mock 数据：UI 阶段占位，后续由 Rust 后端真实数据替换 =====

export const mockTags: Tag[] = [
  { id: 1, name: "😂开心", bgColor: "#f56c6c", sortOrder: 1 },
  { id: 2, name: "🔥爆笑", bgColor: "#e6a23c", sortOrder: 2 },
  { id: 3, name: "🐱猫猫", bgColor: "#67c23a", sortOrder: 3 },
  { id: 4, name: "😭破防", bgColor: "#409eff", sortOrder: 4 },
  { id: 5, name: "🖥️工作", bgColor: "#909399", sortOrder: 5 },
  { id: 6, name: "[已导出]", bgColor: "#9c27b0", sortOrder: 99, isReserved: true },
];

const EMOJIS = [
  "🐱", "🐶", "😂", "🔥", "🐸", "🐷", "😭", "😡",
  "🍉", "🍺", "💻", "🎮", "🐔", "🐢", "🎉", "🤡",
];

function pick<T>(arr: T[], i: number): T {
  return arr[i % arr.length];
}

const now = Date.now();
const MIN = 60_000;
const HOUR = 3_600_000;
const DAY = 86_400_000;

// 造一条媒体记录：id 顺序与时间对应，方便预览
function makeMedia(
  id: number,
  opts: {
    type: Media["type"];
    minutesAgo: number; // 距现在的分钟数
    tags?: number[];
    source?: SourceType;
    description?: string;
    sizeMB?: number;
  }
): Media {
  const taken = now - opts.minutesAgo * MIN;
  const isGif = opts.type === "gif";
  const isVideo = opts.type === "video";
  return {
    id,
    fileName: `${isGif ? "gif" : isVideo ? "video" : "img"}_${String(id).padStart(3, "0")}.${isGif ? "gif" : isVideo ? "mp4" : "png"}`,
    filePath: `/mock/${String(id).padStart(3, "0")}.${isGif ? "gif" : isVideo ? "mp4" : "png"}`,
    storageType: "public",
    type: opts.type,
    mimeType: isGif ? "image/gif" : isVideo ? "video/mp4" : "image/png",
    source: opts.source ?? "wechat",
    description: opts.description ?? "",
    takenTime: taken,
    importTime: taken + 5 * MIN,
    fileSize: Math.round((opts.sizeMB ?? 1.5) * 1024 * 1024),
    width: isVideo ? 720 : 400 + ((id * 37) % 500),
    height: isVideo ? 1280 : 400 + ((id * 53) % 500),
    isDeleted: false,
    tagIds: opts.tags ?? [],
    thumbFrom: `hsl(${(id * 47) % 360} 70% 70%)`,
    thumbTo: `hsl(${(id * 47 + 60) % 360} 70% 45%)`,
    emoji: pick(EMOJIS, id),
  };
}

export const mockMedia: Media[] = [
  // —— 刚刚 ——
  makeMedia(1, { type: "image", minutesAgo: 0.5, tags: [1, 2], description: "刚收到的猫猫表情包", source: "wechat" }),
  // —— 几分钟前 ——
  makeMedia(2, { type: "gif", minutesAgo: 6, tags: [2], source: "qq" }),
  makeMedia(3, { type: "image", minutesAgo: 20, tags: [3], source: "wechat", description: "同事发的梗图，太真实了" }),
  // —— 今天（几小时前）——
  makeMedia(4, { type: "image", minutesAgo: 3 * 60, tags: [1, 3, 4], source: "screenshot", description: "摸鱼时刻" }),
  makeMedia(5, { type: "video", minutesAgo: 5 * 60, tags: [2], source: "download" }),
  makeMedia(6, { type: "gif", minutesAgo: 7 * 60, tags: [5], source: "download", description: "工作周报专用" }),
  // —— 昨天 ——
  makeMedia(7, { type: "image", minutesAgo: 26 * 60, tags: [3], source: "wechat" }),
  makeMedia(8, { type: "image", minutesAgo: 28 * 60, tags: [1, 4], source: "camera", description: "自拍打卡" }),
  makeMedia(9, { type: "gif", minutesAgo: 30 * 60, tags: [2, 3], source: "qq" }),
  // —— 前天 ——
  makeMedia(10, { type: "image", minutesAgo: 50 * 60, tags: [5], source: "screenshot", description: "需求评审截图" }),
  makeMedia(11, { type: "image", minutesAgo: 52 * 60, tags: [], source: "download" }),
  // —— 三天前 ——
  makeMedia(12, { type: "video", minutesAgo: 74 * 60, tags: [2], source: "download" }),
  makeMedia(13, { type: "image", minutesAgo: 75 * 60, tags: [3, 4], source: "wechat" }),
  // —— 4 天前 ~ 今年内（MM-dd）——
  makeMedia(14, { type: "image", minutesAgo: 5 * 24 * 60, tags: [1], source: "wechat", description: "周末聚会合影" }),
  makeMedia(15, { type: "gif", minutesAgo: 8 * 24 * 60, tags: [2, 6], source: "download", description: "已导出过的梗图" }),
  makeMedia(16, { type: "image", minutesAgo: 12 * 24 * 60, tags: [3], source: "camera" }),
  makeMedia(17, { type: "image", minutesAgo: 20 * 24 * 60, tags: [], source: "screenshot" }),
  makeMedia(18, { type: "video", minutesAgo: 25 * 24 * 60, tags: [5], source: "download", description: "教程视频片段" }),
  makeMedia(19, { type: "gif", minutesAgo: 30 * 24 * 60, tags: [2, 6], source: "qq" }),
  makeMedia(20, { type: "image", minutesAgo: 45 * 24 * 60, tags: [1, 2, 3, 4, 5], source: "wechat", description: "标签很多的一条" }),
  makeMedia(21, { type: "image", minutesAgo: 60 * 24 * 60, tags: [3], source: "download" }),
  makeMedia(22, { type: "image", minutesAgo: 90 * 24 * 60, tags: [], source: "screenshot" }),
  makeMedia(23, { type: "gif", minutesAgo: 120 * 24 * 60, tags: [2], source: "wechat" }),
  makeMedia(24, { type: "image", minutesAgo: 150 * 24 * 60, tags: [4], source: "camera" }),
  // —— 去年及更早（yyyy-MM-dd）——
  makeMedia(25, { type: "image", minutesAgo: 400 * 24 * 60, tags: [1, 3], source: "wechat", description: "去年收藏的老图" }),
  makeMedia(26, { type: "gif", minutesAgo: 420 * 24 * 60, tags: [6], source: "download" }),
  makeMedia(27, { type: "image", minutesAgo: 600 * 24 * 60, tags: [], source: "camera" }),
  makeMedia(28, { type: "video", minutesAgo: 800 * 24 * 60, tags: [2, 5], source: "download" }),
];

/** 来源标签的中文名 */
export const SOURCE_LABELS: Record<SourceType, string> = {
  camera: "相机",
  screenshot: "截图",
  wechat: "微信",
  qq: "QQ",
  download: "下载",
  custom: "自定义",
};

import { convertFileSrc } from "@tauri-apps/api/core";
import { startDrag } from "@crabnebula/tauri-plugin-drag";
import { ElMessage } from "element-plus";
import type { Media } from "../types";

/**
 * 系统级拖拽（拖出到桌面/文件管理器/其它应用）：
 * 基于 tauri-plugin-drag 的 startDrag，在 HTML dragstart 中调用。
 *
 * 关键：startDrag 必须在 dragstart 事件内【同步】调用——
 * HTML5 拖拽序列在事件返回后即结束，异步调用会导致拖拽无反应。
 */

const iconCache = new Map<string, string>();

function iconKey(m: Media): string {
  return `${m.id}:${m.sha256 ?? m.filePath}`;
}

/** 纯色占位图标（同步生成，视频/图片未就绪/加载失败时用） */
function solidIcon(media: Media, size = 128): string {
  const canvas = document.createElement("canvas");
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext("2d");
  if (ctx) {
    ctx.fillStyle = "#409eff";
    ctx.fillRect(0, 0, size, size);
    ctx.fillStyle = "#ffffff";
    ctx.font = `${Math.floor(size / 3)}px sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(media.mediaType === "video" ? "🎬" : "🖼️", size / 2, size / 2);
  }
  return canvas.toDataURL("image/png");
}

/** 异步生成真实缩略图图标并缓存（供后续拖拽使用） */
export function prewarmIcon(media: Media, size = 128): void {
  const key = iconKey(media);
  if (iconCache.has(key)) return;
  if (media.mediaType === "video") {
    iconCache.set(key, solidIcon(media, size));
    return;
  }
  const img = new Image();
  img.onload = () => {
    const canvas = document.createElement("canvas");
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    const scale = Math.max(
      size / (img.naturalWidth || 1),
      size / (img.naturalHeight || 1)
    );
    const w = (img.naturalWidth || 1) * scale;
    const h = (img.naturalHeight || 1) * scale;
    ctx.drawImage(img, (size - w) / 2, (size - h) / 2, w, h);
    iconCache.set(key, canvas.toDataURL("image/png"));
  };
  img.onerror = () => iconCache.set(key, solidIcon(media, size));
  img.src = convertFileSrc(media.filePath);
}

/** 批量预热（列表加载后调用，限制数量避免一次性加载过多） */
export function prewarmIcons(mediaList: Media[], limit = 200): void {
  mediaList.slice(0, limit).forEach(prewarmIcon);
}

/**
 * 从 dragstart 事件发起系统级拖拽（同步）。
 * 必须调用 e.preventDefault() 阻止 WebView 内部 HTML5 拖拽。
 */
export function dragMediaOut(e: DragEvent, media: Media): void {
  e.preventDefault();

  // 同步获取图标：缓存命中用真实缩略图，未命中先用占位（保证不阻塞拖拽）
  const icon = iconCache.get(iconKey(media)) ?? solidIcon(media);
  // 后台预热真实图标，供下一次拖拽使用
  prewarmIcon(media);

  startDrag({
    item: [media.filePath],
    icon,
    mode: "copy",
  }).catch((err) => {
    console.warn("[drag] 拖拽失败:", media.filePath, err);
    ElMessage.error(`拖拽失败：${err}`);
  });
}

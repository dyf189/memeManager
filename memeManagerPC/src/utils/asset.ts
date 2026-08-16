import { convertFileSrc } from "@tauri-apps/api/core";

/**
 * 媒体文件路径 → 可显示 URL。
 * mock 数据（data:/http(s):/blob:）原样返回；真实本地路径走 Tauri asset 协议。
 */
export function fileSrc(path: string): string {
  if (/^(data:|https?:|blob:)/i.test(path)) return path;
  return convertFileSrc(path);
}

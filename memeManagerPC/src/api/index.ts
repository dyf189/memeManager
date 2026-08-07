import { invoke } from "@tauri-apps/api/core";
import { open } from "@tauri-apps/plugin-dialog";
import type { Media, Tag } from "../types";

/** 导入导出摘要类型（对应 Rust 端返回结构） */
export interface ScanSummary {
  added: number;
  updated: number;
  skipped: number;
  removed: number;
}
export interface ImportResult {
  succeeded: number;
  skipped: number;
  failed: number;
  failedNames: string[];
}
export interface ExportResult {
  shards: string[];
  totalMedia: number;
}

/**
 * API 层：统一封装 Rust 后端 tauri command。
 * Tauri 会自动把 JS 的 camelCase 参数名映射为 Rust 的 snake_case 参数名。
 */
export const api = {
  // —— 媒体 ——
  listMedia: () => invoke<Media[]>("list_media"),
  listRecycle: () => invoke<Media[]>("list_recycle"),
  scanFolder: (dir: string, recursive: boolean) =>
    invoke<ScanSummary>("scan_folder", { dir, recursive }),
  setDescription: (id: number, description: string) =>
    invoke<void>("set_description", { id, description }),
  replaceMediaTags: (mediaIds: number[], tagIds: number[]) =>
    invoke<void>("replace_media_tags", { mediaIds, tagIds }),
  deleteMedia: (ids: number[]) => invoke<number>("delete_media", { ids }),
  restoreMedia: (ids: number[]) => invoke<number>("restore_media", { ids }),
  purgeMedia: (ids: number[]) => invoke<number>("purge_media", { ids }),

  // —— 标签 ——
  listTags: () => invoke<Tag[]>("list_tags"),
  addTag: (name: string, bgColor: string) =>
    invoke<Tag>("add_tag", { name, bgColor }),
  renameTag: (id: number, name: string) =>
    invoke<void>("rename_tag", { id, name }),
  setTagColor: (id: number, color: string) =>
    invoke<void>("set_tag_color", { id, color }),
  deleteTag: (id: number) => invoke<void>("delete_tag", { id }),
  moveTag: (id: number, dir: number) => invoke<void>("move_tag", { id, dir }),
  setTagOrder: (ids: number[]) => invoke<void>("set_tag_order", { ids }),

  // —— 设置 ——
  getSettings: () => invoke<Record<string, string>>("get_settings"),
  setSetting: (key: string, value: string) =>
    invoke<void>("set_setting", { key, value }),
  /** 程序默认媒体目录（应用数据目录/media） */
  defaultMediaDir: () => invoke<string>("default_media_dir"),

  // —— .mpak 导入导出 ——
  exportPak: (items: unknown[], maxSize: number, destDir: string) =>
    invoke<ExportResult>("export_pak", { items, maxSize, destDir }),
  importPak: (path: string, destDir: string) =>
    invoke<ImportResult>("import_pak", { path, destDir }),

  // —— 系统对话框 ——
  /** 选择文件夹，返回路径或 null（取消） */
  pickDirectory: async (): Promise<string | null> => {
    const picked = await open({ directory: true, multiple: false });
    return typeof picked === "string" ? picked : null;
  },
  /** 选择 .mpak 文件，返回路径或 null（取消） */
  pickMpakFile: async (): Promise<string | null> => {
    const picked = await open({
      multiple: false,
      filters: [{ name: "Meme Package (.mpak)", extensions: ["mpak"] }],
    });
    return typeof picked === "string" ? picked : null;
  },
  /** 选择图片/视频文件（可多选） */
  pickImageFiles: async (): Promise<string[]> => {
    const picked = await open({
      multiple: true,
      filters: [
        {
          name: "图片 / 视频",
          extensions: [
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "tif", "tiff", "avif", "heic", "heif",
            "mp4", "webm", "mov", "mkv", "avi", "mpg", "mpeg", "ogv", "3gp", "flv",
          ],
        },
      ],
    });
    return Array.isArray(picked) ? picked : picked ? [picked] : [];
  },
  /** 选择任意文件（可多选） */
  pickAnyFiles: async (): Promise<string[]> => {
    const picked = await open({ multiple: true });
    return Array.isArray(picked) ? picked : picked ? [picked] : [];
  },
  /** 导入用户选择的文件到目标目录并入库 */
  importFiles: (paths: string[], destDir: string) =>
    invoke<ScanSummary>("import_files", { paths, destDir }),
  /** 复制文件到系统剪贴板（图片复制图像，视频复制路径） */
  copyToClipboard: (path: string) =>
    invoke<string>("copy_to_clipboard", { path }),
};

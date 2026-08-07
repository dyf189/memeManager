// ===== 领域类型定义（与 Rust 后端 media.rs 返回结构对齐，serde camelCase）=====

export type MediaType = "image" | "gif" | "video";

/** 存储类型：user=用户目录 | app=安装目录 | custom=自定义目录 */
export type StorageType = "user" | "app" | "custom";

export type SourceType =
  | "camera"
  | "screenshot"
  | "wechat"
  | "qq"
  | "download"
  | "custom";

export interface Tag {
  id: number;
  name: string;
  bgColor: string;
  /** 保留标签（如系统内置）：不可删除、不可重名 */
  isReserved?: boolean;
  sortOrder: number;
}

export interface Media {
  id: number;
  fileName: string;
  filePath: string;
  storageType: StorageType;
  mediaType: MediaType;
  mimeType: string | null;
  source: string;
  description: string;
  /** 拍摄/分组依据时间（ms） */
  takenTime: number;
  /** 导入时间（ms） */
  importTime: number;
  fileSize: number; // 字节
  width: number | null;
  height: number | null;
  /** 媒体内容 SHA-256（hex 小写），用于导出去重 */
  sha256: string | null;
  isDeleted: boolean;
  deletedTime: number | null;
  tagIds: number[];
}

/** 可叠加的筛选条件（goals.md 第 7 节） */
export interface FilterState {
  type: MediaType | "all";
  source: SourceType | "all";
  /** 按来源目录筛选（filePath 的父目录），"all" = 全部 */
  dir: string;
  hasDescription: "all" | "yes" | "no";
  /** 多选标签，AND 逻辑 */
  tagIds: number[];
  timeRange: [number, number] | null;
  sizeRange: [number, number] | null; // 字节
}

export function emptyFilter(): FilterState {
  return {
    type: "all",
    source: "all",
    dir: "all",
    hasDescription: "all",
    tagIds: [],
    timeRange: null,
    sizeRange: null,
  };
}

export type ViewMode = "grid" | "list";

/** 时间分组结果 */
export interface TimeGroup {
  label: string;
  items: Media[];
}

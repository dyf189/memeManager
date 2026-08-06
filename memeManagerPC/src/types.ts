// ===== 领域类型定义（与 goals.md 的实体对应，后续由 Rust 后端返回）=====

export type MediaType = "image" | "gif" | "video";

/** 存储类型：private=应用私有目录，public=公共目录，external=外部索引（仅记录路径） */
export type StorageType = "private" | "public" | "external";

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
  /** [已导出] 等保留标签：不可删除、不可重名 */
  isReserved?: boolean;
  sortOrder: number;
}

export interface Media {
  id: number;
  fileName: string;
  filePath: string;
  storageType: StorageType;
  type: MediaType;
  mimeType: string;
  source: SourceType;
  description: string;
  /** 拍摄/分组依据时间（ms） */
  takenTime: number;
  /** 导入时间（ms） */
  importTime: number;
  fileSize: number; // 字节
  width: number;
  height: number;
  isDeleted: boolean;
  deletedTime?: number;
  tagIds: number[];
  /** —— 以下为 UI 占位用，接入真实数据后删除 —— */
  thumbFrom: string; // 渐变起始色
  thumbTo: string; // 渐变结束色
  emoji: string; // 占位表情符号
}

/** 可叠加的筛选条件（goals.md 第 7 节） */
export interface FilterState {
  type: MediaType | "all";
  source: SourceType | "all";
  hasDescription: "all" | "yes" | "no";
  exported: "all" | "exported" | "not";
  /** 多选标签，AND 逻辑 */
  tagIds: number[];
  timeRange: [number, number] | null;
  sizeRange: [number, number] | null; // 字节
}

export function emptyFilter(): FilterState {
  return {
    type: "all",
    source: "all",
    hasDescription: "all",
    exported: "all",
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

import type { Media, Tag } from "../types";
import { mockMedia, mockTags } from "../mock/data";

/**
 * API 层：后续替换为 Tauri invoke 调用 Rust 后端命令。
 * 目前所有方法返回 mock 数据，前端无需改动即可平滑切换。
 */
export const api = {
  async listMedia(): Promise<Media[]> {
    return mockMedia;
  },
  async listTags(): Promise<Tag[]> {
    return mockTags;
  },
  async addTag(tag: Omit<Tag, "id" | "sortOrder">): Promise<Tag> {
    return { ...tag, id: Date.now(), sortOrder: 999 };
  },
};

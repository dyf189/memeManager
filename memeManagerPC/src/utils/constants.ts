import type { SourceType } from "../types";

/** 来源标签的中文名 */
export const SOURCE_LABELS: Record<SourceType, string> = {
  camera: "相机",
  screenshot: "截图",
  wechat: "微信",
  qq: "QQ",
  download: "下载",
  custom: "自定义",
};

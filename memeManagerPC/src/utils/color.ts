/** 根据背景色亮度自动选择黑/白文字色（goals.md 标签系统） */
export function textColorFor(bg: string): string {
  const hex = bg.replace("#", "");
  if (hex.length < 6) return "#ffffff";
  const r = parseInt(hex.slice(0, 2), 16);
  const g = parseInt(hex.slice(2, 4), 16);
  const b = parseInt(hex.slice(4, 6), 16);
  // 感知亮度
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.6 ? "#1f2329" : "#ffffff";
}

/** 标签预设色（新建标签时循环分配） */
export const PRESET_TAG_COLORS = [
  "#f56c6c",
  "#e6a23c",
  "#67c23a",
  "#409eff",
  "#909399",
  "#9c27b0",
  "#00bcd4",
  "#ff9800",
  "#8bc34a",
  "#e91e63",
  "#3f51b5",
  "#795548",
];

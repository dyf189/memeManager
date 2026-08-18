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

export interface Rgb {
  r: number;
  g: number;
  b: number;
}

/** 解析 #rgb / #rrggbb 为 RGB，非法输入返回 null */
export function hexToRgb(hex: string): Rgb | null {
  let h = hex.trim().replace("#", "");
  if (h.length === 3) h = h.split("").map((c) => c + c).join("");
  if (!/^[0-9a-fA-F]{6}$/.test(h)) return null;
  return {
    r: parseInt(h.slice(0, 2), 16),
    g: parseInt(h.slice(2, 4), 16),
    b: parseInt(h.slice(4, 6), 16),
  };
}

/** Rgb → #rrggbb（规范化输出） */
export function rgbToHex({ r, g, b }: Rgb): string {
  const to = (n: number) =>
    Math.round(Math.min(255, Math.max(0, n))).toString(16).padStart(2, "0");
  return `#${to(r)}${to(g)}${to(b)}`;
}

/** 两个十六进制色按比例 t（0~1）混合，t=1 时完全为目标色 */
export function mixHex(from: string, to: string, t: number): string | null {
  const a = hexToRgb(from);
  const b = hexToRgb(to);
  if (!a || !b) return null;
  return rgbToHex({
    r: a.r + (b.r - a.r) * t,
    g: a.g + (b.g - a.g) * t,
    b: a.b + (b.b - a.b) * t,
  });
}

/** 十六进制色 → rgba() 字符串（派生半透明强调色用） */
export function alphaHex(hex: string, alpha: number): string | null {
  const c = hexToRgb(hex);
  if (!c) return null;
  return `rgba(${c.r}, ${c.g}, ${c.b}, ${alpha})`;
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

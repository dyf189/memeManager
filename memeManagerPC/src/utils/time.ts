import type { Media, TimeGroup } from "../types";

const MINUTE = 60_000;
const HOUR = 3_600_000;
const DAY = 86_400_000;

function dayStart(ts: number): number {
  const d = new Date(ts);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

function isSameYear(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear();
}

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

/**
 * goals.md 时间分组规则：
 *  刚刚(<1min)、X分钟前、X小时前（自然日内）、昨天、前天、三天前、
 *  4天前~今年内 MM-dd、去年及更早 yyyy-MM-dd
 */
export function groupLabel(ts: number, now: number = Date.now()): string {
  const d = new Date(ts);
  const diff = now - ts;

  if (diff < MINUTE) return "刚刚";
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)}分钟前`;

  const today = dayStart(now);
  const dayDiff = Math.round((today - dayStart(ts)) / DAY);

  if (dayDiff <= 0) return `${Math.floor(diff / HOUR)}小时前`;
  if (dayDiff === 1) return "昨天";
  if (dayDiff === 2) return "前天";
  if (dayDiff === 3) return "三天前";

  const n = new Date(now);
  if (isSameYear(d, n)) return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** 按时间降序分组（分组头顺序 = 导出顺序） */
export function groupByTime(items: Media[], now: number = Date.now()): TimeGroup[] {
  const groups: TimeGroup[] = [];
  for (const m of [...items].sort((a, b) => b.takenTime - a.takenTime)) {
    const label = groupLabel(m.takenTime, now);
    const last = groups[groups.length - 1];
    if (last && last.label === label) last.items.push(m);
    else groups.push({ label, items: [m] });
  }
  return groups;
}

/** 分组 key：用于 sticky header 对比（相邻同组会自然合并） */
export function groupKey(ts: number, now: number = Date.now()): string {
  return groupLabel(ts, now);
}

/** 搜索匹配区间（不区分大小写） */
export interface MatchRange {
  start: number;
  end: number; // 开区间
}

export function findMatches(text: string, query: string): MatchRange[] {
  if (!query) return [];
  const lowerText = text.toLowerCase();
  const lowerQuery = query.toLowerCase();
  const ranges: MatchRange[] = [];
  let idx = 0;
  while (idx <= lowerText.length - lowerQuery.length) {
    const found = lowerText.indexOf(lowerQuery, idx);
    if (found === -1) break;
    ranges.push({ start: found, end: found + lowerQuery.length });
    idx = found + lowerQuery.length;
  }
  return ranges;
}

/**
 * 把文本按匹配区间拆分成片段，供模板渲染高亮。
 * 截断逻辑：目前使用 CSS ellipsis / line-clamp（智能截断留待后续迭代，
 * 见 goals.md「搜索结果展示与高亮截断算法」）。
 */
export function splitByMatches(
  text: string,
  query: string
): { text: string; matched: boolean }[] {
  const ranges = findMatches(text, query);
  if (ranges.length === 0) return [{ text, matched: false }];

  const parts: { text: string; matched: boolean }[] = [];
  let cursor = 0;
  for (const r of ranges) {
    if (r.start > cursor) parts.push({ text: text.slice(cursor, r.start), matched: false });
    parts.push({ text: text.slice(r.start, r.end), matched: true });
    cursor = r.end;
  }
  if (cursor < text.length) parts.push({ text: text.slice(cursor), matched: false });
  return parts;
}

//! .mpak 导入：魔数/版本/哈希校验 → 解析 → 逐条三层校验 → 提取落位 → 去重

use std::fs;
use std::path::{Path, PathBuf};

use super::{read_u16, read_u32, read_u64, sha256_hex, FORMAT_ID, HASH_LEN, HEADER_LEN, MAGIC, VERSION, Metadata};

/// 成功导入的媒体条目（用于落库时恢复元数据）
#[derive(serde::Serialize, Debug)]
#[serde(rename_all = "camelCase")]
pub struct ImportedItem {
    /// 落位后的完整路径（与库中 file_path 对应）
    pub file_path: String,
    pub name: String,
    pub description: Option<String>,
    pub created_at: i64,
    pub tags: Vec<String>,
    pub width: Option<u32>,
    pub height: Option<u32>,
}

#[derive(serde::Serialize, Default, Debug)]
#[serde(rename_all = "camelCase")]
pub struct ImportResult {
    pub succeeded: usize,
    pub skipped: usize,
    pub failed: usize,
    pub failed_names: Vec<String>,
    /// 成功导入的媒体元数据（供导入端入库合并）
    pub items: Vec<ImportedItem>,
}

/// Windows 禁止的保留名（含扩展名也不允许）
const RESERVED_NAMES: [&str; 22] = [
    "CON", "PRN", "AUX", "NUL",
    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
];

/// Windows 禁止的字符（规范 5.3）
fn is_invalid_char(c: char) -> bool {
    matches!(c, '\\' | '/' | ':' | '*' | '?' | '"' | '<' | '>' | '|')
}

/// 导入端文件名清洗（规范 5.3）：
/// 非法字符→`_`；保留名→前缀 `_`；去末尾空格/点；超长截断；空名兜底
pub fn sanitize_filename(raw: &str) -> String {
    let trimmed = raw.trim();
    let (stem, ext) = match trimmed.rsplit_once('.') {
        // 扩展名必须非空才算真正的扩展名；否则整段当 stem（如 "abc." / "...."）
        Some((s, e)) if !s.is_empty() && !e.is_empty() => (s, format!(".{}", e)),
        _ => (trimmed, String::new()),
    };

    let mut stem: String = stem
        .chars()
        .map(|c| if is_invalid_char(c) { '_' } else { c })
        // 丢弃控制字符（如 \x00、换行），避免写入异常文件名
        .filter(|c| !c.is_control())
        .collect();

    // 去掉末尾空格/点
    while stem.ends_with(' ') || stem.ends_with('.') {
        stem.pop();
    }

    // Windows 保留名前缀 `_`
    if RESERVED_NAMES.contains(&stem.to_uppercase().as_str()) {
        stem = format!("_{}", stem);
    }

    // 超长截断（保底 200 字符 + 扩展名）
    if stem.chars().count() > 200 {
        stem = stem.chars().take(200).collect();
    }

    if stem.is_empty() {
        return format!("meme{}", ext);
    }
    format!("{}{}", stem, ext)
}

/// 目标文件名冲突处理：同名加 `_1`、`_2`…（在清洗名上叠加，保留扩展名）
pub(crate) fn resolve_target(dest_dir: &str, raw_name: &str) -> PathBuf {
    let cleaned = sanitize_filename(raw_name);
    let base = PathBuf::from(dest_dir).join(&cleaned);
    let mut candidate = base;
    let mut n = 1;
    while candidate.exists() {
        let stem = Path::new(&cleaned)
            .file_stem()
            .map(|s| s.to_string_lossy().into_owned())
            .unwrap_or_default();
        let ext = Path::new(&cleaned)
            .extension()
            .map(|e| format!(".{}", e.to_string_lossy()))
            .unwrap_or_default();
        candidate = PathBuf::from(dest_dir).join(format!("{}_{}{}", stem, n, ext));
        n += 1;
    }
    candidate
}

/// 目标目录已存在文件清单（仅收集 size 与路径，哈希在 size 匹配时才计算）
fn scan_dest(dest_dir: &str) -> Vec<(u64, PathBuf)> {
    let mut list = Vec::new();
    if let Ok(rd) = fs::read_dir(dest_dir) {
        for entry in rd.flatten() {
            if let Ok(meta) = entry.metadata() {
                if meta.is_file() {
                    list.push((meta.len(), entry.path()));
                }
            }
        }
    }
    list
}

/// 去重判断：目标目录中已有「大小 + 内容哈希」完全一致的媒体（规范 4.4）
fn already_exists(existing: &[(u64, PathBuf)], size: u64, sha: &str) -> bool {
    existing
        .iter()
        .filter(|(s, _)| *s == size)
        .any(|(_, p)| fs::read(p).map(|b| sha256_hex(&b) == sha).unwrap_or(false))
}

/// 导入入口
///
/// - `path`: .mpak 文件路径
/// - `dest_dir`: 媒体落位目录（不存在则创建）
pub fn import_pak(path: &str, dest_dir: &str) -> Result<ImportResult, String> {
    let data = fs::read(path).map_err(|e| format!("读取文件失败「{}」: {}", path, e))?;
    if data.len() < HEADER_LEN + HASH_LEN {
        return Err("文件过小，不是有效的 .mpak 文件".into());
    }

    // —— 4.1 校验 ——
    if data[0..4] != MAGIC {
        return Err("文件头魔数不匹配，不是 .mpak 文件".into());
    }
    let version = read_u16(&data, 4).unwrap_or(0);
    if version != VERSION {
        return Err(format!("不支持的格式版本: {}（当前支持 v{}）", version, VERSION));
    }

    // 尾部哈希校验：除末尾 32 字节外全部内容
    let (body, tail) = data.split_at(data.len() - HASH_LEN);
    let original_hash = hex::encode(tail);
    let calculated_hash = sha256_hex(body);
    if original_hash != calculated_hash {
        return Err("哈希校验失败，文件已损坏".into());
    }

    // —— 4.2 解析 ——
    let media_count = read_u32(body, 6).unwrap_or(0) as usize;
    let json_len = read_u32(body, 10).unwrap_or(0) as usize;
    if HEADER_LEN + json_len > body.len() {
        return Err("元数据长度越界，文件损坏".into());
    }
    let metadata: Metadata = serde_json::from_slice(&body[HEADER_LEN..HEADER_LEN + json_len])
        .map_err(|e| format!("元数据 JSON 解析失败: {}", e))?;
    if metadata.format != FORMAT_ID {
        return Err(format!("格式标识不匹配: {}", metadata.format));
    }
    if metadata.version != VERSION {
        return Err(format!("JSON 版本不匹配: {}", metadata.version));
    }
    if metadata.media.len() != media_count {
        return Err(format!(
            "媒体数量不一致（头部 {}，JSON {}），文件损坏",
            media_count,
            metadata.media.len()
        ));
    }

    fs::create_dir_all(dest_dir).map_err(|e| format!("创建目录失败「{}」: {}", dest_dir, e))?;

    let mut result = ImportResult::default();
    let mut existing = scan_dest(dest_dir);
    let mut offset = HEADER_LEN + json_len;

    for entry in &metadata.media {
        // —— 定界安全：读文件名长度 ——
        let Some(name_len_u) = read_u16(body, offset) else {
            result.failed += 1;
            result.failed_names.push(entry.name.clone());
            break; // 文件截断，后面不可能再有完整记录
        };
        offset += 2;
        let name_len = name_len_u as usize;
        let Some(name_bytes) = body.get(offset..offset + name_len) else {
            result.failed += 1;
            result.failed_names.push(entry.name.clone());
            break;
        };
        offset += name_len;
        let bin_name = String::from_utf8_lossy(name_bytes).into_owned();

        // —— 定界安全：读内容长度 ——
        let Some(content_len) = read_u64(body, offset) else {
            result.failed += 1;
            result.failed_names.push(entry.name.clone());
            break;
        };
        offset += 8;
        let remaining = body.len() - offset;
        if content_len as usize > remaining {
            // 防恶意/损坏长度值导致 OOM：声明长度超出剩余字节 → 损坏
            result.failed += 1;
            result.failed_names.push(entry.name.clone());
            break;
        }
        let content = &body[offset..offset + content_len as usize];
        offset += content_len as usize;

        // —— 文件名交叉验证（防错位）——
        if bin_name != entry.file_name {
            result.failed += 1;
            result.failed_names.push(entry.name.clone());
            continue;
        }
        // —— size 交叉验证 ——
        if content_len != entry.size {
            result.failed += 1;
            result.failed_names.push(entry.name.clone());
            continue;
        }
        // —— 内容哈希校验（兼导出端到导入端完整性）——
        let content_sha = sha256_hex(content);
        if content_sha != entry.sha256 {
            result.failed += 1;
            result.failed_names.push(entry.name.clone());
            continue;
        }

        // —— 4.4 去重：大小 + 内容哈希 ——
        if already_exists(&existing, content_len, &content_sha) {
            result.skipped += 1;
            continue;
        }

        // —— 4.3 提取落位：清洗 + 冲突后缀 ——
        let target = resolve_target(dest_dir, &entry.name);
        if fs::write(&target, content).is_err() {
            result.failed += 1;
            result.failed_names.push(entry.name.clone());
            continue;
        }
        result.succeeded += 1;
        // 已写入文件加入清单，供后续条去重；并记录元数据供落库合并
        existing.push((content_len, target.clone()));
        result.items.push(ImportedItem {
            file_path: target.to_string_lossy().into_owned(),
            name: entry.name.clone(),
            description: entry.description.clone(),
            created_at: entry.created_at,
            tags: entry.tags.clone(),
            width: entry.width,
            height: entry.height,
        });
    }

    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn test_sanitize_basic() {
        assert_eq!(sanitize_filename("正常名字.jpg"), "正常名字.jpg");
    }

    #[test]
    fn test_sanitize_invalid_chars() {
        assert_eq!(sanitize_filename("a/b:c*.png"), "a_b_c_.png");
    }

    #[test]
    fn test_sanitize_reserved() {
        assert_eq!(sanitize_filename("CON.jpg"), "_CON.jpg");
        assert_eq!(sanitize_filename("com1"), "_com1");
    }

    #[test]
    fn test_sanitize_trailing() {
        assert_eq!(sanitize_filename("名字. "), "名字");
    }

    #[test]
    fn test_sanitize_empty() {
        assert_eq!(sanitize_filename("...."), "meme");
    }

    #[test]
    fn test_resolve_conflict() {
        let dir = std::env::temp_dir().join(format!("mpak_conflict_{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        fs::write(dir.join("猫.jpg"), b"x").unwrap();

        let p = resolve_target(dir.to_str().unwrap(), "猫.jpg");
        assert_eq!(p.file_name().unwrap().to_string_lossy(), "猫_1.jpg");

        let _ = fs::remove_dir_all(&dir);
    }
}

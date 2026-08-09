//! .mpak 导出：读取媒体文件 → 计算哈希 → 按分片算法打包写入

use std::fs;
use std::path::{Path, PathBuf};

use super::{now_ms, sha256_hex, HEADER_LEN, MAGIC, VERSION, MediaEntry, Metadata};

/// 前端传入的待导出媒体（字段与前端 Media 对应）
#[derive(serde::Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct ExportItem {
    /// 显示名（原始文件名，可为中文）
    pub name: String,
    /// 前端类型：image | gif | video
    #[serde(rename = "type")]
    pub media_type: String,
    /// 本地文件绝对路径（导出端读取内容）
    pub file_path: String,
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub description: Option<String>,
    /// 入库时间，Unix 毫秒时间戳
    pub created_at: i64,
    pub tags: Vec<String>,
}

#[derive(serde::Serialize, Debug)]
#[serde(rename_all = "camelCase")]
pub struct ExportResult {
    /// 生成的分片文件完整路径
    pub shards: Vec<String>,
    /// 导出媒体总数
    pub total_media: usize,
}

/// 前端类型 → 规范枚举（IMAGE | VIDEO | GIF）
fn map_type(t: &str) -> String {
    match t.to_ascii_uppercase().as_str() {
        "GIF" => "GIF".into(),
        "VIDEO" => "VIDEO".into(),
        _ => "IMAGE".into(),
    }
}

/// 二进制段扩展名：优先原扩展名（清洗为 [a-zA-Z0-9]，截断 5 字符），否则按类型默认
fn ext_for(path: &str, media_type: &str) -> String {
    let ext: String = Path::new(path)
        .extension()
        .and_then(|e| e.to_str())
        .map(|e| e.chars().filter(|c| c.is_ascii_alphanumeric()).collect())
        .filter(|e: &String| !e.is_empty())
        .unwrap_or_else(|| match media_type {
            "gif" => "gif".into(),
            "video" => "mp4".into(),
            _ => "jpg".into(),
        })
        .chars()
        .take(5)
        .collect();
    format!(".{}", ext)
}

/// 二进制段文件名：meme_ + 4 位全局序号 + 扩展名（规范 5.2）
fn safe_bin_name(index: usize, path: &str, media_type: &str) -> String {
    format!("meme_{:04}{}", index, ext_for(path, media_type))
}

/// 构造一个分片的完整字节流（文件头 + JSON + 媒体数据段 + 尾部哈希）
fn build_shard_bytes(
    prepared: &[(ExportItem, Vec<u8>, String, String)],
    indices: &[usize],
) -> Result<Vec<u8>, String> {
    // —— JSON 段 ——
    let media_entries: Vec<MediaEntry> = indices
        .iter()
        .map(|&i| {
            let (item, bytes, sha, bin_name) = &prepared[i];
            MediaEntry {
                file_name: bin_name.clone(),
                name: item.name.clone(),
                media_type: map_type(&item.media_type),
                size: bytes.len() as u64,
                sha256: sha.clone(),
                width: item.width,
                height: item.height,
                description: item.description.clone(),
                created_at: item.created_at,
                tags: item.tags.clone(),
            }
        })
        .collect();

    let metadata = Metadata {
        format: super::FORMAT_ID.into(),
        version: VERSION,
        exported_at: now_ms(),
        media: media_entries,
    };
    // serde_json 默认输出 UTF-8 明文（不转义非 ASCII），符合规范 5.1
    let json_bytes = serde_json::to_vec(&metadata)
        .map_err(|e| format!("元数据 JSON 序列化失败: {}", e))?;

    // —— 文件头（全大端）——
    let mut out: Vec<u8> = Vec::with_capacity(HEADER_LEN + json_bytes.len());
    out.extend_from_slice(&MAGIC);
    out.extend_from_slice(&VERSION.to_be_bytes());
    out.extend_from_slice(&(indices.len() as u32).to_be_bytes());
    out.extend_from_slice(&(json_bytes.len() as u32).to_be_bytes());

    // —— JSON 段 ——
    out.extend_from_slice(&json_bytes);

    // —— 媒体数据段 ——
    for &i in indices {
        let (_, bytes, _, bin_name) = &prepared[i];
        out.extend_from_slice(&(bin_name.len() as u16).to_be_bytes());
        out.extend_from_slice(bin_name.as_bytes());
        out.extend_from_slice(&(bytes.len() as u64).to_be_bytes());
        out.extend_from_slice(bytes);
    }

    // —— 尾部哈希（覆盖以上全部内容）——
    let hash = sha256_hex(&out);
    let hash_bytes = hex::decode(&hash).map_err(|e| format!("哈希编码异常: {}", e))?;
    out.extend_from_slice(&hash_bytes);
    Ok(out)
}

/// 分片打包入口（带进度回调：on_progress(已处理数, 总数)，用于前端进度展示）
pub fn export_pak_with_progress(
    items: Vec<ExportItem>,
    max_size: u64,
    dest_dir: &str,
    mut on_progress: impl FnMut(usize, usize),
) -> Result<ExportResult, String> {
    export_pak_inner(items, max_size, dest_dir, &mut on_progress)
}

fn export_pak_inner(
    items: Vec<ExportItem>,
    max_size: u64,
    dest_dir: &str,
    mut on_progress: impl FnMut(usize, usize),
) -> Result<ExportResult, String> {
    if items.is_empty() {
        // 规范 6.2：空导出不生成文件
        return Err("未选择任何媒体".into());
    }
    if max_size == 0 {
        return Err("分片大小上限必须大于 0".into());
    }

    // 预读所有文件：字节 + SHA-256 + 二进制段文件名
    // prepared[i] = (item, bytes, sha256, bin_name)
    let mut prepared: Vec<(ExportItem, Vec<u8>, String, String)> = Vec::with_capacity(items.len());
    for (i, item) in items.iter().enumerate() {
        let bytes = fs::read(&item.file_path)
            .map_err(|e| format!("读取文件失败「{}」: {}", item.file_path, e))?;
        let sha = sha256_hex(&bytes);
        let bin_name = safe_bin_name(i + 1, &item.file_path, &item.media_type);
        prepared.push((item.clone(), bytes, sha, bin_name));
        on_progress(i + 1, items.len());
    }

    // —— 贪心分片（规范 6.1）——
    // 片大小 = 14 + JSON 段长度 + Σ(2 + 文件名长 + 8 + 文件字节)
    // 贪心时不含 JSON 长度（写入后可能略超上限，三端一致即可）
    let mut shards: Vec<Vec<usize>> = Vec::new();
    let mut current: Vec<usize> = Vec::new();
    let mut used: u64 = HEADER_LEN as u64;
    for idx in 0..prepared.len() {
        let (_, bytes, _, bin_name) = &prepared[idx];
        let new_size = used + 2 + bin_name.len() as u64 + 8 + bytes.len() as u64;
        if new_size <= max_size {
            current.push(idx);
            used = new_size;
        } else if current.is_empty() {
            // 单文件超过上限：单独成片（允许超限）
            current.push(idx);
            used = new_size;
        } else {
            shards.push(std::mem::take(&mut current));
            used = HEADER_LEN as u64 + 2 + bin_name.len() as u64 + 8 + bytes.len() as u64;
            current.push(idx);
        }
    }
    if !current.is_empty() {
        shards.push(current);
    }

    fs::create_dir_all(dest_dir).map_err(|e| format!("创建输出目录失败「{}」: {}", dest_dir, e))?;

    let mut written: Vec<String> = Vec::with_capacity(shards.len());
    for (si, shard) in shards.iter().enumerate() {
        // 避免覆盖已存在文件：meme_0001.mpak、meme_0001_2.mpak ...
        let mut path = PathBuf::from(dest_dir).join(format!("meme_{:04}.mpak", si + 1));
        let mut n = 2;
        while path.exists() {
            path = PathBuf::from(dest_dir).join(format!("meme_{:04}_{}.mpak", si + 1, n));
            n += 1;
        }
        let data = build_shard_bytes(&prepared, shard)?;
        fs::write(&path, &data).map_err(|e| format!("写入分片失败「{}」: {}", path.display(), e))?;
        written.push(path.to_string_lossy().into_owned());
    }

    Ok(ExportResult {
        shards: written,
        total_media: prepared.len(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    fn tmp_dir(tag: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("mpak_export_{}_{}", tag, std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        dir
    }

    fn make_item(dir: &Path, file: &str, name: &str) -> ExportItem {
        let path = dir.join(file);
        fs::write(&path, format!("content-of-{}", file).as_bytes()).unwrap();
        ExportItem {
            name: name.into(),
            media_type: "image".into(),
            file_path: path.to_string_lossy().into_owned(),
            width: Some(100),
            height: Some(100),
            description: Some("测试".into()),
            created_at: 1785000001000,
            tags: vec!["搞笑".into(), "猫".into()],
        }
    }

    #[test]
    fn test_map_type() {
        assert_eq!(map_type("image"), "IMAGE");
        assert_eq!(map_type("gif"), "GIF");
        assert_eq!(map_type("video"), "VIDEO");
    }

    #[test]
    fn test_ext_for() {
        assert_eq!(ext_for("/a/b/photo.JPG", "image"), ".JPG");
        assert_eq!(ext_for("/a/b/noext", "image"), ".jpg");
        assert_eq!(ext_for("/a/b/anim.gif", "video"), ".gif");
    }

    #[test]
    fn test_empty_export_rejected() {
        let dir = tmp_dir("empty");
        let err = export_pak_with_progress(vec![], 1024, dir.to_str().unwrap(), |_, _| {})
            .unwrap_err();
        assert!(err.contains("未选择"));
    }

    #[test]
    fn test_roundtrip_single_shard() {
        let dir = tmp_dir("rt");
        let media_dir = dir.join("media");
        fs::create_dir_all(&media_dir).unwrap();

        let items = vec![
            make_item(&media_dir, "a.png", "猫咪图.png"),
            make_item(&media_dir, "b.gif", "GIF动画.gif"),
        ];

        let out_dir = dir.join("out");
        let result =
            export_pak_with_progress(items, 1024 * 1024, out_dir.to_str().unwrap(), |_, _| {})
                .unwrap();
        assert_eq!(result.shards.len(), 1);
        assert_eq!(result.total_media, 2);
        assert!(Path::new(&result.shards[0]).exists());

        // 尾部哈希必须为 32 字节
        let bytes = fs::read(&result.shards[0]).unwrap();
        assert!(bytes.len() >= super::super::HEADER_LEN + super::super::HASH_LEN);
    }

    #[test]
    fn test_sharding_large_media() {
        let dir = tmp_dir("shard");
        let media_dir = dir.join("media");
        fs::create_dir_all(&media_dir).unwrap();

        let mut items = Vec::new();
        for i in 0..5 {
            let file = format!("f{}.png", i);
            let p = media_dir.join(&file);
            // 每文件 ~1KB，上限 3KB → 应分多片
            fs::write(&p, vec![0xABu8; 1024]).unwrap();
            items.push(ExportItem {
                name: file.clone(),
                media_type: "image".into(),
                file_path: p.to_string_lossy().into_owned(),
                width: None,
                height: None,
                description: None,
                created_at: 1785000001000 + i,
                tags: vec![],
            });
        }
        let out_dir = dir.join("out");
        let result = export_pak_with_progress(items, 3 * 1024, out_dir.to_str().unwrap(), |_, _| {})
            .unwrap();
        assert!(result.shards.len() >= 2, "5×1KB 按 3KB 上限应分 ≥2 片");
    }
}

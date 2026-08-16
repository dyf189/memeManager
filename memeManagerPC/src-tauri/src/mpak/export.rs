//! .mpak 导出：预扫描（元数据 + 流式哈希）→ 按分片算法规划 → 流式写出。
//! 全程不把媒体文件整体读进内存，支持随时取消。

use std::fs::{self, File};
use std::io::Write;
use std::path::{Path, PathBuf};

use sha2::{Digest, Sha256};

use super::{now_ms, sha256_file, HEADER_LEN, MAGIC, VERSION, MediaEntry, Metadata};

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

/// 预扫描结果：只保留元数据与哈希，不持有文件内容
struct Prepared {
    item: ExportItem,
    size: u64,
    sha: String,
    bin_name: String,
}

/// 边写边算哈希的包装器（用于尾部 SHA-256 覆盖全部已写内容）
struct HashWriter<W: Write> {
    inner: W,
    hasher: Sha256,
}

impl<W: Write> Write for HashWriter<W> {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        self.hasher.update(buf);
        self.inner.write(buf)
    }
    fn flush(&mut self) -> std::io::Result<()> {
        self.inner.flush()
    }
}

/// 写单个分片：文件头 + JSON 段 + 逐文件流式拷贝 + 尾部哈希
fn write_shard(prepared: &[Prepared], indices: &[usize], path: &Path) -> Result<(), String> {
    let media_entries: Vec<MediaEntry> = indices
        .iter()
        .map(|&i| {
            let p = &prepared[i];
            MediaEntry {
                file_name: p.bin_name.clone(),
                name: p.item.name.clone(),
                media_type: map_type(&p.item.media_type),
                size: p.size,
                sha256: p.sha.clone(),
                width: p.item.width,
                height: p.item.height,
                description: p.item.description.clone(),
                created_at: p.item.created_at,
                tags: p.item.tags.clone(),
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

    let file = File::create(path).map_err(|e| format!("创建分片失败「{}」: {}", path.display(), e))?;
    let mut w = HashWriter { inner: file, hasher: Sha256::new() };

    // —— 文件头（全大端）——
    w.write_all(&MAGIC).map_err(io_err(path))?;
    w.write_all(&VERSION.to_be_bytes()).map_err(io_err(path))?;
    w.write_all(&(indices.len() as u32).to_be_bytes()).map_err(io_err(path))?;
    w.write_all(&(json_bytes.len() as u32).to_be_bytes()).map_err(io_err(path))?;

    // —— JSON 段 ——
    w.write_all(&json_bytes).map_err(io_err(path))?;

    // —— 媒体数据段（流式拷贝，不整读进内存）——
    for &i in indices {
        let p = &prepared[i];
        w.write_all(&(p.bin_name.len() as u16).to_be_bytes()).map_err(io_err(path))?;
        w.write_all(p.bin_name.as_bytes()).map_err(io_err(path))?;
        w.write_all(&(p.size).to_be_bytes()).map_err(io_err(path))?;
        let mut src = File::open(&p.item.file_path)
            .map_err(|e| format!("读取文件失败「{}」: {}", p.item.file_path, e))?;
        std::io::copy(&mut src, &mut w).map_err(io_err(path))?;
    }

    // —— 尾部哈希（直接写 inner，避免把哈希自身计入哈希）——
    let digest = w.hasher.finalize();
    w.inner.write_all(&digest).map_err(io_err(path))?;
    w.inner.flush().map_err(io_err(path))?;
    Ok(())
}

fn io_err(path: &Path) -> impl Fn(std::io::Error) -> String + '_ {
    move |e| format!("写入分片失败「{}」: {}", path.display(), e)
}

/// 分片打包入口（带进度回调：on_progress(已处理数, 总数)，用于前端进度展示）。
/// 不可取消的便捷封装，主要供测试使用；应用内导出走 export_pak_cancellable。
#[allow(dead_code)]
pub fn export_pak_with_progress(
    items: Vec<ExportItem>,
    max_size: u64,
    dest_dir: &str,
    on_progress: impl FnMut(usize, usize),
) -> Result<ExportResult, String> {
    export_pak_inner(items, max_size, dest_dir, on_progress, || false)
}

/// 可取消版本：cancelled() 返回 true 时中止并清理已写的分片文件
pub fn export_pak_cancellable<P, C>(
    items: Vec<ExportItem>,
    max_size: u64,
    dest_dir: &str,
    mut on_progress: P,
    cancelled: C,
) -> Result<ExportResult, String>
where
    P: FnMut(usize, usize),
    C: Fn() -> bool,
{
    export_pak_inner(items, max_size, dest_dir, |d, t| on_progress(d, t), cancelled)
}

fn export_pak_inner<P, C>(
    items: Vec<ExportItem>,
    max_size: u64,
    dest_dir: &str,
    mut on_progress: P,
    cancelled: C,
) -> Result<ExportResult, String>
where
    P: FnMut(usize, usize),
    C: Fn() -> bool,
{
    if items.is_empty() {
        // 规范 6.2：空导出不生成文件
        return Err("未选择任何媒体".into());
    }
    if max_size == 0 {
        return Err("分片大小上限必须大于 0".into());
    }

    // —— 预扫描：大小 + 流式哈希 + 二进制段文件名（不持有文件内容）——
    let mut prepared: Vec<Prepared> = Vec::with_capacity(items.len());
    for (i, item) in items.iter().enumerate() {
        if cancelled() {
            return Err("导出已取消".into());
        }
        let meta = fs::metadata(&item.file_path)
            .map_err(|e| format!("读取文件信息失败「{}」: {}", item.file_path, e))?;
        let sha = sha256_file(Path::new(&item.file_path))
            .ok_or_else(|| format!("读取文件失败「{}」", item.file_path))?;
        let bin_name = safe_bin_name(i + 1, &item.file_path, &item.media_type);
        prepared.push(Prepared { item: item.clone(), size: meta.len(), sha, bin_name });
        on_progress(i + 1, items.len());
    }

    // —— 贪心分片（规范 6.1）——
    // 片大小 = 14 + JSON 段长度 + Σ(2 + 文件名长 + 8 + 文件字节)
    // 贪心时不含 JSON 长度（写入后可能略超上限，三端一致即可）
    let mut shards: Vec<Vec<usize>> = Vec::new();
    let mut current: Vec<usize> = Vec::new();
    let mut used: u64 = HEADER_LEN as u64;
    for idx in 0..prepared.len() {
        let p = &prepared[idx];
        let entry_size = 2 + p.bin_name.len() as u64 + 8 + p.size;
        let new_size = used + entry_size;
        if new_size <= max_size {
            current.push(idx);
            used = new_size;
        } else if current.is_empty() {
            // 单文件超过上限：单独成片（允许超限）
            current.push(idx);
            used = new_size;
        } else {
            shards.push(std::mem::take(&mut current));
            used = HEADER_LEN as u64 + entry_size;
            current.push(idx);
        }
    }
    if !current.is_empty() {
        shards.push(current);
    }

    fs::create_dir_all(dest_dir).map_err(|e| format!("创建输出目录失败「{}」: {}", dest_dir, e))?;

    let mut written: Vec<String> = Vec::with_capacity(shards.len());
    for (si, shard) in shards.iter().enumerate() {
        if cancelled() {
            return Err("导出已取消".into());
        }
        // 避免覆盖已存在文件：meme_0001.mpak、meme_0001_2.mpak ...
        let mut path = PathBuf::from(dest_dir).join(format!("meme_{:04}.mpak", si + 1));
        let mut n = 2;
        while path.exists() {
            path = PathBuf::from(dest_dir).join(format!("meme_{:04}_{}.mpak", si + 1, n));
            n += 1;
        }
        if let Err(e) = write_shard(&prepared, shard, &path) {
            // 失败时移除半成品，避免留下损坏分片
            let _ = fs::remove_file(&path);
            return Err(e);
        }
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

        // 流式写出的分片能被导入端完整解析
        let dest = dir.join("dest");
        let imported = crate::mpak::import::import_pak(&result.shards[0], dest.to_str().unwrap()).unwrap();
        assert_eq!(imported.succeeded, 2);
        assert_eq!(imported.failed, 0);
        let roundtrip = fs::read(dest.join("猫咪图.png")).unwrap();
        assert_eq!(roundtrip, b"content-of-a.png");
    }

    #[test]
    fn test_cancellable_export() {
        let dir = tmp_dir("cancel");
        let media_dir = dir.join("media");
        fs::create_dir_all(&media_dir).unwrap();
        let items: Vec<ExportItem> = (0..3).map(|i| make_item(&media_dir, &format!("f{}.png", i), "x")).collect();

        let out_dir = dir.join("out");
        // 第一个文件预扫描后即取消
        let err = export_pak_cancellable(items, 1024 * 1024, out_dir.to_str().unwrap(), |_, _| {}, || true)
            .unwrap_err();
        assert!(err.contains("取消"));
        // 未生成任何分片
        assert!(fs::read_dir(&out_dir).map(|d| d.count()).unwrap_or(0) == 0);
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

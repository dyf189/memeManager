//! .mpak 格式实现（规范见项目根 export-format.md v0.1）
//!
//! 布局：文件头(14B) + 元数据JSON段 + 媒体数据段 + SHA-256(32B)
//! 字节序：全文件多字节整数一律大端（网络字节序）

pub mod export;
pub mod import;

use sha2::{Digest, Sha256};
use std::io::Read;
use std::path::Path;

/// 魔数 "MPAK"
pub const MAGIC: [u8; 4] = *b"MPAK";
/// 格式版本
pub const VERSION: u16 = 1;
/// 文件头长度（固定 14 字节）
pub const HEADER_LEN: usize = 14;
/// 尾部 SHA-256 长度（固定 32 字节）
pub const HASH_LEN: usize = 32;
/// JSON 段 `format` 字段固定值
pub const FORMAT_ID: &str = "memeManager";

/// 元数据 JSON 段（schema 与 export-format.md 第 3 节一致）
#[derive(serde::Serialize, serde::Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct Metadata {
    pub format: String,
    pub version: u16,
    pub exported_at: i64,
    pub media: Vec<MediaEntry>,
}

/// 单条媒体元数据
#[derive(serde::Serialize, serde::Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct MediaEntry {
    /// 二进制段文件名（ASCII 安全名，关联键）
    pub file_name: String,
    /// 显示名（原始名，可为中文）
    pub name: String,
    /// IMAGE | VIDEO | GIF
    #[serde(rename = "type")]
    pub media_type: String,
    /// 文件字节数
    pub size: u64,
    /// 媒体文件二进制内容的 SHA-256（十六进制小写）
    pub sha256: String,
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub description: Option<String>,
    /// 入库时间，Unix 毫秒时间戳
    pub created_at: i64,
    pub tags: Vec<String>,
}

/// 计算 SHA-256（十六进制小写）
pub fn sha256_hex(bytes: &[u8]) -> String {
    let mut h = Sha256::new();
    h.update(bytes);
    hex::encode(h.finalize())
}

/// 流式计算文件 SHA-256（避免大文件整读进内存）
pub fn sha256_file(path: &Path) -> Option<String> {
    let mut f = std::fs::File::open(path).ok()?;
    let mut h = Sha256::new();
    let mut buf = [0u8; 64 * 1024];
    loop {
        let n = f.read(&mut buf).ok()?;
        if n == 0 {
            break;
        }
        h.update(&buf[..n]);
    }
    Some(hex::encode(h.finalize()))
}

/// 当前 Unix 毫秒时间戳
pub fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

/// 安全读取 2 字节大端 u16（越界返回 None）
pub fn read_u16(data: &[u8], off: usize) -> Option<u16> {
    data.get(off..off + 2)
        .map(|s| u16::from_be_bytes([s[0], s[1]]))
}

/// 安全读取 4 字节大端 u32（越界返回 None）
pub fn read_u32(data: &[u8], off: usize) -> Option<u32> {
    data.get(off..off + 4).map(|s| u32::from_be_bytes([s[0], s[1], s[2], s[3]]))
}

/// 安全读取 8 字节大端 u64（越界返回 None）
pub fn read_u64(data: &[u8], off: usize) -> Option<u64> {
    data.get(off..off + 8)
        .map(|s| u64::from_be_bytes(s.try_into().unwrap()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sha256_hex_known_value() {
        // "abc" 的 SHA-256
        assert_eq!(
            sha256_hex(b"abc"),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
    }

    #[test]
    fn test_read_helpers_out_of_bounds() {
        let data = [0u8; 4];
        assert_eq!(read_u16(&data, 0), Some(0));
        assert_eq!(read_u16(&data, 3), None);
        assert_eq!(read_u32(&data, 0), Some(0));
        assert_eq!(read_u64(&data, 0), None);
    }
}

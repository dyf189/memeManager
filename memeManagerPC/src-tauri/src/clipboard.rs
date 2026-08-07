//! 剪贴板分享：图片复制为图像，视频/其他复制文件路径文本

use std::path::Path;

/// 复制文件到系统剪贴板，返回提示文本（包含实际执行路径，便于排查）
pub fn copy_to_clipboard(path: &str) -> Result<String, String> {
    let p = Path::new(path);
    if !p.exists() {
        return Err(format!("文件不存在: {}", path));
    }

    let ext = p
        .extension()
        .and_then(|e| e.to_str())
        .map(|e| e.to_lowercase())
        .unwrap_or_default();

    // 视频无法复制为图像，退化为复制文件路径
    let is_video = matches!(
        ext.as_str(),
        "mp4" | "webm" | "mov" | "mkv" | "avi" | "mpg" | "mpeg" | "ogv" | "3gp" | "flv"
    );
    if is_video {
        let mut cb = arboard::Clipboard::new().map_err(|e| format!("剪贴板不可用: {}", e))?;
        cb.set_text(path).map_err(|e| format!("复制失败: {}", e))?;
        return Ok("视频无法复制图像，已复制文件路径到剪贴板".into());
    }

    // 图片（GIF 取第一帧）
    let img = image::open(p).map_err(|e| format!("无法解析图片（{}）: {}", ext, e))?;
    let rgba = img.to_rgba8();
    let (w, h) = rgba.dimensions();

    let mut cb = arboard::Clipboard::new().map_err(|e| format!("剪贴板不可用: {}", e))?;
    match cb.set_image(arboard::ImageData {
        width: w as usize,
        height: h as usize,
        bytes: rgba.into_raw().into(),
    }) {
        Ok(()) => Ok(format!("图片已复制到剪贴板 ({}×{})", w, h)),
        Err(e) => {
            // 图像写入失败（如 Wayland 限制）→ 降级复制路径，并明确告知
            let _ = cb.set_text(path);
            Err(format!("图像复制失败（{}），已降级复制文件路径", e))
        }
    }
}

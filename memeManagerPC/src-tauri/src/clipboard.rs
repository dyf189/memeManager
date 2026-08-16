//! 剪贴板分享：图片复制为图像，视频/其他复制文件路径文本。
//!
//! 多后端策略（按优先级）：
//!   1. arboard（Rust 原生，X11/Wayland）
//!   2. 系统工具 wl-copy（Wayland）/ xclip、xsel（X11）
//! 某些 Wayland 环境下 arboard 后端不可用，此时回退到系统工具。

use std::io::Cursor;
use std::io::Write;
use std::path::Path;
use std::process::{Command, Stdio};
use std::sync::Mutex;

/// 全局复用的剪贴板实例（arboard 的 Wayland 后端在反复 new() 时数据源会失效，
/// 复用同一实例可避免该问题）
static CLIPBOARD: Mutex<Option<arboard::Clipboard>> = Mutex::new(None);

fn with_clipboard<T>(
    f: impl FnOnce(&mut arboard::Clipboard) -> Result<T, String>,
) -> Result<T, String> {
    let mut guard = CLIPBOARD.lock().unwrap();
    if guard.is_none() {
        *guard = Some(
            arboard::Clipboard::new().map_err(|e| format!("剪贴板不可用: {}", e))?,
        );
    }
    f(guard.as_mut().unwrap())
}

/// 通过系统剪贴板工具写入二进制数据（指定 MIME 类型）
fn set_bytes_via_tool(bytes: &[u8], mime: &str) -> Result<(), String> {
    let attempts: [(&str, &[&str]); 2] = [
        ("wl-copy", &["--type", mime]),
        ("xclip", &["-selection", "clipboard", "-t", mime]),
    ];
    for (bin, args) in attempts {
        let Ok(mut child) = Command::new(bin).args(args).stdin(Stdio::piped()).spawn() else {
            continue;
        };
        let wrote = child
            .stdin
            .take()
            .map(|mut s| s.write_all(bytes).is_ok())
            .unwrap_or(false);
        if !wrote {
            let _ = child.kill();
            continue;
        }
        if child.wait().map(|s| s.success()).unwrap_or(false) {
            return Ok(());
        }
    }
    Err(format!("未找到可用的剪贴板工具（wl-copy / xclip）写 {}", mime))
}

/// 通过系统剪贴板工具写入图片（PNG 字节）
fn set_image_via_tool(png: &[u8]) -> Result<(), String> {
    set_bytes_via_tool(png, "image/png")
}

/// 通过系统剪贴板工具写入文本
fn set_text_via_tool(text: &str) -> Result<(), String> {
    let attempts: [(&str, &[&str]); 3] = [
        ("wl-copy", &[]),
        ("xclip", &["-selection", "clipboard"]),
        ("xsel", &["--clipboard", "--input"]),
    ];
    for (bin, args) in attempts {
        let Ok(mut child) = Command::new(bin).args(args).stdin(Stdio::piped()).spawn() else {
            continue;
        };
        let wrote = child
            .stdin
            .take()
            .map(|mut s| s.write_all(text.as_bytes()).is_ok())
            .unwrap_or(false);
        if !wrote {
            let _ = child.kill();
            continue;
        }
        if child.wait().map(|s| s.success()).unwrap_or(false) {
            return Ok(());
        }
    }
    Err("未找到可用的剪贴板工具（wl-copy / xclip / xsel）".into())
}

/// 复制文件到系统剪贴板，返回提示文本（包含实际执行路径，便于排查）
pub fn copy_to_clipboard(path: &str) -> Result<String, String> {
    eprintln!("[clipboard] 复制请求: {}", path);
    let p = Path::new(path);
    if !p.exists() {
        eprintln!("[clipboard] 文件不存在");
        return Err(format!("文件不存在: {}", path));
    }

    let ext = p
        .extension()
        .and_then(|e| e.to_str())
        .map(|e| e.to_lowercase())
        .unwrap_or_default();
    eprintln!("[clipboard] 扩展名: {}", ext);

    // 视频无法复制为图像，退化为复制文件路径
    let is_video = matches!(
        ext.as_str(),
        "mp4" | "webm" | "mov" | "mkv" | "avi" | "mpg" | "mpeg" | "ogv" | "3gp" | "flv"
    );
    if is_video {
        match with_clipboard(|cb| cb.set_text(path).map_err(|e| e.to_string())) {
            Ok(()) => {
                eprintln!("[clipboard] 视频：arboard 写入路径文本成功");
                return Ok("视频无法复制图像，已复制文件路径到剪贴板".into());
            }
            Err(e) => eprintln!("[clipboard] 视频：arboard set_text 失败: {}", e),
        }
        set_text_via_tool(path)?;
        eprintln!("[clipboard] 视频：系统工具写入成功");
        return Ok("视频无法复制图像，已复制文件路径到剪贴板（系统工具）".into());
    }

    // 图片：按文件内容探测格式（解决 .jpg 伪装成 GIF/其他格式的动图）
    let mut reader = image::ImageReader::open(p)
        .map_err(|e| format!("无法打开图片（{}）: {}", ext, e))?;
    reader = reader
        .with_guessed_format()
        .map_err(|e| format!("无法识别图片格式: {}", e))?;
    // 用真实内容格式判断是否为 GIF（伪装扩展名也正确识别）
    let is_gif = reader.format() == Some(image::ImageFormat::Gif);
    let img = match reader.decode() {
        Ok(img) => img,
        Err(e) => {
            // HEIC/AVIF 等未编入解码器的格式或损坏文件：退化为复制路径文本，而非直接失败
            eprintln!("[clipboard] 图片解码失败（{}）: {}", ext, e);
            let note = format!("无法解析该图片（{}），已复制文件路径到剪贴板", ext);
            if with_clipboard(|cb| cb.set_text(path).map_err(|e| e.to_string())).is_ok() {
                return Ok(note);
            }
            set_text_via_tool(path)?;
            return Ok(format!("{}（系统工具）", note));
        }
    };
    let rgba = img.to_rgba8();
    let (w, h) = rgba.dimensions();
    let dims = format!("{}×{}", w, h);
    eprintln!("[clipboard] 图片尺寸: {}x{} (is_gif={})", w, h, is_gif);

    // GIF 动画无法通过剪贴板传播，提示用户
    let gif_note = if is_gif { "；GIF 动画无法在剪贴板保留，已复制静态帧" } else { "" };

    // 1) arboard 原生写入（复用全局实例）+ 读回验证；
    //    arboard 在 Wayland 下可能报成功但数据源不可用，验证失败则回退系统工具
    let arboard_ok = with_clipboard(|cb| {
        cb.set_image(arboard::ImageData {
            width: w as usize,
            height: h as usize,
            bytes: rgba.clone().into_raw().into(),
        })
        .map_err(|e| format!("set_image 失败: {}", e))?;
        // 读回验证：能读到同尺寸图像才算真正写入
        match cb.get_image() {
            Ok(im) => {
                if im.width == w as usize && im.height == h as usize {
                    Ok(true)
                } else {
                    eprintln!("[clipboard] 验证失败: 尺寸不匹配");
                    Ok(false)
                }
            }
            Err(e) => {
                eprintln!("[clipboard] 读回验证失败: {}", e);
                Ok(false)
            }
        }
    });
    match arboard_ok {
        Ok(true) => {
            eprintln!("[clipboard] arboard set_image + 验证成功");
            return Ok(format!("图片已复制到剪贴板 ({}){}", dims, gif_note));
        }
        Ok(false) => eprintln!("[clipboard] arboard 写入后验证不通过，回退系统工具"),
        Err(e) => eprintln!("[clipboard] arboard 失败: {}", e),
    }

    // 2) 系统工具（wl-copy / xclip）写入 PNG（Wayland 下最可靠）
    let mut png_buf: Vec<u8> = Vec::new();
    img.write_to(&mut Cursor::new(&mut png_buf), image::ImageFormat::Png)
        .map_err(|e| format!("图片编码失败: {}", e))?;
    match set_image_via_tool(&png_buf) {
        Ok(()) => {
            eprintln!("[clipboard] 系统工具写入成功");
            Ok(format!("图片已复制到剪贴板 ({}, 系统工具){}", dims, gif_note))
        }
        Err(e) => {
            eprintln!("[clipboard] 系统工具写入失败: {}", e);
            Err(e)
        }
    }
}

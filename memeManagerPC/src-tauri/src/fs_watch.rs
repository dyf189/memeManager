//! 已索引目录的文件系统监视：外部文件操作（复制/删除/修改）自动同步入库，
//! 并通过 `media-changed` 事件通知前端刷新。

use std::collections::HashMap;
use std::path::Path;
use std::sync::Mutex;
use std::time::{Duration, Instant};

use notify::{RecursiveMode, Watcher};
use tauri::{AppHandle, Emitter, Manager};

use crate::db::Db;
use crate::media;

/// 保活 watcher（tauri state 持有，防止被 drop 后停止监听）
pub struct Watchers(pub Mutex<HashMap<String, notify::RecommendedWatcher>>);

/// 每个目录的最后同步时间（事件防抖）
pub struct SyncTimes(pub Mutex<HashMap<String, Instant>>);

/// 对目录执行一次增量同步，成功后通知前端刷新
fn sync_dir(handle: &AppHandle, dir: &str) {
    // 防抖：800ms 内重复事件合并为一次同步。
    // 只有同步成功才记录时间：失败时保持窗口关闭，后续事件可立即重试。
    let now = Instant::now();
    {
        let state = handle.state::<SyncTimes>();
        let times = state.0.lock().unwrap();
        if let Some(last) = times.get(dir) {
            if now.duration_since(*last) < Duration::from_millis(800) {
                return;
            }
        }
    }

    let state = handle.state::<Db>();
    let conn = match state.0.lock() {
        Ok(g) => g,
        Err(_) => return,
    };
    if media::scan_folder_impl(&conn, dir, true).is_err() {
        return;
    }
    drop(conn);

    {
        let state = handle.state::<SyncTimes>();
        let mut times = state.0.lock().unwrap();
        times.insert(dir.to_string(), now);
    }

    // 通知前端刷新（外部文件变化立即反映到相册）
    let _ = handle.emit("media-changed", ());
}

/// 启动（或复用）目录的文件监视；重复调用安全
pub fn start_watching(app: &AppHandle, dir: &str) {
    let state = app.state::<Watchers>();
    let mut watchers = match state.0.lock() {
        Ok(g) => g,
        Err(_) => return,
    };
    if watchers.contains_key(dir) {
        return;
    }

    let handle = app.clone();
    let dir_owned = dir.to_string();
    let mut watcher = match notify::recommended_watcher(move |res: notify::Result<notify::Event>| {
        if res.is_ok() {
            let h = handle.clone();
            let d = dir_owned.clone();
            // 在独立线程执行同步，避免阻塞 notify 线程
            std::thread::spawn(move || sync_dir(&h, &d));
        }
    }) {
        Ok(w) => w,
        Err(_) => return,
    };

    if watcher
        .watch(Path::new(dir), RecursiveMode::Recursive)
        .is_ok()
    {
        watchers.insert(dir.to_string(), watcher);
    }
}

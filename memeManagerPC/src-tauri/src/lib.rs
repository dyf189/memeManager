mod clipboard;
mod db;
mod fs_watch;
mod media;
mod mpak;

use tauri::State;
use tauri::Manager;

use db::Db;

/// 按设置中的保留天数清理过期回收站条目，有清理时通知前端刷新
fn auto_purge_recycle(app: tauri::AppHandle) {
    let state = app.state::<Db>();
    let Ok(conn) = state.0.lock() else { return };
    let days = media::get_recycle_days_impl(&conn);
    if days <= 0 {
        return; // 0 = 不进回收站（前端直接永久删除），无需定时清理
    }
    let purged = match media::auto_purge_recycle_impl(&conn, days) {
        Ok(n) => n,
        Err(_) => return,
    };
    drop(conn);
    if purged > 0 {
        use tauri::Emitter;
        let _ = app.emit("media-changed", ());
    }
}

// ===== .mpak 导入导出 =====

/// 导出 .mpak 分片（前端传入待导出媒体列表 + 分片上限 + 输出目录）
#[tauri::command]
fn export_pak(
    app: tauri::AppHandle,
    items: Vec<mpak::export::ExportItem>,
    max_size: u64,
    dest_dir: String,
) -> Result<mpak::export::ExportResult, String> {
    use tauri::Emitter;
    mpak::export::export_pak_with_progress(items, max_size, &dest_dir, |done, total| {
        let _ = app.emit("export-progress", (done, total));
    })
}

/// 导入 .mpak 分片（校验 → 解析 → 提取到目标目录 → 扫描入库 → 元数据合并）
#[tauri::command]
fn import_pak(
    app: tauri::AppHandle,
    db: State<Db>,
    path: String,
    dest_dir: String,
) -> Result<mpak::import::ImportResult, String> {
    let result = mpak::import::import_pak(&path, &dest_dir)?;
    // 提取出的元数据（描述/标签/时间/尺寸）合并到媒体库。
    // 必须先扫描入库再合并：apply 按 file_path 反查记录，
    // 若目标目录尚未索引（自定义目录/监视未触发），记录不存在会导致元数据全部丢失。
    if !result.items.is_empty() {
        let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
        media::scan_folder_impl(&conn, &dest_dir, true)?;
        media::apply_imported_metadata_impl(&conn, &result.items)?;
        media::add_index_dir_impl(&conn, &dest_dir).map_err(|e| e.to_string())?;
        drop(conn);
        fs_watch::start_watching(&app, &dest_dir);
    }
    Ok(result)
}

// ===== 数据层命令（薄包装，逻辑在 media.rs）=====

#[tauri::command]
fn scan_folder(
    app: tauri::AppHandle,
    db: State<Db>,
    dir: String,
    recursive: bool,
) -> Result<media::ScanSummary, String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    let sum = media::scan_folder_impl(&conn, &dir, recursive)?;
    // 建立索引：记录该目录并启动文件监视（外部文件变化自动同步）
    media::add_index_dir_impl(&conn, &dir).map_err(|e| e.to_string())?;
    drop(conn);
    fs_watch::start_watching(&app, &dir);
    Ok(sum)
}

#[tauri::command]
fn list_media(db: State<Db>) -> Result<Vec<media::Media>, String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::list_media_impl(&conn)
}

#[tauri::command]
fn list_recycle(db: State<Db>) -> Result<Vec<media::Media>, String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::list_recycle_impl(&conn)
}

#[tauri::command]
fn list_tags(db: State<Db>) -> Result<Vec<media::Tag>, String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::list_tags_impl(&conn)
}

#[tauri::command]
fn add_tag(db: State<Db>, name: String, bg_color: String) -> Result<media::Tag, String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::add_tag_impl(&conn, &name, &bg_color)
}

#[tauri::command]
fn rename_tag(db: State<Db>, id: i64, name: String) -> Result<(), String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::rename_tag_impl(&conn, id, &name)
}

#[tauri::command]
fn set_tag_color(db: State<Db>, id: i64, color: String) -> Result<(), String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::set_tag_color_impl(&conn, id, &color)
}

#[tauri::command]
fn delete_tag(db: State<Db>, id: i64) -> Result<(), String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::delete_tag_impl(&conn, id)
}

#[tauri::command]
fn set_tag_order(db: State<Db>, ids: Vec<i64>) -> Result<(), String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::set_tag_order_impl(&conn, &ids)
}

#[tauri::command]
fn set_description(db: State<Db>, id: i64, description: String) -> Result<(), String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::set_description_impl(&conn, id, &description)
}

#[tauri::command]
fn replace_media_tags(db: State<Db>, media_ids: Vec<i64>, tag_ids: Vec<i64>) -> Result<(), String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::replace_media_tags_impl(&conn, &media_ids, &tag_ids)
}

#[tauri::command]
fn set_media_order(db: State<Db>, ids: Vec<i64>) -> Result<(), String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::set_media_order_impl(&conn, &ids)
}

#[tauri::command]
fn delete_media(db: State<Db>, ids: Vec<i64>) -> Result<usize, String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::delete_media_impl(&conn, &ids)
}

#[tauri::command]
fn restore_media(db: State<Db>, ids: Vec<i64>) -> Result<usize, String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::restore_media_impl(&conn, &ids)
}

#[tauri::command]
fn purge_media(db: State<Db>, ids: Vec<i64>) -> Result<usize, String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::purge_media_impl(&conn, &ids)
}

#[tauri::command]
fn get_settings(db: State<Db>) -> Result<std::collections::HashMap<String, String>, String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::get_settings_impl(&conn)
}

#[tauri::command]
fn set_setting(db: State<Db>, key: String, value: String) -> Result<(), String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    media::set_setting_impl(&conn, &key, &value)
}

/// 程序默认媒体目录：应用数据目录下的 media 文件夹（不存在则创建）
#[tauri::command]
fn default_media_dir(app: tauri::AppHandle) -> Result<String, String> {
    let dir = app
        .path()
        .app_data_dir()
        .map_err(|e| format!("无法获取应用数据目录: {}", e))?
        .join("media");
    std::fs::create_dir_all(&dir).map_err(|e| format!("创建媒体目录失败: {}", e))?;
    Ok(dir.to_string_lossy().into_owned())
}

/// 导入用户选择的单张/批量文件：复制到目标目录并入库
#[tauri::command]
fn import_files(
    app: tauri::AppHandle,
    db: State<Db>,
    paths: Vec<String>,
    dest_dir: String,
) -> Result<media::ScanSummary, String> {
    let conn = db.0.lock().map_err(|_| "数据库锁异常".to_string())?;
    let sum = media::import_files_impl(&conn, &paths, &dest_dir)?;
    media::add_index_dir_impl(&conn, &dest_dir).map_err(|e| e.to_string())?;
    drop(conn);
    fs_watch::start_watching(&app, &dest_dir);
    Ok(sum)
}

/// 复制文件到系统剪贴板（图片复制图像，视频/其他复制路径文本）
#[tauri::command]
fn copy_to_clipboard(path: String) -> Result<String, String> {
    clipboard::copy_to_clipboard(&path)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_drag::init())
        .setup(|app| {
            // 数据库放在应用数据目录：~/.local/share/com.dyf189.mememanager/
            let dir = app
                .path()
                .app_data_dir()
                .map_err(|e| format!("无法获取应用数据目录: {}", e))?;
            let conn = db::init(&dir.join("mememanager.db"))
                .map_err(|e| format!("初始化数据库失败: {}", e))?;
            app.manage(Db(std::sync::Mutex::new(conn)));
            app.manage(fs_watch::Watchers(std::sync::Mutex::new(
                std::collections::HashMap::new(),
            )));
            app.manage(fs_watch::SyncTimes(std::sync::Mutex::new(
                std::collections::HashMap::new(),
            )));

            // 默认媒体目录自动建立索引（用户直接往里放文件也能入库）
            let media_dir = dir.join("media");
            let _ = std::fs::create_dir_all(&media_dir);
            {
                let state = app.state::<Db>();
                let conn = state.0.lock().map_err(|_| "数据库锁异常".to_string())?;
                let media_dir_str = media_dir.to_string_lossy().into_owned();
                let _ = media::scan_folder_impl(&conn, &media_dir_str, true);
                let _ = media::add_index_dir_impl(&conn, &media_dir_str);
            }

            // 启动所有已索引目录的文件监视
            let handle = app.handle().clone();
            if let Ok(dirs) = {
                let state = app.state::<Db>();
                let conn = state.0.lock().map_err(|_| "数据库锁异常".to_string())?;
                media::list_index_dirs_impl(&conn)
            } {
                for d in dirs {
                    fs_watch::start_watching(&handle, &d);
                }
            }

            // 回收站自动清理：启动时清一次，之后每 30 分钟检查（goals.md 定期任务）
            auto_purge_recycle(app.handle().clone());
            let timer_handle = app.handle().clone();
            std::thread::spawn(move || loop {
                std::thread::sleep(std::time::Duration::from_secs(30 * 60));
                auto_purge_recycle(timer_handle.clone());
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            export_pak,
            import_pak,
            scan_folder,
            list_media,
            list_recycle,
            list_tags,
            add_tag,
            rename_tag,
            set_tag_color,
            delete_tag,
            set_tag_order,
            set_description,
            replace_media_tags,
            delete_media,
            set_media_order,
            restore_media,
            purge_media,
            get_settings,
            set_setting,
            default_media_dir,
            import_files,
            copy_to_clipboard,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

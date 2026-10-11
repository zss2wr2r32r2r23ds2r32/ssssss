#![cfg_attr(all(not(debug_assertions), target_os = "windows"), windows_subsystem = "windows")]

use std::fs::{self, File};
use std::io::{Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

const MAGIC: &[u8; 8] = b"NEXAPAY1";
const INIT_SCRIPT: &str = r#"
document.addEventListener('contextmenu', function (event) { event.preventDefault(); }, true);
document.addEventListener('keydown', function (event) {
  var key = String(event.key || '').toLowerCase();
  var inspect = event.key === 'F12'
    || (event.ctrlKey && event.shiftKey && (key === 'i' || key === 'j' || key === 'c'))
    || (event.ctrlKey && !event.shiftKey && key === 'u')
    || (event.metaKey && event.altKey && key === 'i');
  if (inspect) {
    event.preventDefault();
    event.stopPropagation();
  }
}, true);
"#;

fn fail(message: &str) -> ! {
    rfd::MessageDialog::new()
        .set_title("Nexa")
        .set_description(message)
        .set_level(rfd::MessageLevel::Error)
        .show();
    std::process::exit(1);
}

fn local_root() -> PathBuf {
    if let Ok(dir) = std::env::var("LOCALAPPDATA") {
        if !dir.is_empty() {
            return PathBuf::from(dir).join("Nexa");
        }
    }
    if let Ok(dir) = std::env::var("APPDATA") {
        if !dir.is_empty() {
            return PathBuf::from(dir).join("Nexa");
        }
    }
    std::env::temp_dir().join("Nexa")
}

fn safe_join(root: &Path, rel: &str) -> Result<PathBuf, String> {
    if rel.is_empty() || rel.starts_with('/') || rel.contains(':') || rel.contains('\\') {
        return Err("Nexa.exe runtime is damaged.".into());
    }
    let mut out = root.to_path_buf();
    for part in rel.split('/') {
        if part.is_empty() || part == "." || part == ".." {
            return Err("Nexa.exe runtime is damaged.".into());
        }
        out.push(part);
    }
    Ok(out)
}

fn read_payload(exe: &Path) -> Result<Vec<u8>, String> {
    let mut file = File::open(exe).map_err(|err| err.to_string())?;
    let len = file.metadata().map_err(|err| err.to_string())?.len();
    if len < 16 {
        return Err("Nexa.exe is missing its runtime.".into());
    }
    file.seek(SeekFrom::End(-16)).map_err(|err| err.to_string())?;
    let mut footer = [0u8; 16];
    file.read_exact(&mut footer).map_err(|err| err.to_string())?;
    if &footer[..8] != MAGIC {
        return Err("Nexa.exe is missing its runtime.".into());
    }
    let payload_len = u64::from_le_bytes(footer[8..16].try_into().unwrap());
    if payload_len == 0 || payload_len.saturating_add(16) > len {
        return Err("Nexa.exe runtime is damaged.".into());
    }
    let start = len - 16 - payload_len;
    file.seek(SeekFrom::Start(start)).map_err(|err| err.to_string())?;
    let mut payload = vec![0u8; payload_len as usize];
    file.read_exact(&mut payload).map_err(|err| err.to_string())?;
    Ok(payload)
}

fn extract_payload(payload: &[u8], root: &Path) -> Result<(), String> {
    let mut offset = 0usize;
    while offset + 2 <= payload.len() {
        let name_len = u16::from_le_bytes(payload[offset..offset + 2].try_into().unwrap()) as usize;
        offset += 2;
        if name_len == 0 {
            break;
        }
        if offset + name_len + 8 > payload.len() {
            return Err("Nexa.exe runtime is damaged.".into());
        }
        let name = std::str::from_utf8(&payload[offset..offset + name_len])
            .map_err(|_| "Nexa.exe runtime is damaged.".to_string())?;
        offset += name_len;
        let data_len = u64::from_le_bytes(payload[offset..offset + 8].try_into().unwrap()) as usize;
        offset += 8;
        if offset + data_len > payload.len() {
            return Err("Nexa.exe runtime is damaged.".into());
        }
        let dest = safe_join(root, name)?;
        if let Some(parent) = dest.parent() {
            fs::create_dir_all(parent).map_err(|err| err.to_string())?;
        }
        fs::write(&dest, &payload[offset..offset + data_len]).map_err(|err| err.to_string())?;
        offset += data_len;
    }
    Ok(())
}

fn prepare_runtime() -> Result<PathBuf, String> {
    let runtime = local_root().join("runtime").join(env!("CARGO_PKG_VERSION"));
    let marker = runtime.join(".complete");
    if marker.is_file() && runtime.join("node.exe").is_file() && runtime.join("server.cjs").is_file() {
        return Ok(runtime);
    }
    let exe = std::env::current_exe().map_err(|err| err.to_string())?;
    let payload = read_payload(&exe)?;
    if runtime.exists() {
        fs::remove_dir_all(&runtime).map_err(|err| err.to_string())?;
    }
    fs::create_dir_all(&runtime).map_err(|err| err.to_string())?;
    extract_payload(&payload, &runtime)?;
    fs::write(&marker, b"ok").map_err(|err| err.to_string())?;
    Ok(runtime)
}

fn start_server(runtime: &Path) -> Result<Child, String> {
    let node = runtime.join("node.exe");
    let server = runtime.join("server.cjs");
    if !node.is_file() || !server.is_file() {
        return Err("Nexa runtime is incomplete.".into());
    }
    let data = local_root().join("data");
    fs::create_dir_all(&data).map_err(|err| err.to_string())?;
    let exe = std::env::current_exe().map_err(|err| err.to_string())?;
    let mut cmd = Command::new(&node);
    cmd.arg(&server)
        .current_dir(runtime)
        .env("PORT", "4177")
        .env("NEXA_STATIC", runtime.join("ui"))
        .env("NEXA_DATA_DIR", &data)
        .env("NEXA_APP_VERSION", env!("CARGO_PKG_VERSION"))
        .env("NEXA_EXE_PATH", &exe)
        .env("NEXA_PARENT_PID", std::process::id().to_string())
        .stdin(Stdio::null())
        .stdout(Stdio::null());
    if let Ok(log) = File::create(data.join("server.log")) {
        cmd.stderr(Stdio::from(log));
    } else {
        cmd.stderr(Stdio::null());
    }
    #[cfg(target_os = "windows")]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x08000000);
    }
    cmd.spawn().map_err(|err| format!("Could not start Nexa: {err}"))
}

fn server_ready() -> bool {
    let Ok(mut stream) = std::net::TcpStream::connect_timeout(
        &"127.0.0.1:4177".parse().unwrap(),
        Duration::from_millis(400),
    ) else {
        return false;
    };
    let _ = stream.set_read_timeout(Some(Duration::from_millis(800)));
    let _ = stream.set_write_timeout(Some(Duration::from_millis(800)));
    if stream
        .write_all(b"GET /health HTTP/1.0\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
        .is_err()
    {
        return false;
    }
    let mut buf = [0u8; 512];
    let Ok(size) = stream.read(&mut buf) else {
        return false;
    };
    String::from_utf8_lossy(&buf[..size]).contains("200")
}

fn wait_for_server() -> Result<(), String> {
    let started = Instant::now();
    while started.elapsed() < Duration::from_secs(20) {
        if server_ready() {
            return Ok(());
        }
        std::thread::sleep(Duration::from_millis(150));
    }
    Err("The local Nexa server did not start.".into())
}

fn stop_server(slot: &Mutex<Option<Child>>) {
    if let Some(mut child) = slot.lock().ok().and_then(|mut guard| guard.take()) {
        let _ = child.kill();
        let _ = child.wait();
    }
}

#[tauri::command]
fn open_external(url: String) -> Result<(), String> {
    if !url.starts_with("https://discord.com/oauth2/authorize?") {
        return Err("Refusing to open that address.".into());
    }
    opener::open(url).map_err(|err| err.to_string())
}

#[tauri::command]
fn pick_folder() -> Option<String> {
    rfd::FileDialog::new()
        .set_title("Import a Fortnite build")
        .pick_folder()
        .map(|path| path.display().to_string())
}

fn main() {
    let runtime = match prepare_runtime() {
        Ok(dir) => dir,
        Err(err) => fail(&err),
    };
    let server = match start_server(&runtime) {
        Ok(child) => child,
        Err(err) => fail(&err),
    };
    let server = Arc::new(Mutex::new(Some(server)));
    if let Err(err) = wait_for_server() {
        stop_server(&server);
        fail(&err);
    }

    let on_exit = Arc::clone(&server);
    let app = tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![open_external, pick_folder])
        .setup(|app| {
            let icon = tauri::image::Image::from_bytes(include_bytes!("../icons/icon.ico"))?;
            tauri::WebviewWindowBuilder::new(
                app,
                "main",
                tauri::WebviewUrl::External("http://127.0.0.1:4177/".parse().unwrap()),
            )
            .title("Nexa")
            .inner_size(1280.0, 800.0)
            .min_inner_size(1100.0, 700.0)
            .resizable(true)
            .decorations(false)
            .center()
            .focused(true)
            .devtools(false)
            .icon(icon)?
            .initialization_script(INIT_SCRIPT)
            .build()?;
            Ok(())
        })
        .build(tauri::generate_context!())
        .unwrap_or_else(|err| fail(&err.to_string()));

    app.run(move |_app, event| {
        if let tauri::RunEvent::Exit = event {
            stop_server(&on_exit);
        }
    });
}

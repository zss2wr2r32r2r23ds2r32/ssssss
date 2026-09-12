import { spawn, type ChildProcessWithoutNullStreams } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { isWindows } from './windows-api'

const log = {
  warn: (message: string, extra?: Record<string, unknown>) => {
    console.warn(`[win32] ${message}`, extra ?? '')
  },
}

const HOST_SCRIPT = `
$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

public static class AvixNative {
  public const int INPUT_MOUSE = 0;
  public const int INPUT_KEYBOARD = 1;
  public const int KEYEVENTF_EXTENDEDKEY = 0x0001;
  public const int KEYEVENTF_KEYUP = 0x0002;
  public const int KEYEVENTF_SCANCODE = 0x0008;
  public const int MOUSEEVENTF_WHEEL = 0x0800;
  public const int CDS_TEST = 2;
  public const int CDS_FULLSCREEN = 4;
  public const int DISP_CHANGE_SUCCESSFUL = 0;
  public const int ENUM_CURRENT_SETTINGS = -1;
  public const int SW_SHOWMINNOACTIVE = 7;
  public const uint SWP_NOMOVE = 0x0002;
  public const uint SWP_NOSIZE = 0x0001;
  public const uint SWP_NOACTIVATE = 0x0010;
  public const int HWND_BOTTOM = 1;
  public const int DISPLAY_DEVICE_ATTACHED_TO_DESKTOP = 1;

  [StructLayout(LayoutKind.Sequential)]
  public struct MOUSEINPUT {
    public int dx; public int dy; public uint mouseData; public uint dwFlags; public uint time; public IntPtr dwExtraInfo;
  }
  [StructLayout(LayoutKind.Sequential)]
  public struct KEYBDINPUT {
    public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo;
  }
  [StructLayout(LayoutKind.Sequential)]
  public struct HARDWAREINPUT {
    public uint uMsg; public ushort wParamL; public ushort wParamH;
  }
  [StructLayout(LayoutKind.Explicit)]
  public struct INPUTUNION {
    [FieldOffset(0)] public MOUSEINPUT mi;
    [FieldOffset(0)] public KEYBDINPUT ki;
    [FieldOffset(0)] public HARDWAREINPUT hi;
  }
  [StructLayout(LayoutKind.Sequential)]
  public struct INPUT {
    public int type;
    public INPUTUNION u;
  }
  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
  public struct DEVMODE {
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string dmDeviceName;
    public short dmSpecVersion;
    public short dmDriverVersion;
    public short dmSize;
    public short dmDriverExtra;
    public int dmFields;
    public int dmPositionX;
    public int dmPositionY;
    public int dmDisplayOrientation;
    public int dmDisplayFixedOutput;
    public short dmColor;
    public short dmDuplex;
    public short dmYResolution;
    public short dmTTOption;
    public short dmCollate;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string dmFormName;
    public short dmLogPixels;
    public int dmBitsPerPel;
    public int dmPelsWidth;
    public int dmPelsHeight;
    public int dmDisplayFlags;
    public int dmDisplayFrequency;
    public int dmICMMethod;
    public int dmICMIntent;
    public int dmMediaType;
    public int dmDitherType;
    public int dmReserved1;
    public int dmReserved2;
    public int dmPanningWidth;
    public int dmPanningHeight;
  }
  [StructLayout(LayoutKind.Sequential)]
  public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
  public struct DISPLAY_DEVICE {
    public int cb;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string DeviceName;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string DeviceString;
    public int StateFlags;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string DeviceID;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string DeviceKey;
  }

  [DllImport("user32.dll", SetLastError = true)]
  public static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);
  [DllImport("user32.dll")]
  public static extern uint MapVirtualKey(uint uCode, uint uMapType);
  [DllImport("user32.dll")]
  public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")]
  public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)]
  public static extern int GetClassName(IntPtr hWnd, StringBuilder lpClassName, int nMaxCount);
  [DllImport("user32.dll")]
  public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
  [DllImport("user32.dll")]
  public static extern bool IsWindowVisible(IntPtr hWnd);
  [DllImport("user32.dll")]
  public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
  public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
  [DllImport("user32.dll")]
  public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")]
  public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);
  [DllImport("user32.dll")]
  public static extern bool EnumDisplaySettings(string deviceName, int iModeNum, ref DEVMODE lpDevMode);
  [DllImport("user32.dll")]
  public static extern int ChangeDisplaySettingsEx(string lpszDeviceName, ref DEVMODE lpDevMode, IntPtr hwnd, int dwflags, IntPtr lParam);
  [DllImport("user32.dll")]
  public static extern int ChangeDisplaySettingsEx(string lpszDeviceName, IntPtr lpDevMode, IntPtr hwnd, int dwflags, IntPtr lParam);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)]
  public static extern bool EnumDisplayDevices(string lpDevice, uint iDevNum, ref DISPLAY_DEVICE lpDisplayDevice, uint dwFlags);

  public static string SendVk(int vk, int down) {
    INPUT i = new INPUT();
    i.type = INPUT_KEYBOARD;
    i.u.ki.wVk = 0;
    i.u.ki.wScan = (ushort)MapVirtualKey((uint)vk, 0);
    i.u.ki.dwFlags = KEYEVENTF_SCANCODE | (down == 0 ? KEYEVENTF_KEYUP : 0);
    uint n = SendInput(1, new INPUT[] { i }, Marshal.SizeOf(typeof(INPUT)));
    return n > 0 ? "OK" : "FAIL";
  }

  public static string Mouse(int flags, int data) {
    INPUT i = new INPUT();
    i.type = INPUT_MOUSE;
    i.u.mi.mouseData = unchecked((uint)data);
    i.u.mi.dwFlags = (uint)flags;
    uint n = SendInput(1, new INPUT[] { i }, Marshal.SizeOf(typeof(INPUT)));
    return n > 0 ? "OK" : "FAIL";
  }

  public static string FocusInfo() {
    IntPtr hwnd = GetForegroundWindow();
    if (hwnd == IntPtr.Zero) return "0|0||";
    uint pid;
    GetWindowThreadProcessId(hwnd, out pid);
    StringBuilder sb = new StringBuilder(256);
    GetClassName(hwnd, sb, 256);
    string name = "";
    try {
      var p = System.Diagnostics.Process.GetProcessById((int)pid);
      name = p.ProcessName ?? "";
      p.Dispose();
    } catch {}
    return pid.ToString() + "|" + hwnd.ToInt64().ToString() + "|" + sb.ToString() + "|" + name;
  }

  static string PidsNamed(HashSet<string> names) {
    var ids = new List<string>();
    foreach (var p in System.Diagnostics.Process.GetProcesses()) {
      try {
        if (names.Contains(p.ProcessName)) ids.Add(p.Id.ToString());
      } catch {}
      try { p.Dispose(); } catch {}
    }
    return string.Join(",", ids.ToArray());
  }

  public static string GamePids() {
    return PidsNamed(new HashSet<string>(StringComparer.OrdinalIgnoreCase) {
      "FortniteClient-Win64-Shipping",
      "FortniteClient-Win64-Shipping_EAC_EOS",
      "FortniteClient-Win64-Shipping_EAC",
      "FortniteClient-Win64-Shipping_BE"
    });
  }

  public static string HelperPids() {
    return PidsNamed(new HashSet<string>(StringComparer.OrdinalIgnoreCase) {
      "FortniteLauncher", "Fortnite", "FortniteBootstrapper"
    });
  }

  public static string EpicPids() {
    return PidsNamed(new HashSet<string>(StringComparer.OrdinalIgnoreCase) {
      "EpicGamesLauncher", "EpicGamesLauncher-Win64-Shipping"
    });
  }

  public static string FortniteRect() {
    IntPtr found = IntPtr.Zero;
    EnumWindows((h, l) => {
      if (!IsWindowVisible(h)) return true;
      uint pid;
      GetWindowThreadProcessId(h, out pid);
      try {
        var p = System.Diagnostics.Process.GetProcessById((int)pid);
        string name = p.ProcessName;
        p.Dispose();
        StringBuilder sb = new StringBuilder(256);
        GetClassName(h, sb, 256);
        string cls = sb.ToString();
        bool shipping = name.IndexOf("FortniteClient-Win64-Shipping", StringComparison.OrdinalIgnoreCase) >= 0
          && name.IndexOf("EAC", StringComparison.OrdinalIgnoreCase) < 0
          && name.IndexOf("_BE", StringComparison.OrdinalIgnoreCase) < 0;
        if (shipping && (cls == "UnrealWindow" || cls.IndexOf("Unreal", StringComparison.OrdinalIgnoreCase) >= 0 || cls.Length == 0)) {
          found = h;
          return false;
        }
      } catch {}
      return true;
    }, IntPtr.Zero);
    if (found == IntPtr.Zero) return "";
    RECT r;
    if (!GetWindowRect(found, out r)) return "";
    return r.Left + "," + r.Top + "," + r.Right + "," + r.Bottom;
  }

  public static string HideEpic() {
    int n = 0;
    EnumWindows((h, l) => {
      if (!IsWindowVisible(h)) return true;
      uint pid;
      GetWindowThreadProcessId(h, out pid);
      try {
        var p = System.Diagnostics.Process.GetProcessById((int)pid);
        string name = p.ProcessName;
        p.Dispose();
        if (name.IndexOf("EpicGamesLauncher", StringComparison.OrdinalIgnoreCase) >= 0) {
          ShowWindow(h, SW_SHOWMINNOACTIVE);
          SetWindowPos(h, new IntPtr(HWND_BOTTOM), 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE);
          n++;
        }
      } catch {}
      return true;
    }, IntPtr.Zero);
    return n.ToString();
  }

  public static string ListModes(string device) {
    var outp = new List<string>();
    int i = 0;
    while (true) {
      DEVMODE dm = new DEVMODE();
      dm.dmSize = (short)Marshal.SizeOf(typeof(DEVMODE));
      if (!EnumDisplaySettings(string.IsNullOrEmpty(device) ? null : device, i, ref dm)) break;
      outp.Add(dm.dmPelsWidth + "x" + dm.dmPelsHeight + "@" + dm.dmDisplayFrequency);
      i++;
      if (i > 400) break;
    }
    return string.Join(";", outp.ToArray());
  }

  public static string CurrentMode(string device) {
    DEVMODE dm = new DEVMODE();
    dm.dmSize = (short)Marshal.SizeOf(typeof(DEVMODE));
    if (!EnumDisplaySettings(string.IsNullOrEmpty(device) ? null : device, ENUM_CURRENT_SETTINGS, ref dm)) return "";
    return dm.dmPelsWidth + "x" + dm.dmPelsHeight + "@" + dm.dmDisplayFrequency + "|" + (dm.dmDeviceName ?? "");
  }

  public static string ChangeMode(string device, int w, int h, int freq) {
    DEVMODE cur = new DEVMODE();
    cur.dmSize = (short)Marshal.SizeOf(typeof(DEVMODE));
    string dev = string.IsNullOrEmpty(device) ? null : device;
    if (!EnumDisplaySettings(dev, ENUM_CURRENT_SETTINGS, ref cur)) return "ENUM_FAIL";
    DEVMODE dm = cur;
    dm.dmSize = (short)Marshal.SizeOf(typeof(DEVMODE));
    dm.dmPelsWidth = w;
    dm.dmPelsHeight = h;
    dm.dmFields = 0x00080000 | 0x00100000;
    if (freq > 0) {
      dm.dmDisplayFrequency = freq;
      dm.dmFields |= 0x00400000;
    }
    int test = ChangeDisplaySettingsEx(dev, ref dm, IntPtr.Zero, CDS_TEST, IntPtr.Zero);
    if (test != DISP_CHANGE_SUCCESSFUL) return "TEST_" + test;
    int apply = ChangeDisplaySettingsEx(dev, ref dm, IntPtr.Zero, CDS_FULLSCREEN, IntPtr.Zero);
    return apply == DISP_CHANGE_SUCCESSFUL ? "OK" : "APPLY_" + apply;
  }

  public static string RestoreMode(string device) {
    string dev = string.IsNullOrEmpty(device) ? null : device;
    int r = ChangeDisplaySettingsEx(dev, IntPtr.Zero, IntPtr.Zero, 0, IntPtr.Zero);
    return r == DISP_CHANGE_SUCCESSFUL ? "OK" : "RESTORE_" + r;
  }

  public static string PrimaryDevice() {
    DISPLAY_DEVICE d = new DISPLAY_DEVICE();
    d.cb = Marshal.SizeOf(typeof(DISPLAY_DEVICE));
    if (!EnumDisplayDevices(null, 0, ref d, 0)) return "";
    return d.DeviceName ?? "";
  }

  public static string DeviceAt(int x, int y) {
    for (uint i = 0; i < 16; i++) {
      DISPLAY_DEVICE d = new DISPLAY_DEVICE();
      d.cb = Marshal.SizeOf(typeof(DISPLAY_DEVICE));
      if (!EnumDisplayDevices(null, i, ref d, 0)) break;
      if ((d.StateFlags & DISPLAY_DEVICE_ATTACHED_TO_DESKTOP) == 0) continue;
      DEVMODE dm = new DEVMODE();
      dm.dmSize = (short)Marshal.SizeOf(typeof(DEVMODE));
      if (!EnumDisplaySettings(d.DeviceName, ENUM_CURRENT_SETTINGS, ref dm)) continue;
      int left = dm.dmPositionX;
      int top = dm.dmPositionY;
      int right = left + dm.dmPelsWidth;
      int bottom = top + dm.dmPelsHeight;
      if (x >= left && x < right && y >= top && y < bottom) return d.DeviceName ?? "";
    }
    return PrimaryDevice();
  }
}
"@
while ($true) {
  $line = [Console]::In.ReadLine()
  if ($null -eq $line) { break }
  if ($line -eq 'QUIT') { break }
  try {
    $parts = $line.Split('|')
    $op = $parts[0]
    $out = ''
    switch ($op) {
      'PING' { $out = 'PONG' }
      'KEY' { $out = [AvixNative]::SendVk([int]$parts[1], [int]$parts[2]) }
      'MOUSE' { $out = [AvixNative]::Mouse([int]$parts[1], [int]$parts[2]) }
      'FOCUS' { $out = [AvixNative]::FocusInfo() }
      'GAMEPIDS' { $out = [AvixNative]::GamePids() }
      'HELPERPIDS' { $out = [AvixNative]::HelperPids() }
      'EPICPIDS' { $out = [AvixNative]::EpicPids() }
      'FNRECT' { $out = [AvixNative]::FortniteRect() }
      'HIDEEPIC' { $out = [AvixNative]::HideEpic() }
      'MODES' { $out = [AvixNative]::ListModes($(if ($parts.Length -gt 1) { $parts[1] } else { '' })) }
      'CURRENT' { $out = [AvixNative]::CurrentMode($(if ($parts.Length -gt 1) { $parts[1] } else { '' })) }
      'DISPLAY' { $out = [AvixNative]::ChangeMode($(if ($parts.Length -gt 1) { $parts[1] } else { '' }), [int]$parts[2], [int]$parts[3], [int]$parts[4]) }
      'RESTORE' { $out = [AvixNative]::RestoreMode($(if ($parts.Length -gt 1) { $parts[1] } else { '' })) }
      'DEVICE' { $out = [AvixNative]::PrimaryDevice() }
      'DEVICEAT' { $out = [AvixNative]::DeviceAt([int]$parts[1], [int]$parts[2]) }
      default { $out = 'UNKNOWN' }
    }
    [Console]::Out.WriteLine('OK ' + $out)
  } catch {
    [Console]::Out.WriteLine('ERR ' + $_.Exception.Message)
  }
}
`

type Pending = { resolve: (v: string) => void; reject: (e: Error) => void }

function parsePids(raw: string): number[] {
  return raw
    .split(',')
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isFinite(n) && n > 0)
}

class Win32Host {
  private child: ChildProcessWithoutNullStreams | null = null
  private queue: Pending[] = []
  private buffer = ''
  private starting: Promise<void> | null = null

  async ensure(): Promise<boolean> {
    if (!isWindows) return false
    if (this.child && !this.child.killed) return true
    if (this.starting) {
      await this.starting
      return Boolean(this.child)
    }
    this.starting = this.spawnHost()
    try {
      await this.starting
    } finally {
      this.starting = null
    }
    return Boolean(this.child)
  }

  private spawnHost(): Promise<void> {
    return new Promise((resolve) => {
      try {
        const scriptPath = join(tmpdir(), 'avix-win32-host.ps1')
        writeFileSync(scriptPath, HOST_SCRIPT, 'utf8')
        const child = spawn(
          'powershell.exe',
          ['-NoProfile', '-STA', '-ExecutionPolicy', 'Bypass', '-File', scriptPath],
          { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] },
        )
        this.child = child
        child.stdout.setEncoding('utf8')
        child.stderr.setEncoding('utf8')
        child.stdout.on('data', (chunk: string) => {
          this.buffer += chunk
          let idx = this.buffer.indexOf('\n')
          while (idx >= 0) {
            const line = this.buffer.slice(0, idx).replace(/\r$/, '')
            this.buffer = this.buffer.slice(idx + 1)
            if (line) this.settle(line)
            idx = this.buffer.indexOf('\n')
          }
        })
        child.stderr.on('data', (chunk: string) => {
          const text = String(chunk).trim()
          if (text) log.warn('host stderr', { text: text.slice(0, 400) })
        })
        child.on('exit', () => {
          this.child = null
          const leftover = this.queue.splice(0)
          for (const p of leftover) p.reject(new Error('win32 host exited'))
        })
        const boot: Pending = {
          resolve: () => resolve(),
          reject: () => resolve(),
        }
        const timer = setTimeout(() => resolve(), 8000)
        this.queue.push({
          resolve: (v) => {
            clearTimeout(timer)
            boot.resolve(v)
          },
          reject: (e) => {
            clearTimeout(timer)
            boot.reject(e)
          },
        })
        child.stdin.write('PING\n')
      } catch (error) {
        log.warn('win32 host spawn failed', { error: String(error) })
        this.child = null
        resolve()
      }
    })
  }

  private settle(line: string): void {
    const pending = this.queue.shift()
    if (!pending) return
    if (line.startsWith('ERR ')) pending.reject(new Error(line.slice(4)))
    else pending.resolve(line.startsWith('OK ') ? line.slice(3) : line)
  }

  async request(line: string, timeoutMs = 4000): Promise<string> {
    if (!(await this.ensure()) || !this.child) throw new Error('win32 host unavailable')
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        const i = this.queue.indexOf(entry)
        if (i >= 0) this.queue.splice(i, 1)
        reject(new Error('win32 host timeout'))
      }, timeoutMs)
      const entry: Pending = {
        resolve: (v) => {
          clearTimeout(timer)
          resolve(v)
        },
        reject: (e) => {
          clearTimeout(timer)
          reject(e)
        },
      }
      this.queue.push(entry)
      this.child!.stdin.write(`${line}\n`)
    })
  }

  dispose(): void {
    try {
      this.child?.stdin.write('QUIT\n')
    } catch {
      /* ignore */
    }
    this.child?.kill()
    this.child = null
  }
}

const host = new Win32Host()

export async function hostSendKey(vk: number, down: boolean): Promise<void> {
  await host.request(`KEY|${vk}|${down ? 1 : 0}`, 1500)
}

export async function hostMouse(flags: number, data = 0): Promise<void> {
  await host.request(`MOUSE|${flags}|${data}`, 1500)
}

export async function hostWheel(delta: number): Promise<void> {
  await hostMouse(0x0800, delta)
}

export async function hostClick(button: 'left' | 'right' | 'middle' = 'left'): Promise<void> {
  const down = button === 'right' ? 0x0008 : button === 'middle' ? 0x0020 : 0x0002
  const up = button === 'right' ? 0x0010 : button === 'middle' ? 0x0040 : 0x0004
  await hostMouse(down)
  await hostMouse(up)
}

export async function hostFocus(): Promise<{ pid: number; hwnd: string; className: string; processName: string }> {
  const raw = await host.request('FOCUS', 1500)
  const [pid, hwnd, className, processName] = raw.split('|')
  return {
    pid: Number(pid) || 0,
    hwnd: hwnd || '0',
    className: className || '',
    processName: processName || '',
  }
}

export async function hostGamePids(): Promise<number[]> {
  return parsePids(await host.request('GAMEPIDS', 2500))
}

export async function hostHelperPids(): Promise<number[]> {
  return parsePids(await host.request('HELPERPIDS', 2500))
}

export async function hostEpicPids(): Promise<number[]> {
  return parsePids(await host.request('EPICPIDS', 2500))
}

export async function hostFortniteRect(): Promise<{ left: number; top: number; right: number; bottom: number } | null> {
  const raw = await host.request('FNRECT', 2500)
  if (!raw || !raw.includes(',')) return null
  const [left, top, right, bottom] = raw.split(',').map(Number)
  if (![left, top, right, bottom].every((n) => Number.isFinite(n))) return null
  return { left, top, right, bottom }
}

export async function hostHideEpic(): Promise<void> {
  try {
    await host.request('HIDEEPIC', 2500)
  } catch {
    /* optional */
  }
}

export async function hostListModes(device = ''): Promise<Array<{ width: number; height: number; freq: number }>> {
  const raw = await host.request(`MODES|${device}`, 4000)
  if (!raw) return []
  return raw
    .split(';')
    .map((part) => {
      const m = part.match(/^(\d+)x(\d+)@(\d+)$/)
      if (!m) return null
      return { width: Number(m[1]), height: Number(m[2]), freq: Number(m[3]) }
    })
    .filter((x): x is { width: number; height: number; freq: number } => Boolean(x))
}

export async function hostCurrentMode(
  device = '',
): Promise<{ width: number; height: number; freq: number; device: string } | null> {
  const raw = await host.request(`CURRENT|${device}`, 2500)
  if (!raw.includes('x')) return null
  const [wh, deviceName] = raw.split('|')
  const m = wh.match(/^(\d+)x(\d+)@(\d+)$/)
  if (!m) return null
  return { width: Number(m[1]), height: Number(m[2]), freq: Number(m[3]), device: deviceName || '' }
}

export async function hostChangeDisplay(device: string, width: number, height: number, freq: number): Promise<string> {
  return host.request(`DISPLAY|${device}|${width}|${height}|${freq}`, 6000)
}

export async function hostRestoreDisplay(device = ''): Promise<string> {
  return host.request(`RESTORE|${device}`, 4000)
}

export async function hostDeviceAt(x: number, y: number): Promise<string> {
  return host.request(`DEVICEAT|${x}|${y}`, 2500)
}

export function disposeWin32Host(): void {
  host.dispose()
}

$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

public static class AvixNative {
  public const int INPUT_MOUSE = 0;
  public const int INPUT_KEYBOARD = 1;
  public const int KEYEVENTF_KEYUP = 0x0002;
  public const int KEYEVENTF_SCANCODE = 0x0008;
  public const int MOUSEEVENTF_WHEEL = 0x0800;
  public const int SW_SHOWMINNOACTIVE = 7;
  public const uint SWP_NOMOVE = 0x0002;
  public const uint SWP_NOSIZE = 0x0001;
  public const uint SWP_NOACTIVATE = 0x0010;
  public const int HWND_BOTTOM = 1;

  [StructLayout(LayoutKind.Sequential)]
  public struct MOUSEINPUT {
    public int dx; public int dy; public uint mouseData; public uint dwFlags; public uint time; public IntPtr dwExtraInfo;
  }
  [StructLayout(LayoutKind.Sequential)]
  public struct KEYBDINPUT {
    public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo;
  }
  [StructLayout(LayoutKind.Sequential)]
  public struct HARDWAREINPUT { public uint uMsg; public ushort wParamL; public ushort wParamH; }
  [StructLayout(LayoutKind.Explicit)]
  public struct INPUTUNION {
    [FieldOffset(0)] public MOUSEINPUT mi;
    [FieldOffset(0)] public KEYBDINPUT ki;
    [FieldOffset(0)] public HARDWAREINPUT hi;
  }
  [StructLayout(LayoutKind.Sequential)]
  public struct INPUT { public int type; public INPUTUNION u; }
  [StructLayout(LayoutKind.Sequential)]
  public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }

  [DllImport("user32.dll", SetLastError = true)] public static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);
  [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint uCode, uint uMapType);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetClassName(IntPtr hWnd, StringBuilder lpClassName, int nMaxCount);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
  public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);

  public static string SendVk(int vk, int down) {
    INPUT i = new INPUT();
    i.type = INPUT_KEYBOARD;
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
        bool shipping = name.IndexOf("FortniteClient-Win64-Shipping", StringComparison.OrdinalIgnoreCase) >= 0
          && name.IndexOf("EAC", StringComparison.OrdinalIgnoreCase) < 0
          && name.IndexOf("_BE", StringComparison.OrdinalIgnoreCase) < 0;
        if (shipping) { found = h; return false; }
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
      'FNRECT' { $out = [AvixNative]::FortniteRect() }
      'HIDEEPIC' { $out = [AvixNative]::HideEpic() }
      default { $out = 'UNKNOWN' }
    }
    [Console]::Out.WriteLine('OK ' + $out)
  } catch {
    [Console]::Out.WriteLine('ERR ' + $_.Exception.Message)
  }
}

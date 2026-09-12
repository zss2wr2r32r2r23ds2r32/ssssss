param(
  [Parameter(Position = 0, Mandatory = $true)][string]$Op,
  [Parameter(Position = 1)][string]$A = '',
  [Parameter(Position = 2)][string]$B = '',
  [Parameter(Position = 3)][string]$C = '',
  [Parameter(Position = 4)][string]$D = ''
)
$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

public static class AvixDisplay {
  public const int CDS_TEST = 2;
  public const int CDS_FULLSCREEN = 4;
  public const int DISP_CHANGE_SUCCESSFUL = 0;
  public const int ENUM_CURRENT_SETTINGS = -1;
  public const int DISPLAY_DEVICE_ATTACHED_TO_DESKTOP = 1;
  public const int QDC_ONLY_ACTIVE_PATHS = 2;
  public const uint SDC_APPLY = 0x00000080;
  public const uint SDC_USE_SUPPLIED_DISPLAY_CONFIG = 0x00000020;
  public const uint SDC_ALLOW_CHANGES = 0x00000400;
  public const uint SDC_SAVE_TO_DATABASE = 0x00000200;
  public const int DISPLAYCONFIG_SCALING_STRETCHED = 5;
  public const int DISPLAYCONFIG_SCALING_IDENTITY = 1;

  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
  public struct DEVMODE {
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string dmDeviceName;
    public short dmSpecVersion, dmDriverVersion, dmSize, dmDriverExtra;
    public int dmFields, dmPositionX, dmPositionY, dmDisplayOrientation, dmDisplayFixedOutput;
    public short dmColor, dmDuplex, dmYResolution, dmTTOption, dmCollate;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string dmFormName;
    public short dmLogPixels;
    public int dmBitsPerPel, dmPelsWidth, dmPelsHeight, dmDisplayFlags, dmDisplayFrequency;
    public int dmICMMethod, dmICMIntent, dmMediaType, dmDitherType, dmReserved1, dmReserved2, dmPanningWidth, dmPanningHeight;
  }
  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
  public struct DISPLAY_DEVICE {
    public int cb;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string DeviceName;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string DeviceString;
    public int StateFlags;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string DeviceID;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string DeviceKey;
  }

  [DllImport("user32.dll")] public static extern bool EnumDisplaySettings(string deviceName, int iModeNum, ref DEVMODE lpDevMode);
  [DllImport("user32.dll")] public static extern int ChangeDisplaySettingsEx(string lpszDeviceName, ref DEVMODE lpDevMode, IntPtr hwnd, int dwflags, IntPtr lParam);
  [DllImport("user32.dll")] public static extern int ChangeDisplaySettingsEx(string lpszDeviceName, IntPtr lpDevMode, IntPtr hwnd, int dwflags, IntPtr lParam);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern bool EnumDisplayDevices(string lpDevice, uint iDevNum, ref DISPLAY_DEVICE lpDisplayDevice, uint dwFlags);
  [DllImport("nvapi64.dll", EntryPoint = "nvapi_QueryInterface", CallingConvention = CallingConvention.Cdecl)]
  public static extern IntPtr NvQuery(uint id);

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

  public static string DeviceAt(int x, int y) {
    for (uint i = 0; i < 16; i++) {
      DISPLAY_DEVICE d = new DISPLAY_DEVICE();
      d.cb = Marshal.SizeOf(typeof(DISPLAY_DEVICE));
      if (!EnumDisplayDevices(null, i, ref d, 0)) break;
      if ((d.StateFlags & DISPLAY_DEVICE_ATTACHED_TO_DESKTOP) == 0) continue;
      DEVMODE dm = new DEVMODE();
      dm.dmSize = (short)Marshal.SizeOf(typeof(DEVMODE));
      if (!EnumDisplaySettings(d.DeviceName, ENUM_CURRENT_SETTINGS, ref dm)) continue;
      int left = dm.dmPositionX, top = dm.dmPositionY;
      if (x >= left && x < left + dm.dmPelsWidth && y >= top && y < top + dm.dmPelsHeight) return d.DeviceName ?? "";
    }
    DISPLAY_DEVICE p = new DISPLAY_DEVICE();
    p.cb = Marshal.SizeOf(typeof(DISPLAY_DEVICE));
    if (!EnumDisplayDevices(null, 0, ref p, 0)) return "";
    return p.DeviceName ?? "";
  }

  public static string NvidiaProbe() {
    try {
      IntPtr fn = NvQuery(0x0150E828);
      if (fn == IntPtr.Zero) return "MISSING";
      var init = (NvInit)Marshal.GetDelegateForFunctionPointer(fn, typeof(NvInit));
      int status = init();
      return status == 0 ? "OK" : "FAIL_" + status;
    } catch (DllNotFoundException) {
      return "MISSING";
    } catch (Exception ex) {
      return "ERR_" + ex.GetType().Name;
    }
  }

  private delegate int NvInit();
}
"@
try {
  $out = ''
  switch ($Op.ToUpperInvariant()) {
    'APPLY' { $out = [AvixDisplay]::ChangeMode($D, [int]$A, [int]$B, $(if ($C) { [int]$C } else { 0 })) }
    'RESTORE' { $out = [AvixDisplay]::RestoreMode($A) }
    'CURRENT' { $out = [AvixDisplay]::CurrentMode($A) }
    'MODES' { $out = [AvixDisplay]::ListModes($A) }
    'DEVICEAT' { $out = [AvixDisplay]::DeviceAt([int]$A, [int]$B) }
    'NVIDIA' { $out = [AvixDisplay]::NvidiaProbe() }
    default { $out = 'UNKNOWN' }
  }
  Write-Output $out
} catch {
  Write-Output ('ERR ' + $_.Exception.Message)
  exit 1
}

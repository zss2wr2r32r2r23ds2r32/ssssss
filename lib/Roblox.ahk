#Requires AutoHotkey v2.0
; Lightweight Roblox window helpers (no image search).

GetRobloxHWND() {
    if (hwnd := WinExist("Roblox ahk_exe RobloxPlayerBeta.exe"))
        return hwnd
    if (hwnd := WinExist("Roblox ahk_exe ApplicationFrameHost.exe"))
        return hwnd
    return 0
}

ActivateRoblox() {
    hwnd := GetRobloxHWND()
    if !hwnd
        return 0
    try {
        WinActivate "ahk_id " hwnd
        WinWaitActive "ahk_id " hwnd, , 2
        return 1
    } catch {
        return 0
    }
}

RequireRoblox() {
    if ActivateRoblox()
        return 1
    MsgBox "Roblox / Bee Swarm Simulator is not open.`nOpen the game, claim a hive, then press Start.", "Pine Pollen Macro", 0x30
    return 0
}

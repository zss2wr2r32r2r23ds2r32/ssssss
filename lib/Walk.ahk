#Requires AutoHotkey v2.0
; Legacy Natro-style walk timing: 4000ms per tile at 1 movespeed.

Walk(tiles, hasteCap := 0) {
    global MoveSpeedNum, running
    if !running
        return
    speed := MoveSpeedNum
    if !IsNumber(speed) || speed <= 0
        speed := 28
    HyperSleep(4000 / speed * tiles)
}

nm_Walk(tiles, MoveKey1, MoveKey2 := 0) {
    global running
    if !running
        return
    Send "{" MoveKey1 " down}" (MoveKey2 ? "{" MoveKey2 " down}" : "")
    Walk(tiles)
    Send "{" MoveKey1 " up}" (MoveKey2 ? "{" MoveKey2 " up}" : "")
}

nm_gotoramp() {
    global FwdKey, RightKey, HiveSlot
    nm_Walk(5, FwdKey)
    nm_Walk(9.2 * HiveSlot - 4, RightKey)
}

nm_gotocannon() {
    global RightKey, FwdKey, SC_Space
    Send "{" SC_Space " down}{" RightKey " down}"
    Sleep 100
    Send "{" SC_Space " up}"
    nm_Walk(2, RightKey)
    nm_Walk(1.5, FwdKey, RightKey)
    Sleep 200
}

ReleaseMovementKeys() {
    global FwdKey, LeftKey, BackKey, RightKey, SC_Space, SC_E
    Send "{" FwdKey " up}{" LeftKey " up}{" BackKey " up}{" RightKey " up}{" SC_Space " up}{" SC_E " up}{LButton up}"
}

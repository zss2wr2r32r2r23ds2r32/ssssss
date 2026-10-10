; Pine Tree field paths adapted from NatroMacro
; https://github.com/NatroTeam/NatroMacro  (GPL-3.0)
; gtf-pinetree.ahk / wf-pinetree.ahk

GoToPineTree() {
    global MoveMethod, RightKey, BackKey, FwdKey, LeftKey, RotRight, RotLeft, running
    if !running
        return
    if (MoveMethod = "Cannon") {
        nm_gotoramp()
        if !running
            return
        nm_gotocannon()
        if !running
            return
        Send "{e down}"
        HyperSleep(100)
        Send "{e up}{" RightKey " down}{" BackKey " down}"
        HyperSleep(925)
        Send "{space 2}"
        HyperSleep(4500)
        Send "{" BackKey " up}"
        HyperSleep(500)
        Send "{" RightKey " up}{space}{" RotLeft " 4}"
        Sleep 2000
    } else {
        nm_gotoramp()
        if !running
            return
        nm_Walk(67.5, BackKey, LeftKey)
        Send "{" RotRight " 4}"
        nm_Walk(31, FwdKey)
        nm_Walk(7.8, LeftKey)
        nm_Walk(10, BackKey)
        nm_Walk(5, RightKey)
        nm_Walk(1.5, FwdKey)
        nm_Walk(60, LeftKey)
        nm_Walk(3.75, RightKey)
        nm_Walk(38, FwdKey)
        nm_Walk(33, LeftKey, FwdKey)
        Sleep 200
    }
}

WalkFromPineTree() {
    global FwdKey, RightKey, LeftKey, BackKey, RotLeft, SC_Space, HiveSlot, running
    if !running
        return
    nm_Walk(31, FwdKey)
    nm_Walk(75, RightKey)
    Send "{" RotLeft " 4}"
    Sleep 50
    nm_Walk(20, FwdKey)
    nm_Walk(3, FwdKey, LeftKey)
    nm_Walk(18, FwdKey)
    nm_Walk(6, FwdKey, RightKey)
    nm_Walk(10, RightKey)
    nm_Walk(2, LeftKey)
    Send "{" FwdKey " down}"
    Walk(6)
    Send "{" SC_Space " down}"
    HyperSleep(200)
    Send "{" SC_Space " up}"
    Walk(108)
    Send "{" FwdKey " up}"
    if (HiveSlot = 3) {
        nm_Walk(2.7, BackKey)
    } else {
        nm_Walk(1.5, BackKey)
        nm_Walk(35, RightKey)
        nm_Walk(2.7, BackKey)
    }
}

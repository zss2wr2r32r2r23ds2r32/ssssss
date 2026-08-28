/*
Pine Pollen Macro
A Bee Swarm Simulator AutoHotkey v2 collector focused on Pine Tree field.

Path timing and Pine Tree routes are adapted from Natro Macro
(https://github.com/NatroTeam/NatroMacro), licensed under GNU GPL v3.0.
*/
#Requires AutoHotkey v2.0
#SingleInstance Force
#MaxThreads 255

#Include "%A_ScriptDir%\lib"
#Include "JSON.ahk"
#Include "HyperSleep.ahk"
#Include "nowUnix.ahk"
#Include "DurationFromSeconds.ahk"
#Include "License.ahk"
#Include "Walk.ahk"
#Include "Roblox.ahk"
#Include "%A_ScriptDir%\paths\pinetree.ahk"

SetWorkingDir A_ScriptDir
CoordMode "Mouse", "Screen"
SendMode "Event"
SetKeyDelay 20

; WASD + camera (Natro scan codes)
TCFBKey := FwdKey := "sc011"   ; w
TCLRKey := LeftKey := "sc01e"  ; a
AFCFBKey := BackKey := "sc01f" ; s
AFCLRKey := RightKey := "sc020" ; d
RotLeft := "sc033"   ; ,
RotRight := "sc034"  ; .
RotUp := "sc149"     ; PgUp
RotDown := "sc151"   ; PgDn
ZoomIn := "sc017"    ; i
ZoomOut := "sc018"   ; o
SC_E := "sc012"
SC_R := "sc013"
SC_Esc := "sc001"
SC_Enter := "sc01c"
SC_LShift := "sc02a"
SC_Space := "sc039"
SC_1 := "sc002"

MoveSpeedNum := 28
MoveMethod := "Walk"
HiveSlot := 3
HiveBees := 50
GatherMinutes := 10
PatternName := "CornerXSnake"
PatternSize := "M"
PatternReps := 3
PlaceSprinkler := 1
ConvertAfterGather := 1
HoldClick := 1

running := false
paused := false
cyclesCompleted := 0
licenseRecord := unset

DirCreate "settings"

licenseRecord := License.PromptAndActivate()
if !licenseRecord {
    ExitApp
}

configPath := A_WorkingDir "\settings\config.ini"
LoadConfig()

MainGui := Gui("+OwnDialogs", "Pine Pollen Macro")
MainGui.BackColor := "141414"
MainGui.SetFont("s10 cWhite", "Segoe UI")
MainGui.AddText("x16 y14 w360", "Pine Tree pollen collector")
MainGui.SetFont("s8 c888888", "Segoe UI")
roleLabel := licenseRecord.Has("role") ? licenseRecord["role"] : "user"
MainGui.AddText("x16 y34 w360", "License: " roleLabel "  |  F1 Start   F2 Pause   F3 Stop")

MainGui.SetFont("s9 cWhite", "Segoe UI")
MainGui.AddText("x16 y64", "Hive slot")
hiveEdit := MainGui.AddEdit("x100 y60 w50 Number", HiveSlot)
MainGui.AddText("x170 y64", "(1-6)")

MainGui.AddText("x16 y96", "Move speed")
speedEdit := MainGui.AddEdit("x100 y92 w50", MoveSpeedNum)
MainGui.AddText("x170 y96 w200", "Match in-game speed (no haste)")

MainGui.AddText("x16 y128", "Travel")
methodDDL := MainGui.AddDropDownList("x100 y124 w120", ["Walk", "Cannon"])
methodDDL.Text := MoveMethod

MainGui.AddText("x16 y160", "Gather min")
gatherEdit := MainGui.AddEdit("x100 y156 w50", GatherMinutes)

MainGui.AddText("x16 y192", "Pattern")
patternDDL := MainGui.AddDropDownList("x100 y188 w140", ["CornerXSnake", "Squares", "Snake", "Lines", "Stationary"])
patternDDL.Text := PatternName

MainGui.AddText("x250 y192", "Size")
sizeDDL := MainGui.AddDropDownList("x290 y188 w70", ["XS", "S", "M", "L", "XL"])
sizeDDL.Text := PatternSize

MainGui.AddText("x16 y224", "Reps")
repsEdit := MainGui.AddEdit("x100 y220 w50 Number", PatternReps)

sprinklerBox := MainGui.AddCheckbox("x16 y256 Checked" PlaceSprinkler, "Place sprinkler (1)")
convertBox := MainGui.AddCheckbox("x200 y256 Checked" ConvertAfterGather, "Convert at hive")
clickBox := MainGui.AddCheckbox("x16 y280 Checked" HoldClick, "Hold left click while gathering")

MainGui.SetFont("s9 cWhite", "Segoe UI")
startBtn := MainGui.AddButton("x16 y314 w90 h28", "Start (F1)")
pauseBtn := MainGui.AddButton("x116 y314 w90 h28", "Pause (F2)")
stopBtn := MainGui.AddButton("x216 y314 w90 h28", "Stop (F3)")
pauseBtn.Enabled := false
stopBtn.Enabled := false

MainGui.SetFont("s8 cBBBBBB", "Consolas")
statusBox := MainGui.AddEdit("x16 y356 w360 h150 ReadOnly -Wrap", "Ready. Open Bee Swarm Simulator, claim a hive, then press Start.`r`nTesting license key: admintest123")

startBtn.OnEvent("Click", StartMacro)
pauseBtn.OnEvent("Click", PauseMacro)
stopBtn.OnEvent("Click", StopMacro)
MainGui.OnEvent("Close", (*) => ExitApp())
MainGui.Show("w392 h524")

Hotkey "F1", StartMacro
Hotkey "F2", PauseMacro
Hotkey "F3", StopMacro

Log(msg) {
    global statusBox
    line := FormatTime(A_Now, "HH:mm:ss") "  " msg
    statusBox.Value := Trim(statusBox.Value "`r`n" line, "`r`n")
    try SendMessage(0x115, 7, 0, statusBox.Hwnd) ; WM_VSCROLL bottom
}

SaveConfigFromGui() {
    global HiveSlot, MoveSpeedNum, MoveMethod, GatherMinutes, PatternName, PatternSize, PatternReps
    global PlaceSprinkler, ConvertAfterGather, HoldClick
    global hiveEdit, speedEdit, methodDDL, gatherEdit, patternDDL, sizeDDL, repsEdit
    global sprinklerBox, convertBox, clickBox, configPath
    HiveSlot := Integer(hiveEdit.Value || 3)
    HiveSlot := Min(6, Max(1, HiveSlot))
    MoveSpeedNum := Number(speedEdit.Value || 28)
    if MoveSpeedNum <= 0
        MoveSpeedNum := 28
    MoveMethod := methodDDL.Text
    GatherMinutes := Number(gatherEdit.Value || 10)
    if GatherMinutes <= 0
        GatherMinutes := 10
    PatternName := patternDDL.Text
    PatternSize := sizeDDL.Text
    PatternReps := Integer(repsEdit.Value || 3)
    if PatternReps < 1
        PatternReps := 1
    PlaceSprinkler := sprinklerBox.Value
    ConvertAfterGather := convertBox.Value
    HoldClick := clickBox.Value
    IniWrite HiveSlot, configPath, "Settings", "HiveSlot"
    IniWrite MoveSpeedNum, configPath, "Settings", "MoveSpeedNum"
    IniWrite MoveMethod, configPath, "Settings", "MoveMethod"
    IniWrite GatherMinutes, configPath, "Settings", "GatherMinutes"
    IniWrite PatternName, configPath, "Settings", "PatternName"
    IniWrite PatternSize, configPath, "Settings", "PatternSize"
    IniWrite PatternReps, configPath, "Settings", "PatternReps"
    IniWrite PlaceSprinkler, configPath, "Settings", "PlaceSprinkler"
    IniWrite ConvertAfterGather, configPath, "Settings", "ConvertAfterGather"
    IniWrite HoldClick, configPath, "Settings", "HoldClick"
}

LoadConfig() {
    global HiveSlot, MoveSpeedNum, MoveMethod, GatherMinutes, PatternName, PatternSize, PatternReps
    global PlaceSprinkler, ConvertAfterGather, HoldClick, configPath
    if !FileExist(configPath)
        return
    HiveSlot := Integer(IniRead(configPath, "Settings", "HiveSlot", HiveSlot))
    MoveSpeedNum := Number(IniRead(configPath, "Settings", "MoveSpeedNum", MoveSpeedNum))
    MoveMethod := IniRead(configPath, "Settings", "MoveMethod", MoveMethod)
    GatherMinutes := Number(IniRead(configPath, "Settings", "GatherMinutes", GatherMinutes))
    PatternName := IniRead(configPath, "Settings", "PatternName", PatternName)
    PatternSize := IniRead(configPath, "Settings", "PatternSize", PatternSize)
    PatternReps := Integer(IniRead(configPath, "Settings", "PatternReps", PatternReps))
    PlaceSprinkler := Integer(IniRead(configPath, "Settings", "PlaceSprinkler", PlaceSprinkler))
    ConvertAfterGather := Integer(IniRead(configPath, "Settings", "ConvertAfterGather", ConvertAfterGather))
    HoldClick := Integer(IniRead(configPath, "Settings", "HoldClick", HoldClick))
}

WaitIfPaused() {
    global paused, running
    while paused && running
        Sleep 100
}

PatternScale(name) {
    switch name {
        case "XS": return 0.25
        case "S": return 0.5
        case "L": return 1.5
        case "XL": return 2
        default: return 1
    }
}

ResetCharacter() {
    global SC_Esc, SC_R, SC_Enter, ZoomOut
    Log("Resetting character")
    Send "{" SC_Esc "}{" SC_R "}{" SC_Enter "}"
    Sleep 8000
    Send "{" ZoomOut " 8}"
    Sleep 400
}

ConvertAtHive() {
    global SC_E
    Log("Converting pollen at hive")
    Send "{" SC_E " down}"
    Sleep 100
    Send "{" SC_E " up}"
    ; Wait for backpack to empty (timed; no image search in this lite build)
    Sleep 15000
}

PlaceFieldSprinkler() {
    global SC_1
    Log("Placing sprinkler")
    Send "{" SC_1 "}"
    Sleep 400
}

RunGatherPattern() {
    global PatternName, PatternSize, PatternReps, FwdKey, LeftKey, BackKey, RightKey
    size := PatternScale(PatternSize)
    reps := PatternReps
    name := PatternName
    Log("Gathering with " name " x" reps)
    if (name = "Stationary") {
        Sleep 10000
        return
    }
    loop reps {
        if (name = "Squares") {
            length := 5 * size + A_Index
            nm_Walk(length, FwdKey)
            nm_Walk(length, LeftKey)
            nm_Walk(length, BackKey)
            nm_Walk(length, RightKey)
        } else if (name = "Snake") {
            nm_Walk(11 * size, LeftKey)
            nm_Walk(1, FwdKey)
            nm_Walk(11 * size, RightKey)
            nm_Walk(1, FwdKey)
        } else if (name = "Lines") {
            nm_Walk(11 * size, FwdKey)
            nm_Walk(1, LeftKey)
            nm_Walk(11 * size, BackKey)
            nm_Walk(1, LeftKey)
        } else { ; CornerXSnake-style box, Pine Tree default
            nm_Walk(4 * size, LeftKey)
            nm_Walk(2 * size, FwdKey)
            nm_Walk(8 * size, RightKey)
            nm_Walk(2 * size, FwdKey)
            nm_Walk(8 * size, LeftKey)
            nm_Walk(8 * size, RightKey, BackKey)
            nm_Walk(8 * size, LeftKey)
        }
    }
}

GatherLoop() {
    global GatherMinutes, HoldClick, running, paused
    endAt := A_TickCount + GatherMinutes * 60000
    if HoldClick
        Send "{LButton down}"
    try {
        while running && A_TickCount < endAt {
            WaitIfPaused()
            if !running
                break
            RunGatherPattern()
        }
    } finally {
        Send "{LButton up}"
    }
}

MacroLoop() {
    global running, cyclesCompleted, ConvertAfterGather, PlaceSprinkler, MoveMethod
    while running {
        WaitIfPaused()
        if !running
            break
        if !ActivateRoblox() {
            Log("Roblox closed — stopping")
            break
        }
        ResetCharacter()
        WaitIfPaused()
        if ConvertAfterGather
            ConvertAtHive()
        WaitIfPaused()
        Log("Traveling to Pine Tree (" MoveMethod ")")
        GoToPineTree()
        WaitIfPaused()
        if PlaceSprinkler
            PlaceFieldSprinkler()
        WaitIfPaused()
        GatherLoop()
        WaitIfPaused()
        if !running
            break
        Log("Walking back from Pine Tree")
        WalkFromPineTree()
        WaitIfPaused()
        if ConvertAfterGather
            ConvertAtHive()
        cyclesCompleted += 1
        Log("Cycle " cyclesCompleted " complete")
    }
    ReleaseMovementKeys()
    running := false
    paused := false
    Log("Stopped")
    SetButtonsIdle()
}

SetButtonsIdle() {
    global startBtn, pauseBtn, stopBtn
    startBtn.Enabled := true
    pauseBtn.Enabled := false
    stopBtn.Enabled := false
}

StartMacro(*) {
    global running, paused
    if running {
        if paused {
            PauseMacro()
        }
        return
    }
    SaveConfigFromGui()
    if !RequireRoblox()
        return
    running := true
    paused := false
    startBtn.Enabled := false
    pauseBtn.Enabled := true
    stopBtn.Enabled := true
    Log("Started Pine Tree collection")
    SetTimer(MacroLoop, -1)
}

PauseMacro(*) {
    global running, paused
    if !running
        return
    paused := !paused
    if paused {
        ReleaseMovementKeys()
        Log("Paused")
        pauseBtn.Text := "Resume"
    } else {
        if !ActivateRoblox() {
            Log("Roblox not found")
            return
        }
        Log("Resumed")
        pauseBtn.Text := "Pause (F2)"
    }
}

StopMacro(*) {
    global running, paused
    if !running
        return
    running := false
    paused := false
    ReleaseMovementKeys()
    pauseBtn.Text := "Pause (F2)"
    Log("Stop requested")
}

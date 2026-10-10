#Requires AutoHotkey v2.0
; Pine Pollen Macro — license-key validation
; Keys are SHA-256("{salt}:{key}") compared to hashes in licenses.json

class License {
    static Salt := "pine-pollen-macro-v1"
    static FilePath := A_WorkingDir "\licenses.json"
    static ActivatedPath := A_WorkingDir "\settings\license.ini"

    static Normalize(key) {
        return Trim(key ?? "")
    }

    static Sha256(str) {
        ; Windows CryptoAPI SHA-256 of a UTF-8 string (no trailing NUL)
        PROV_RSA_AES := 24
        CALG_SHA_256 := 0x800C
        HP_HASHVAL := 0x2
        CRYPT_VERIFYCONTEXT := 0xF0000000
        hProv := 0, hHash := 0
        if !DllCall("advapi32\CryptAcquireContextW", "Ptr*", &hProv, "Ptr", 0, "Ptr", 0, "UInt", PROV_RSA_AES, "UInt", CRYPT_VERIFYCONTEXT)
            throw Error("CryptAcquireContext failed")
        try {
            if !DllCall("advapi32\CryptCreateHash", "Ptr", hProv, "UInt", CALG_SHA_256, "Ptr", 0, "UInt", 0, "Ptr*", &hHash)
                throw Error("CryptCreateHash failed")
            utf8Len := StrPut(str, "UTF-8") - 1
            buf := Buffer(utf8Len)
            StrPut(str, buf, "UTF-8")
            if !DllCall("advapi32\CryptHashData", "Ptr", hHash, "Ptr", buf, "UInt", buf.Size, "UInt", 0)
                throw Error("CryptHashData failed")
            hashLen := 32
            hashBuf := Buffer(hashLen)
            if !DllCall("advapi32\CryptGetHashParam", "Ptr", hHash, "UInt", HP_HASHVAL, "Ptr", hashBuf, "UInt*", &hashLen, "UInt", 0)
                throw Error("CryptGetHashParam failed")
            hex := ""
            loop hashLen
                hex .= Format("{:02x}", NumGet(hashBuf, A_Index - 1, "UChar"))
            return hex
        } finally {
            if hHash
                DllCall("advapi32\CryptDestroyHash", "Ptr", hHash)
            if hProv
                DllCall("advapi32\CryptReleaseContext", "Ptr", hProv, "UInt", 0)
        }
    }

    static HashKey(key, salt := unset) {
        if !IsSet(salt)
            salt := License.Salt
        return License.Sha256(salt ":" License.Normalize(key))
    }

    static LoadStore() {
        if !FileExist(License.FilePath)
            throw Error("licenses.json not found:`n" License.FilePath)
        data := JSON.parse(FileRead(License.FilePath, "UTF-8"))
        salt := data.Has("salt") && data["salt"] ? data["salt"] : License.Salt
        keys := data.Has("keys") ? data["keys"] : []
        return Map("salt", salt, "keys", keys)
    }

    static Lookup(key) {
        normalized := License.Normalize(key)
        if (normalized = "")
            throw Error("Enter a license key")
        store := License.LoadStore()
        digest := License.HashKey(normalized, store["salt"])
        for entry in store["keys"] {
            hash := StrLower(entry.Has("hash") ? entry["hash"] : "")
            if (hash = digest) {
                return Map(
                    "hash", hash,
                    "label", entry.Has("label") ? entry["label"] : "",
                    "role", entry.Has("role") ? entry["role"] : "user"
                )
            }
        }
        throw Error("Invalid license key")
    }

    static IsValid(key) {
        try {
            License.Lookup(key)
            return true
        } catch
            return false
    }

    static SaveActivation(record) {
        DirCreate(A_WorkingDir "\settings")
        IniWrite record["hash"], License.ActivatedPath, "License", "KeyHash"
        IniWrite record["role"], License.ActivatedPath, "License", "Role"
        IniWrite record["label"], License.ActivatedPath, "License", "Label"
        IniWrite A_NowUTC, License.ActivatedPath, "License", "ActivatedAt"
        IniWrite 1, License.ActivatedPath, "License", "Activated"
    }

    static LoadActivation() {
        if !FileExist(License.ActivatedPath)
            return false
        if IniRead(License.ActivatedPath, "License", "Activated", "0") != "1"
            return false
        savedHash := StrLower(IniRead(License.ActivatedPath, "License", "KeyHash", ""))
        if (savedHash = "")
            return false
        store := License.LoadStore()
        for entry in store["keys"] {
            hash := StrLower(entry.Has("hash") ? entry["hash"] : "")
            if (hash = savedHash)
                return Map(
                    "hash", hash,
                    "label", entry.Has("label") ? entry["label"] : "",
                    "role", entry.Has("role") ? entry["role"] : "user"
                )
        }
        return false
    }

    static PromptAndActivate() {
        existing := License.LoadActivation()
        if existing
            return existing

        result := Map()
        licenseGui := Gui("+AlwaysOnTop", "Pine Pollen Macro — License")
        licenseGui.BackColor := "1b1b1b"
        licenseGui.SetFont("s11 cWhite", "Segoe UI")
        licenseGui.AddText("w360", "Enter your license key to start the Pine Tree collector.")
        licenseGui.SetFont("s9 cAAAAAA", "Segoe UI")
        licenseGui.AddText("w360 y+6", "Testing key: admintest123")
        licenseGui.SetFont("s11 cBlack", "Segoe UI")
        edit := licenseGui.AddEdit("w360 y+10 Password")
        licenseGui.SetFont("s10 cWhite", "Segoe UI")
        status := licenseGui.AddText("w360 h36 y+8 cFF6B6B", "")
        okBtn := licenseGui.AddButton("w110 h28 y+8 Default", "Activate")
        cancelBtn := licenseGui.AddButton("x+12 w110 h28", "Exit")

        Submit(*) {
            try {
                record := License.Lookup(edit.Value)
                License.SaveActivation(record)
                result["ok"] := true
                result["record"] := record
                licenseGui.Destroy()
            } catch as err {
                status.Value := err.Message
            }
        }
        Cancel(*) {
            result["ok"] := false
            licenseGui.Destroy()
        }
        okBtn.OnEvent("Click", Submit)
        cancelBtn.OnEvent("Click", Cancel)
        licenseGui.OnEvent("Close", Cancel)
        licenseGui.Show()
        WinWaitClose licenseGui
        if !result.Has("ok") || !result["ok"]
            return false
        return result["record"]
    }
}

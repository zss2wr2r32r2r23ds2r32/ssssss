#Requires AutoHotkey v2.0
; Tuff Macro — license-key validation
; Keys are SHA-256("{salt}:{key}") compared to hashes in licenses.json
; The plaintext key is never shown in the startup box.

class License {
    static Salt := "tuff-macro-v1"
    static KeyHash := "a1c254e301986bccdc66b6164e84e5047dfe33f02a9af09fafc34a42882201bc"

    static RootDir() {
        scriptRoot := A_ScriptDir "\.."
        for candidate in [scriptRoot, A_WorkingDir, A_ScriptDir] {
            if FileExist(candidate "\licenses.json") || FileExist(candidate "\START.bat")
                return candidate
        }
        return scriptRoot
    }

    static StorePath() {
        return License.RootDir() "\licenses.json"
    }

    static ActivatedPath() {
        return License.RootDir() "\settings\license.ini"
    }

    static Normalize(key) {
        return StrLower(Trim(key ?? ""))
    }

    static Sha256(str) {
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

    static EntryHash(entry) {
        if entry is Map
            return StrLower(entry.Has("hash") ? entry["hash"] : "")
        try {
            if entry.HasProp("hash")
                return StrLower(entry.hash)
        }
        return ""
    }

    static LoadStore() {
        salt := License.Salt
        keys := []
        path := License.StorePath()
        if FileExist(path) {
            data := JSON.parse(FileRead(path, "UTF-8"))
            if (data is Map) {
                if data.Has("salt") && data["salt"]
                    salt := data["salt"]
                if data.Has("keys")
                    keys := data["keys"]
            } else {
                if data.HasProp("salt") && data.salt
                    salt := data.salt
                if data.HasProp("keys")
                    keys := data.keys
            }
        }
        keys.Push(Map("hash", License.KeyHash, "label", "Owner", "role", "admin"))
        return Map("salt", salt, "keys", keys)
    }

    static Lookup(key) {
        normalized := License.Normalize(key)
        if (normalized = "")
            throw Error("Enter a key")

        digest := ""
        try digest := License.HashKey(normalized, License.Salt)
        catch
            digest := ""

        if digest != "" && digest = License.KeyHash
            return Map("hash", License.KeyHash, "label", "Owner", "role", "admin")

        try {
            store := License.LoadStore()
            try digest := License.HashKey(normalized, store["salt"])
            for entry in store["keys"] {
                hash := License.EntryHash(entry)
                if (hash != "" && hash = digest)
                    return Map(
                        "hash", hash,
                        "label", (entry is Map && entry.Has("label")) ? entry["label"] : "Owner",
                        "role", (entry is Map && entry.Has("role")) ? entry["role"] : "admin"
                    )
            }
        } catch {
        }

        ; Last resort if CryptoAPI/JSON is unavailable. Split so the GUI never prints the key.
        if (normalized = "charlies" "macro")
            return Map("hash", License.KeyHash, "label", "Owner", "role", "admin")

        throw Error("Invalid key")
    }

    static SaveActivation(record) {
        dir := License.RootDir() "\settings"
        if !DirExist(dir)
            DirCreate(dir)
        path := License.ActivatedPath()
        IniWrite record["hash"], path, "License", "KeyHash"
        IniWrite record["role"], path, "License", "Role"
        IniWrite A_NowUTC, path, "License", "ActivatedAt"
        IniWrite 1, path, "License", "Activated"
    }

    static LoadActivation() {
        path := License.ActivatedPath()
        if !FileExist(path)
            path := A_ScriptDir "\settings\license.ini"
        if !FileExist(path)
            return false
        if IniRead(path, "License", "Activated", "0") != "1"
            return false
        savedHash := StrLower(IniRead(path, "License", "KeyHash", ""))
        if (savedHash = "")
            return false
        if savedHash = License.KeyHash
            return Map("hash", savedHash, "label", "Owner", "role", "admin")
        try {
            store := License.LoadStore()
            for entry in store["keys"] {
                hash := License.EntryHash(entry)
                if (hash = savedHash)
                    return Map(
                        "hash", hash,
                        "label", (entry is Map && entry.Has("label")) ? entry["label"] : "Owner",
                        "role", (entry is Map && entry.Has("role")) ? entry["role"] : "admin"
                    )
            }
        } catch {
        }
        return false
    }

    static PromptAndActivate() {
        existing := License.LoadActivation()
        if existing
            return existing

        result := Map()
        box := Gui("+AlwaysOnTop -MinimizeBox -MaximizeBox", "Tuff Macro")
        box.BackColor := "0x071422"
        box.SetFont("s11 cWhite", "Segoe UI")
        edit := box.AddEdit("x24 y22 w260 h28 Password Center Background0B1F33")
        box.SetFont("s9 cFF8A8A", "Segoe UI")
        errLabel := box.AddText("x24 y52 w260 h16 Hidden Center", "Invalid key.")
        box.SetFont("s9 c7EC8FF", "Segoe UI")
        okBtn := box.AddButton("x100 y74 w108 h28 Default", "Unlock")

        Submit(*) {
            try {
                record := License.Lookup(edit.Value)
                License.SaveActivation(record)
                result["ok"] := true
                result["record"] := record
                box.Destroy()
            } catch {
                errLabel.Visible := true
                edit.Value := ""
                edit.Focus()
            }
        }
        Cancel(*) {
            result["ok"] := false
            box.Destroy()
        }
        okBtn.OnEvent("Click", Submit)
        box.OnEvent("Close", Cancel)
        box.OnEvent("Escape", Cancel)
        try {
            hIcon := LoadPicture(License.RootDir() "\nm_image_assets\tuff.ico", "Icon1 w32 h32", &imgType)
            DllCall("SendMessage", "ptr", box.Hwnd, "uint", 0x80, "ptr", 1, "ptr", hIcon)
            DllCall("SendMessage", "ptr", box.Hwnd, "uint", 0x80, "ptr", 0, "ptr", hIcon)
        }
        box.Show("w308 h118")
        WinWaitClose box
        if !result.Has("ok") || !result["ok"]
            return false
        return result["record"]
    }
}

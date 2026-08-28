/*
 * Pine Pollen Macro — Windows launcher
 * License gate + official Bee Swarm Simulator Roblox deeplink.
 * Does not inject into Roblox. Uses ShellExecute only.
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <wincrypt.h>
#include <shellapi.h>
#include <stdio.h>
#include <string.h>

#pragma comment(lib, "advapi32")
#pragma comment(lib, "crypt32")
#pragma comment(lib, "shell32")
#pragma comment(lib, "user32")
#pragma comment(lib, "gdi32")
#pragma comment(lib, "comctl32")

#define BSS_PLACE_ID "1537690962"
#define BSS_DEEPLINK "roblox://experiences/start?placeId=1537690962"
#define BSS_WEB_START "https://www.roblox.com/games/start?placeId=1537690962"
#define BSS_WEB_PAGE "https://www.roblox.com/games/1537690962/Bee-Swarm-Simulator"

#define LICENSE_SALT "pine-pollen-macro-v1"
#define LICENSE_TEST_HASH "ae89aa89b4126018c97e89cb8fba7b3dd9bcae0fd57e8c2ff79f4558b00c36a4"

#define ID_EDIT_KEY 101
#define ID_BTN_ACTIVATE 102
#define ID_BTN_EXIT 103
#define ID_LBL_STATUS 104
#define ID_BTN_PLAY 201
#define ID_BTN_MACRO 202
#define ID_BTN_QUIT 203
#define ID_LBL_MAIN 204

static HWND g_keyEdit;
static HWND g_status;
static HWND g_licenseWnd;
static HWND g_mainWnd;
static char g_exeDir[MAX_PATH];
static int g_licensed = 0;

static int hex_nibble(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

static int sha256_hex(const char *text, char out_hex[65]) {
    HCRYPTPROV prov = 0;
    HCRYPTHASH hash = 0;
    BYTE digest[32];
    DWORD digest_len = 32;
    int ok = 0;

    if (!CryptAcquireContext(&prov, NULL, NULL, PROV_RSA_AES, CRYPT_VERIFYCONTEXT))
        return 0;
    if (!CryptCreateHash(prov, CALG_SHA_256, 0, 0, &hash))
        goto done;
    if (!CryptHashData(hash, (const BYTE *)text, (DWORD)strlen(text), 0))
        goto done;
    if (!CryptGetHashParam(hash, HP_HASHVAL, digest, &digest_len, 0))
        goto done;
    for (DWORD i = 0; i < digest_len; i++)
        sprintf(out_hex + i * 2, "%02x", digest[i]);
    out_hex[64] = 0;
    ok = 1;
done:
    if (hash) CryptDestroyHash(hash);
    if (prov) CryptReleaseContext(prov, 0);
    return ok;
}

static void trim_copy(const char *src, char *dst, size_t dst_size) {
    while (*src == ' ' || *src == '\t' || *src == '\r' || *src == '\n')
        src++;
    size_t n = strlen(src);
    while (n > 0 && (src[n - 1] == ' ' || src[n - 1] == '\t' || src[n - 1] == '\r' || src[n - 1] == '\n'))
        n--;
    if (n >= dst_size)
        n = dst_size - 1;
    memcpy(dst, src, n);
    dst[n] = 0;
}

static int hashes_equal(const char *a, const char *b) {
    if (strlen(a) != 64 || strlen(b) != 64)
        return 0;
    for (int i = 0; i < 64; i++) {
        int na = hex_nibble(a[i]);
        int nb = hex_nibble(b[i]);
        if (na < 0 || nb < 0 || na != nb)
            return 0;
    }
    return 1;
}

static int license_is_valid(const char *key) {
    char trimmed[256];
    char payload[320];
    char digest[65];
    trim_copy(key, trimmed, sizeof(trimmed));
    if (!trimmed[0])
        return 0;
    snprintf(payload, sizeof(payload), "%s:%s", LICENSE_SALT, trimmed);
    if (!sha256_hex(payload, digest))
        return 0;
    return hashes_equal(digest, LICENSE_TEST_HASH);
}

static void exe_dir(char *out, size_t out_size) {
    char path[MAX_PATH];
    GetModuleFileNameA(NULL, path, MAX_PATH);
    char *slash = strrchr(path, '\\');
    if (slash)
        *slash = 0;
    strncpy(out, path, out_size - 1);
    out[out_size - 1] = 0;
}

static int file_exists(const char *path) {
    DWORD attr = GetFileAttributesA(path);
    return attr != INVALID_FILE_ATTRIBUTES && !(attr & FILE_ATTRIBUTE_DIRECTORY);
}

static int launch_url(const char *url) {
    HINSTANCE rc = ShellExecuteA(NULL, "open", url, NULL, NULL, SW_SHOWNORMAL);
    return (INT_PTR)rc > 32;
}

static int open_bee_swarm(void) {
    if (launch_url(BSS_DEEPLINK))
        return 1;
    if (launch_url(BSS_WEB_START))
        return 1;
    return launch_url(BSS_WEB_PAGE);
}

static int start_ahk_macro(void) {
    char script[MAX_PATH];
    char cmd[MAX_PATH * 2];
    const char *ahk_candidates[] = {
        "AutoHotkey64.exe",
        "AutoHotkey.exe",
        "C:\\Program Files\\AutoHotkey\\v2\\AutoHotkey64.exe",
        "C:\\Program Files\\AutoHotkey\\v2\\AutoHotkey32.exe",
        "C:\\Program Files\\AutoHotkey\\AutoHotkey64.exe",
        NULL
    };
    snprintf(script, sizeof(script), "%s\\pine_macro.ahk", g_exeDir);
    if (!file_exists(script))
        return 0;
    for (int i = 0; ahk_candidates[i]; i++) {
        const char *ahk = ahk_candidates[i];
        char local[MAX_PATH];
        if (!strchr(ahk, '\\')) {
            snprintf(local, sizeof(local), "%s\\%s", g_exeDir, ahk);
            if (!file_exists(local))
                continue;
            ahk = local;
        } else if (!file_exists(ahk)) {
            continue;
        }
        snprintf(cmd, sizeof(cmd), "\"%s\" \"%s\"", ahk, script);
        STARTUPINFOA si;
        PROCESS_INFORMATION pi;
        ZeroMemory(&si, sizeof(si));
        si.cb = sizeof(si);
        ZeroMemory(&pi, sizeof(pi));
        if (CreateProcessA(NULL, cmd, NULL, NULL, FALSE, 0, NULL, g_exeDir, &si, &pi)) {
            CloseHandle(pi.hThread);
            CloseHandle(pi.hProcess);
            return 1;
        }
    }
    return 0;
}

static void set_status(HWND hwnd, const char *text) {
    if (hwnd)
        SetWindowTextA(hwnd, text);
}

static LRESULT CALLBACK LicenseProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_CREATE: {
        CreateWindowA("STATIC", "Pine Pollen Macro",
                      WS_CHILD | WS_VISIBLE, 20, 16, 360, 24, hwnd, NULL, NULL, NULL);
        CreateWindowA("STATIC", "Enter your license key, then the launcher opens Bee Swarm Simulator.",
                      WS_CHILD | WS_VISIBLE, 20, 44, 360, 36, hwnd, NULL, NULL, NULL);
        CreateWindowA("STATIC", "Testing key: admintest123",
                      WS_CHILD | WS_VISIBLE, 20, 84, 360, 18, hwnd, NULL, NULL, NULL);
        g_keyEdit = CreateWindowExA(WS_EX_CLIENTEDGE, "EDIT", "",
                                    WS_CHILD | WS_VISIBLE | ES_PASSWORD | ES_AUTOHSCROLL,
                                    20, 110, 360, 28, hwnd, (HMENU)ID_EDIT_KEY, NULL, NULL);
        CreateWindowA("BUTTON", "Activate & Open Game",
                      WS_CHILD | WS_VISIBLE | BS_DEFPUSHBUTTON,
                      20, 160, 180, 32, hwnd, (HMENU)ID_BTN_ACTIVATE, NULL, NULL);
        CreateWindowA("BUTTON", "Exit",
                      WS_CHILD | WS_VISIBLE,
                      214, 160, 80, 32, hwnd, (HMENU)ID_BTN_EXIT, NULL, NULL);
        g_status = CreateWindowA("STATIC", "",
                                 WS_CHILD | WS_VISIBLE, 20, 204, 360, 36, hwnd, (HMENU)ID_LBL_STATUS, NULL, NULL);
        SetFocus(g_keyEdit);
        return 0;
    }
    case WM_COMMAND:
        if (LOWORD(wParam) == ID_BTN_EXIT) {
            DestroyWindow(hwnd);
            return 0;
        }
        if (LOWORD(wParam) == ID_BTN_ACTIVATE) {
            char key[256];
            GetWindowTextA(g_keyEdit, key, sizeof(key));
            if (!license_is_valid(key)) {
                set_status(g_status, "Invalid license key.");
                return 0;
            }
            g_licensed = 1;
            ShowWindow(hwnd, SW_HIDE);
            ShowWindow(g_mainWnd, SW_SHOW);
            SetForegroundWindow(g_mainWnd);
            if (open_bee_swarm())
                set_status(GetDlgItem(g_mainWnd, ID_LBL_MAIN),
                           "Opened Bee Swarm Simulator. Claim a hive, then start the macro.");
            else
                set_status(GetDlgItem(g_mainWnd, ID_LBL_MAIN),
                           "Could not start Roblox. Install Roblox, then click Play again.");
            return 0;
        }
        return 0;
    case WM_CLOSE:
        DestroyWindow(hwnd);
        return 0;
    case WM_DESTROY:
        if (!g_licensed)
            PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcA(hwnd, msg, wParam, lParam);
}

static LRESULT CALLBACK MainProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_CREATE: {
        CreateWindowA("STATIC", "Pine Tree pollen collector",
                      WS_CHILD | WS_VISIBLE, 20, 16, 360, 24, hwnd, NULL, NULL, NULL);
        CreateWindowA("STATIC", "Official game link: Bee Swarm Simulator (place 1537690962)",
                      WS_CHILD | WS_VISIBLE, 20, 44, 360, 36, hwnd, NULL, NULL, NULL);
        CreateWindowA("BUTTON", "Play Bee Swarm Simulator",
                      WS_CHILD | WS_VISIBLE | BS_DEFPUSHBUTTON,
                      20, 90, 250, 36, hwnd, (HMENU)ID_BTN_PLAY, NULL, NULL);
        CreateWindowA("BUTTON", "Start Pine Macro",
                      WS_CHILD | WS_VISIBLE,
                      20, 136, 180, 32, hwnd, (HMENU)ID_BTN_MACRO, NULL, NULL);
        CreateWindowA("BUTTON", "Quit",
                      WS_CHILD | WS_VISIBLE,
                      214, 136, 80, 32, hwnd, (HMENU)ID_BTN_QUIT, NULL, NULL);
        CreateWindowA("STATIC", "License accepted. Click Play to open the Roblox game.",
                      WS_CHILD | WS_VISIBLE, 20, 184, 360, 50, hwnd, (HMENU)ID_LBL_MAIN, NULL, NULL);
        return 0;
    }
    case WM_COMMAND:
        if (LOWORD(wParam) == ID_BTN_QUIT) {
            DestroyWindow(hwnd);
            return 0;
        }
        if (LOWORD(wParam) == ID_BTN_PLAY) {
            if (open_bee_swarm())
                set_status(GetDlgItem(hwnd, ID_LBL_MAIN),
                           "Opened Bee Swarm Simulator (roblox:// place 1537690962).");
            else
                set_status(GetDlgItem(hwnd, ID_LBL_MAIN),
                           "Roblox did not open. Install Roblox from roblox.com and try again.");
            return 0;
        }
        if (LOWORD(wParam) == ID_BTN_MACRO) {
            if (start_ahk_macro())
                set_status(GetDlgItem(hwnd, ID_LBL_MAIN),
                           "Started pine_macro.ahk. Keep this folder next to the script.");
            else
                set_status(GetDlgItem(hwnd, ID_LBL_MAIN),
                           "Need AutoHotkey v2 and pine_macro.ahk in this folder. Game link still works.");
            return 0;
        }
        return 0;
    case WM_CLOSE:
        DestroyWindow(hwnd);
        return 0;
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcA(hwnd, msg, wParam, lParam);
}

int WINAPI WinMain(HINSTANCE inst, HINSTANCE prev, LPSTR cmd, int show) {
    (void)prev;
    (void)cmd;
    exe_dir(g_exeDir, sizeof(g_exeDir));

    WNDCLASSA lc = {0};
    lc.lpfnWndProc = LicenseProc;
    lc.hInstance = inst;
    lc.lpszClassName = "PineLicenseWnd";
    lc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    lc.hCursor = LoadCursor(NULL, IDC_ARROW);
    RegisterClassA(&lc);

    WNDCLASSA mc = {0};
    mc.lpfnWndProc = MainProc;
    mc.hInstance = inst;
    mc.lpszClassName = "PineMainWnd";
    mc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    mc.hCursor = LoadCursor(NULL, IDC_ARROW);
    RegisterClassA(&mc);

    g_licenseWnd = CreateWindowA("PineLicenseWnd", "Pine Pollen Macro — License",
                                 WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU,
                                 CW_USEDEFAULT, CW_USEDEFAULT, 420, 290,
                                 NULL, NULL, inst, NULL);
    g_mainWnd = CreateWindowA("PineMainWnd", "Pine Pollen Macro",
                              WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU,
                              CW_USEDEFAULT, CW_USEDEFAULT, 420, 290,
                              NULL, NULL, inst, NULL);

    ShowWindow(g_licenseWnd, show);
    UpdateWindow(g_licenseWnd);

    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }
    return (int)msg.wParam;
}

/*
 * Shir o Khorshid for Windows - main window, tray, system proxy integration.
 * GPLv3. See shir.h for the full notice.
 */

#include "ui.h"
#include "shir.h"
#include <stdlib.h>
#include <string.h>

#include <wininet.h>
#include <shlobj.h>
#include <knownfolders.h>
#include <regstr.h>

#define WM_TRAYICON        (WM_APP + 2)
#define IDT_PUMP           1
#define IDT_REPAINT        2

/* --------------------------------------------------------------- globals */

static UI        g_ui;
static Settings  g_settings;
static HINSTANCE g_inst;
static HWND      g_hwnd;
static int       g_start_minimized = 0;
static int       g_quit = 0;
static HICON     g_tray_current = NULL;
static int       g_tray_added = 0;
static int       g_was_connected = 0;
static HWND      g_upstream_dlg = NULL;

UINT WM_APP_NOTICE = 0;
HWND g_main_hwnd = NULL;

/* ------------------------------------------------------------ small utils */

static void do_uninstall(void);
static void warn_if_unconfigured(void);

static void msg_box(const wchar_t *text, const wchar_t *title)
{
    MessageBoxW(g_hwnd, text, title ? title : tr(S_APP_NAME),
                MB_OK | (i18n_is_persian() ? MB_RTLREADING : 0) | MB_TOPMOST);
}

static void save_settings(void)
{
    wchar_t path[MAX_PATH_W];
    settings_get_path(path, MAX_PATH_W);
    settings_save(&g_settings, path);
}

/* ------------------------------------------------------- Windows proxying */

static void apply_system_proxy(int enable, int http_port)
{
    HKEY key;
    LONG r;

    r = RegOpenKeyExW(HKEY_CURRENT_USER,
        L"Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
        0, KEY_SET_VALUE, &key);
    if (r != ERROR_SUCCESS) return;

    if (enable && http_port > 0) {
        wchar_t server[64];
        const wchar_t *bypass = L"localhost;127.*;10.*;172.16.*;192.168.*;<local>";
        DWORD one = 1;
        swprintf(server, 64, L"127.0.0.1:%d", http_port);
        RegSetValueExW(key, L"ProxyServer", 0, REG_SZ, (const BYTE *)server,
                       (DWORD)((wcslen(server) + 1) * sizeof(wchar_t)));
        RegSetValueExW(key, L"ProxyOverride", 0, REG_SZ, (const BYTE *)bypass,
                       (DWORD)((wcslen(bypass) + 1) * sizeof(wchar_t)));
        RegSetValueExW(key, L"ProxyEnable", 0, REG_DWORD,
                       (const BYTE *)&one, sizeof(one));
    } else {
        DWORD zero = 0;
        RegSetValueExW(key, L"ProxyEnable", 0, REG_DWORD,
                       (const BYTE *)&zero, sizeof(zero));
    }
    RegCloseKey(key);

    InternetSetOptionW(NULL, INTERNET_OPTION_SETTINGS_CHANGED, NULL, 0);
    InternetSetOptionW(NULL, INTERNET_OPTION_REFRESH, NULL, 0);
}

static void sync_system_proxy(void)
{
    TunnelStatus *st = tunnel_status();
    if (g_settings.tunnel_whole_device && g_settings.set_system_proxy &&
        st->state == TSTATE_CONNECTED && st->http_port > 0) {
        apply_system_proxy(1, st->http_port);
    } else {
        apply_system_proxy(0, 0);
    }
}

/* ------------------------------------------------------------- auto-start */

static void set_start_with_windows(int enable)
{
    HKEY key;
    LONG r = RegOpenKeyExW(HKEY_CURRENT_USER,
        L"Software\\Microsoft\\Windows\\CurrentVersion\\Run",
        0, KEY_SET_VALUE, &key);
    if (r != ERROR_SUCCESS) return;
    if (enable) {
        wchar_t exe[MAX_PATH_W], value[MAX_PATH_W + 32];
        GetModuleFileNameW(NULL, exe, MAX_PATH_W);
        swprintf(value, MAX_PATH_W + 31, L"\"%ls\" --minimized", exe);
        RegSetValueExW(key, L"ShirOKhorshid", 0, REG_SZ, (const BYTE *)value,
                       (DWORD)((wcslen(value) + 1) * sizeof(wchar_t)));
    } else {
        RegDeleteValueW(key, L"ShirOKhorshid");
    }
    RegCloseKey(key);
}

/* ------------------------------------------------------------------- tray */

static void tray_update(void)
{
    NOTIFYICONDATAW nid;
    TunnelStatus *st = tunnel_status();
    int connected = (st->state == TSTATE_CONNECTED);
    HICON icon = connected ? g_ui.tray_on : g_ui.tray_off;

    if (!g_tray_added) return;
    if (!icon) return;

    ZeroMemory(&nid, sizeof(nid));
    nid.cbSize = sizeof(nid);
    nid.hWnd = g_hwnd;
    nid.uID = 1;
    nid.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
    nid.uCallbackMessage = WM_TRAYICON;
    nid.hIcon = icon;
    wcsncpy(nid.szTip, tr(S_APP_NAME), 127);
    nid.szTip[127] = 0;
    Shell_NotifyIconW(NIM_MODIFY, &nid);
    g_tray_current = icon;
}

static void tray_add(void)
{
    NOTIFYICONDATAW nid;
    ZeroMemory(&nid, sizeof(nid));
    nid.cbSize = sizeof(nid);
    nid.hWnd = g_hwnd;
    nid.uID = 1;
    nid.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
    nid.uCallbackMessage = WM_TRAYICON;
    nid.hIcon = g_ui.tray_off ? g_ui.tray_off : g_ui.app_icon;
    wcsncpy(nid.szTip, tr(S_APP_NAME), 127);
    nid.szTip[127] = 0;
    if (Shell_NotifyIconW(NIM_ADD, &nid)) g_tray_added = 1;
}

static void tray_remove(void)
{
    NOTIFYICONDATAW nid;
    if (!g_tray_added) return;
    ZeroMemory(&nid, sizeof(nid));
    nid.cbSize = sizeof(nid);
    nid.hWnd = g_hwnd;
    nid.uID = 1;
    Shell_NotifyIconW(NIM_DELETE, &nid);
    g_tray_added = 0;
}

static void tray_balloon(const wchar_t *title, const wchar_t *text)
{
    NOTIFYICONDATAW nid;
    if (!g_tray_added) return;
    ZeroMemory(&nid, sizeof(nid));
    nid.cbSize = sizeof(nid);
    nid.hWnd = g_hwnd;
    nid.uID = 1;
    nid.uFlags = NIF_INFO;
    wcsncpy(nid.szInfoTitle, title, 63);
    nid.szInfoTitle[63] = 0;
    wcsncpy(nid.szInfo, text, 255);
    nid.szInfo[255] = 0;
    nid.dwInfoFlags = NIIF_INFO;
    Shell_NotifyIconW(NIM_MODIFY, &nid);
}

/* ------------------------------------------------------------- connection */

static void open_browser(const wchar_t *url)
{
    const wchar_t *target = (url && url[0]) ? url : L"https://psiphon.ca";
    ShellExecuteW(NULL, L"open", target, NULL, NULL, SW_SHOWNORMAL);
}

static void connect_tunnel(void)
{
    if (!tunnel_core_available()) {
        msg_box(tr(S_CORE_MISSING), tr(S_ERROR_TITLE));
        return;
    }
    if (tunnel_start(&g_settings)) {
        log_ring_add(0, L"connect requested");
        InvalidateRect(g_hwnd, NULL, FALSE);
    }
}

static void disconnect_tunnel(void)
{
    tunnel_stop();
    apply_system_proxy(0, 0);
    InvalidateRect(g_hwnd, NULL, FALSE);
}

static void toggle_connection(void)
{
    TunnelStatus *st = tunnel_status();
    if (st->state == TSTATE_STOPPED) connect_tunnel();
    else disconnect_tunnel();
}

/* ------------------------------------------------- upstream proxy dialog */

#define IDC_UP_HOST     101
#define IDC_UP_PORT     102
#define IDC_UP_USER     103
#define IDC_UP_PASS     104
#define IDC_UP_HTTPS    105
#define IDC_UP_OK       106
#define IDC_UP_CANCEL   107

static HWND g_ed_host, g_ed_port, g_ed_user, g_ed_pass, g_cb_https;

static LRESULT CALLBACK upstream_proc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp)
{
    /* Registered at runtime, so it cannot appear in the switch below. */
    if (WM_APP_NOTICE && msg == WM_APP_NOTICE) {
        TunnelStatus *st = tunnel_status();
        int connected = (st->state == TSTATE_CONNECTED);
        InvalidateRect(hwnd, NULL, FALSE);
        if (connected && !g_was_connected) {
            g_was_connected = 1;
            sync_system_proxy();
            tray_update();
            if (g_settings.notifications_sound)
                MessageBeep(MB_ICONINFORMATION);
            tray_balloon(tr(S_APP_NAME), tr(S_CONNECTED));
            if (g_settings.auto_open_homepage && tunnel_homepage()[0])
                open_browser(tunnel_homepage());
        } else if (!connected && g_was_connected) {
            g_was_connected = 0;
            sync_system_proxy();
            tray_update();
        }
        return 0;
    }

    switch (msg) {
    case WM_CREATE: {
        int w = 420, pad = 18, y = 18, lw = 150;
        HFONT f = g_ui.f_body;
        const StrId labels[4] = { S_UPSTREAM_HOST, S_UPSTREAM_PORT,
                                  S_UPSTREAM_USERNAME, S_UPSTREAM_PASSWORD };
        int i;
        HWND edits[4];
        wchar_t portbuf[32];
        DWORD style = WS_VISIBLE | WS_CHILD | WS_BORDER | ES_AUTOHSCROLL;
        DWORD rtl = i18n_is_persian() ? WS_EX_RTLREADING : 0;
        DWORD ltr = i18n_is_persian() ? (WS_EX_RIGHT | WS_EX_RTLREADING) : 0;

        (void)lp;
        for (i = 0; i < 4; i++) {
            HWND lbl = CreateWindowExW(rtl, L"STATIC", tr(labels[i]),
                WS_VISIBLE | WS_CHILD | (i18n_is_persian() ? SS_RIGHT : SS_LEFT),
                w - pad - lw, y + i * 38, lw, 22, hwnd, NULL, g_inst, NULL);
            SendMessageW(lbl, WM_SETFONT, (WPARAM)f, TRUE);
            edits[i] = CreateWindowExW(ltr, L"EDIT", L"", style,
                18, y + i * 38, w - pad * 2 - lw - 14, 26, hwnd,
                (HMENU)(INT_PTR)(IDC_UP_HOST + i), g_inst, NULL);
            SendMessageW(edits[i], WM_SETFONT, (WPARAM)f, TRUE);
        }
        g_ed_host = edits[0]; g_ed_port = edits[1];
        g_ed_user = edits[2]; g_ed_pass = edits[3];

        SendMessageW(g_ed_host, WM_SETTEXT, 0, (LPARAM)g_settings.upstream_host);
        swprintf(portbuf, 32, L"%d", g_settings.upstream_port);
        SendMessageW(g_ed_port, WM_SETTEXT, 0, (LPARAM)portbuf);
        SendMessageW(g_ed_user, WM_SETTEXT, 0, (LPARAM)g_settings.upstream_username);
        SendMessageW(g_ed_pass, WM_SETTEXT, 0, (LPARAM)g_settings.upstream_password);
        if (g_settings.upstream_password[0])
            SendMessageW(g_ed_pass, EM_SETPASSWORDCHAR, (WPARAM)L'•', 0);

        g_cb_https = CreateWindowExW(rtl, L"BUTTON", tr(S_UPSTREAM_HTTPS),
            WS_VISIBLE | WS_CHILD | BS_AUTOCHECKBOX,
            18, y + 4 * 38 + 6, w - 36, 24, hwnd,
            (HMENU)(INT_PTR)IDC_UP_HTTPS, g_inst, NULL);
        SendMessageW(g_cb_https, WM_SETFONT, (WPARAM)f, TRUE);
        SendMessageW(g_cb_https, BM_SETCHECK,
                     g_settings.upstream_proxy_use_https ? BST_CHECKED : BST_UNCHECKED, 0);

        {
            HWND ok = CreateWindowExW(0, L"BUTTON", tr(S_APPLY),
                WS_VISIBLE | WS_CHILD | BS_DEFPUSHBUTTON,
                w - 18 - 200, y + 5 * 38 + 20, 92, 30, hwnd,
                (HMENU)(INT_PTR)IDC_UP_OK, g_inst, NULL);
            HWND cancel = CreateWindowExW(0, L"BUTTON", tr(S_CANCEL),
                WS_VISIBLE | WS_CHILD | BS_PUSHBUTTON,
                w - 18 - 100, y + 5 * 38 + 20, 92, 30, hwnd,
                (HMENU)(INT_PTR)IDC_UP_CANCEL, g_inst, NULL);
            SendMessageW(ok, WM_SETFONT, (WPARAM)f, TRUE);
            SendMessageW(cancel, WM_SETFONT, (WPARAM)f, TRUE);
        }
        return 0;
    }
    case WM_COMMAND:
        switch (LOWORD(wp)) {
        case IDC_UP_OK: {
            wchar_t buf[256];
            GetWindowTextW(g_ed_host, g_settings.upstream_host, 255);
            GetWindowTextW(g_ed_user, g_settings.upstream_username, 127);
            GetWindowTextW(g_ed_pass, g_settings.upstream_password, 127);
            GetWindowTextW(g_ed_port, buf, 255);
            g_settings.upstream_port = _wtoi(buf);
            if (g_settings.upstream_port <= 0) g_settings.upstream_port = 8080;
            g_settings.upstream_proxy_use_https =
                (SendMessageW(g_cb_https, BM_GETCHECK, 0, 0) == BST_CHECKED);
            save_settings();
            DestroyWindow(hwnd);
            return 0;
        }
        case IDC_UP_CANCEL:
            DestroyWindow(hwnd);
            return 0;
        }
        break;
    case WM_DESTROY:
        g_upstream_dlg = NULL;
        EnableWindow(g_hwnd, TRUE);
        SetForegroundWindow(g_hwnd);
        InvalidateRect(g_hwnd, NULL, FALSE);
        return 0;
    case WM_CLOSE:
        DestroyWindow(hwnd);
        return 0;
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

static void show_upstream_dialog(void)
{
    if (g_upstream_dlg) { SetForegroundWindow(g_upstream_dlg); return; }
    g_upstream_dlg = CreateWindowExW(WS_EX_DLGMODALFRAME, L"ShirUpstreamDlg",
        tr(S_OPTIONS_PROXY),
        WS_POPUPWINDOW | WS_CAPTION | WS_SYSMENU,
        CW_USEDEFAULT, CW_USEDEFAULT, 420, 300,
        g_hwnd, NULL, g_inst, NULL);
    if (g_upstream_dlg) {
        RECT rc, parent;
        GetWindowRect(g_hwnd, &parent);
        GetWindowRect(g_upstream_dlg, &rc);
        SetWindowPos(g_upstream_dlg, HWND_TOP,
            parent.left + ((parent.right - parent.left) - (rc.right - rc.left)) / 2,
            parent.top + ((parent.bottom - parent.top) - (rc.bottom - rc.top)) / 2,
            0, 0, SWP_NOSIZE | SWP_SHOWWINDOW);
        EnableWindow(g_hwnd, FALSE);
        EnableWindow(g_upstream_dlg, TRUE);
        SetForegroundWindow(g_upstream_dlg);
    }
}

/* ------------------------------------------------------------- actions */

static void copy_logs_to_clipboard(void)
{
    size_t total = 1;
    int i, n = log_ring_count();
    wchar_t *buf;
    wchar_t *p;

    for (i = 0; i < n; i++) {
        const LogEntry *e = log_ring_at(i);
        if (e) total += wcslen(e->text) + 2;
    }
    buf = (wchar_t *)malloc(total * sizeof(wchar_t));
    if (!buf) return;
    p = buf;
    for (i = 0; i < n; i++) {
        const LogEntry *e = log_ring_at(i);
        if (!e) continue;
        p += swprintf(p, total - (size_t)(p - buf), L"%ls\r\n", e->text);
    }

    if (OpenClipboard(g_hwnd)) {
        HGLOBAL h = GlobalAlloc(GMEM_MOVEABLE, (total + 1) * sizeof(wchar_t));
        if (h) {
            wchar_t *dst = (wchar_t *)GlobalLock(h);
            if (dst) { wcscpy(dst, buf); GlobalUnlock(h); }
            EmptyClipboard();
            SetClipboardData(CF_UNICODETEXT, h);
        }
        CloseClipboard();
    }
    free(buf);
}

static void do_region_pick(void)
{
    RECT anchor;
    int cur = 0, i, pick;
    /* Anchor the popup under the region row. */
    anchor.left = 60; anchor.top = 200; anchor.right = 240; anchor.bottom = 230;
    for (i = 0; i < REGION_COUNT; i++)
        if (!wcscmp(kRegionCodes[i], g_settings.region)) { cur = i; break; }
    {
        const wchar_t *labels[REGION_COUNT];
        for (i = 0; i < REGION_COUNT; i++)
            labels[i] = i18n_is_persian() ? kRegionNamesFa[i] : kRegionNamesEn[i];
        pick = ui_popup_pick_strings(g_hwnd, labels, REGION_COUNT, cur, anchor);
    }
    if (pick >= 0) {
        wcsncpy(g_settings.region, kRegionCodes[pick], 31);
        save_settings();
        InvalidateRect(g_hwnd, NULL, FALSE);
    }
}

static void do_protocol_pick(void)
{
    static const wchar_t *codes[] = { L"", L"SSH", L"OSSH", L"UNFRONTED-MEEK-OSSH",
                                      L"UNFRONTED-MEEK-HTTPS", L"FRONTED-MEEK-OSSH",
                                      L"FRONTED-MEEK-HTTP", L"QUIC-OSSH", L"TLS-OSSH" };
    const wchar_t *labels[9];
    int i, cur = 0, pick;
    RECT anchor = { 60, 260, 240, 290 };

    labels[0] = tr(S_PROTOCOL_ALL);
    for (i = 1; i < 9; i++) labels[i] = codes[i];
    for (i = 0; i < 9; i++) if (!wcscmp(codes[i], g_settings.protocol)) { cur = i; break; }

    pick = ui_popup_pick_strings(g_hwnd, labels, 9, cur, anchor);
    if (pick >= 0) {
        wcsncpy(g_settings.protocol, codes[pick], 31);
        save_settings();
        InvalidateRect(g_hwnd, NULL, FALSE);
    }
}

static void do_language_pick(void)
{
    const wchar_t *labels[2];
    int pick;
    RECT anchor = { 60, 700, 240, 730 };
    labels[0] = tr(S_LANGUAGE_FA);
    labels[1] = tr(S_LANGUAGE_EN);
    pick = ui_popup_pick_strings(g_hwnd, labels, 2, g_settings.persian ? 0 : 1, anchor);
    if (pick >= 0) {
        g_settings.persian = (pick == 0);
        i18n_set_language(g_settings.persian);
        g_ui.rtl = g_settings.persian ? 1 : 0;
        save_settings();
        InvalidateRect(g_hwnd, NULL, FALSE);
    }
}

static void handle_setting_toggle(int index)
{
    int changed = 0;
    int was_running = (tunnel_status()->state != TSTATE_STOPPED);

    switch (index) {
    case 0: g_settings.tunnel_whole_device ^= 1; changed = 1; break;
    case 1: g_settings.set_system_proxy ^= 1; changed = 1; break;
    case 4: g_settings.upstream_proxy_enabled ^= 1; changed = 1; break;
    case 6:
        g_settings.start_with_windows ^= 1;
        set_start_with_windows(g_settings.start_with_windows);
        changed = 1;
        break;
    case 7: g_settings.minimize_to_tray ^= 1; changed = 1; break;
    case 8: g_settings.auto_open_homepage ^= 1; changed = 1; break;
    case 9: g_settings.disable_timeouts ^= 1; changed = 1; break;
    case 10: g_settings.lan_proxy_sharing ^= 1; changed = 1; break;
    default: break;
    }

    if (!changed) return;
    save_settings();

    if (was_running && (index == 0 || index == 1 || index == 4 || index == 10)) {
        /* Routing changes need a reconnect to take effect. */
        disconnect_tunnel();
        connect_tunnel();
    }
    sync_system_proxy();
    InvalidateRect(g_hwnd, NULL, FALSE);
}

/* ------------------------------------------------------- uninstall / config */

/* Runs on "ShirOKhorshid.exe --uninstall": removes shortcuts and the Programs
   and Features entry, then hands the file removal over to a small helper
   script so the executable can delete itself. */
static void do_uninstall(void)
{
    wchar_t dir[MAX_PATH], menu[MAX_PATH], desk[MAX_PATH], lnk[MAX_PATH];
    wchar_t tmp[MAX_PATH], bat[MAX_PATH];
    HKEY key;
    FILE *f;

    GetModuleFileNameW(NULL, dir, MAX_PATH);
    {
        wchar_t *slash = wcsrchr(dir, L'\\');
        if (slash) *slash = 0;
    }

    if (MessageBoxW(NULL,
        L"Remove Shir o Khorshid from this computer?",
        L"Shir o Khorshid - Uninstall",
        MB_YESNO | MB_ICONQUESTION | MB_TOPMOST) != IDYES)
        exit(0);

    RegDeleteKeyW(HKEY_CURRENT_USER,
        L"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\ShirOKhorshid");
    if (RegOpenKeyExW(HKEY_CURRENT_USER,
            L"Software\\Microsoft\\Windows\\CurrentVersion\\Run",
            0, KEY_SET_VALUE, &key) == ERROR_SUCCESS) {
        RegDeleteValueW(key, L"ShirOKhorshid");
        RegCloseKey(key);
    }

    {
        PWSTR p = NULL;
        if (SUCCEEDED(SHGetKnownFolderPath(&FOLDERID_StartMenu, 0, NULL, &p)) && p) {
            swprintf(lnk, MAX_PATH, L"%ls\\Programs\\Shir o Khorshid.lnk", p);
            DeleteFileW(lnk);
            CoTaskMemFree(p);
        }
        p = NULL;
        if (SUCCEEDED(SHGetKnownFolderPath(&FOLDERID_Desktop, 0, NULL, &p)) && p) {
            swprintf(lnk, MAX_PATH, L"%ls\\Shir o Khorshid.lnk", p);
            DeleteFileW(lnk);
            CoTaskMemFree(p);
        }
    }
    (void)menu; (void)desk;

    /* Delete the install folder from a detached helper (self-delete). */
    if (GetTempPathW(MAX_PATH, tmp)) {
        swprintf(bat, MAX_PATH, L"%ls\\shir-uninstall.cmd", tmp);
        f = _wfopen(bat, L"wb");
        if (f) {
            fwprintf(f, L"@echo off\r\n");
            fwprintf(f, L"ping -n 3 127.0.0.1 >nul\r\n");
            fwprintf(f, L"rmdir /s /q \"%ls\"\r\n", dir);
            fwprintf(f, L"del /q \"%%~f0\"\r\n");
            fclose(f);
            ShellExecuteW(NULL, L"open", bat, NULL, NULL, SW_HIDE);
        }
    }
    exit(0);
}

/* The propagation channel id and sponsor id are supplied at build time. If
   they are missing the app still runs, but it cannot connect, so say so. */
static void warn_if_unconfigured(void)
{
    wchar_t v[64];
    if (values_lookup_string(L"PropagationChannelId", v, 64) && v[0]) return;
    log_ring_add(2, L"this build has no propagation channel id configured; "
                    L"the tunnel cannot start until values.json is filled in");
    MessageBoxW(NULL,
        L"This build was compiled without Psiphon credentials\n"
        L"(values.json is empty), so it cannot connect yet.\n\n"
        L"Build with PSIPHON_PROPAGATION_CHANNEL_ID and PSIPHON_SPONSOR_ID set "
        L"- the GitHub Actions workflow build-windows.yml does this for you "
        L"using the repository secrets.",
        L"Shir o Khorshid", MB_OK | MB_ICONWARNING | MB_TOPMOST);
}

/* ------------------------------------------------------------ window proc */

static LRESULT CALLBACK wnd_proc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp)
{
    /* Registered at runtime, so it cannot appear in the switch below. */
    if (WM_APP_NOTICE && msg == WM_APP_NOTICE) {
        TunnelStatus *st = tunnel_status();
        int connected = (st->state == TSTATE_CONNECTED);
        InvalidateRect(hwnd, NULL, FALSE);
        if (connected && !g_was_connected) {
            g_was_connected = 1;
            sync_system_proxy();
            tray_update();
            if (g_settings.notifications_sound)
                MessageBeep(MB_ICONINFORMATION);
            tray_balloon(tr(S_APP_NAME), tr(S_CONNECTED));
            if (g_settings.auto_open_homepage && tunnel_homepage()[0])
                open_browser(tunnel_homepage());
        } else if (!connected && g_was_connected) {
            g_was_connected = 0;
            sync_system_proxy();
            tray_update();
        }
        return 0;
    }

    switch (msg) {
    case WM_CREATE: {
        RECT rc;
        HMODULE uxtheme;
        typedef BOOL (WINAPI *fnSDAC)(void *);
        fnSDAC pSDAC;

        g_hwnd = hwnd;
        g_main_hwnd = hwnd;
        WM_APP_NOTICE = RegisterWindowMessageW(L"ShirOKhorshidNotice");

        uxtheme = LoadLibraryW(L"user32.dll");
        if (uxtheme) {
            pSDAC = (fnSDAC)(void *)GetProcAddress(uxtheme,
                        "SetProcessDpiAwarenessContext");
            if (pSDAC) pSDAC((void *)(INT_PTR)-4); /* PER_MONITOR_AWARE_V2 */
            FreeLibrary(uxtheme);
        }

        GetClientRect(hwnd, &rc);
        tray_add();
        SetTimer(hwnd, IDT_PUMP, 500, NULL);
        SetTimer(hwnd, IDT_REPAINT, 1000, NULL);
        return 0;
    }

    case WM_TIMER:
        if (wp == IDT_PUMP) {
            tunnel_pump();
            tray_update();
        }
        InvalidateRect(hwnd, NULL, FALSE);
        return 0;

    case WM_ERASEBKGND:
        return 1;

    case WM_PAINT: {
        PAINTSTRUCT ps;
        RECT client;
        HDC hdc = BeginPaint(hwnd, &ps);
        GetClientRect(hwnd, &client);
        ui_begin(&g_ui, hdc, &client);
        ui_paint(&g_ui, &g_settings);
        ui_end(&g_ui, hdc);
        EndPaint(hwnd, &ps);
        return 0;
    }

    case WM_MOUSEMOVE: {
        int x = (short)LOWORD(lp), y = (short)HIWORD(lp);
        int id = ui_hit_test(&g_ui, x, y);
        HCURSOR cur = LoadCursor(NULL, id != WID_NONE ? IDC_HAND : IDC_ARROW);
        SetCursor(cur);
        if (id != g_ui.hot) { g_ui.hot = id; InvalidateRect(hwnd, NULL, FALSE); }
        return 0;
    }

    case WM_LBUTTONUP: {
        int x = (short)LOWORD(lp), y = (short)HIWORD(lp);
        int id = ui_hit_test(&g_ui, x, y);
        switch (id) {
        case WID_CONNECT: toggle_connection(); break;
        case WID_BROWSER: open_browser(tunnel_homepage()); break;
        case WID_CLEAR_LOGS: log_ring_clear(); InvalidateRect(hwnd, NULL, FALSE); break;
        case WID_COPY_LOGS: copy_logs_to_clipboard(); break;
        case WID_REGION: do_region_pick(); break;
        case WID_PROTOCOL: do_protocol_pick(); break;
        case WID_LANG: do_language_pick(); break;
        case WID_UPSTREAM: show_upstream_dialog(); break;
        default:
            if (id >= WID_TAB && id < WID_TAB + TAB_COUNT) {
                g_ui.tab = id - WID_TAB;
                InvalidateRect(hwnd, NULL, FALSE);
            } else if (id >= WID_FILTER && id < WID_FILTER + 3) {
                ui_set_log_filter(id - WID_FILTER);
                g_ui.scroll[TAB_LOGS] = 0;
                InvalidateRect(hwnd, NULL, FALSE);
            } else if (id >= WID_SETTING && id < WID_SETTING + 32) {
                handle_setting_toggle(id - WID_SETTING);
            }
            break;
        }
        return 0;
    }

    case WM_MOUSEWHEEL: {
        int delta = GET_WHEEL_DELTA_WPARAM(wp);
        int tab = g_ui.tab;
        int max_scroll = g_ui.content_h[tab] - g_ui.view_h;
        if (max_scroll < 0) max_scroll = 0;
        g_ui.scroll[tab] -= delta / 3;
        if (g_ui.scroll[tab] < 0) g_ui.scroll[tab] = 0;
        if (g_ui.scroll[tab] > max_scroll) g_ui.scroll[tab] = max_scroll;
        InvalidateRect(hwnd, NULL, FALSE);
        return 0;
    }

    case WM_SIZE: {
        RECT rc;
        GetClientRect(hwnd, &rc);
        if (g_ui.surface) { DeleteObject(g_ui.surface); g_ui.surface = NULL; }
        if (g_ui.surf_dc) { DeleteDC(g_ui.surf_dc); g_ui.surf_dc = NULL; }
        InvalidateRect(hwnd, NULL, FALSE);
        return 0;
    }

    case WM_DPICHANGED: {
        int dpi = (int)(WORD)HIWORD(wp);
        RECT *suggested = (RECT *)lp;
        ui_dpi_changed(&g_ui, dpi);
        if (suggested)
            SetWindowPos(hwnd, NULL, suggested->left, suggested->top,
                         suggested->right - suggested->left,
                         suggested->bottom - suggested->top,
                         SWP_NOZORDER | SWP_NOACTIVATE);
        return 0;
    }

    case WM_TRAYICON:
        if (lp == WM_LBUTTONUP || lp == WM_LBUTTONDBLCLK) {
            ShowWindow(hwnd, SW_RESTORE);
            SetForegroundWindow(hwnd);
        } else if (lp == WM_RBUTTONUP) {
            HMENU menu = CreatePopupMenu();
            POINT pt;
            AppendMenuW(menu, MF_STRING, 1, tr(S_TRAY_OPEN));
            AppendMenuW(menu, MF_STRING, 2,
                        tunnel_status()->state == TSTATE_STOPPED
                            ? tr(S_TRAY_CONNECT) : tr(S_TRAY_DISCONNECT));
            AppendMenuW(menu, MF_SEPARATOR, 0, NULL);
            AppendMenuW(menu, MF_STRING, 3, tr(S_TRAY_EXIT));
            GetCursorPos(&pt);
            SetForegroundWindow(hwnd);
            {
                UINT cmd = TrackPopupMenu(menu, TPM_RETURNCMD | TPM_NONOTIFY,
                                          pt.x, pt.y, 0, hwnd, NULL);
                switch (cmd) {
                case 1: ShowWindow(hwnd, SW_RESTORE); SetForegroundWindow(hwnd); break;
                case 2: toggle_connection(); break;
                case 3: g_quit = 1; DestroyWindow(hwnd); break;
                }
            }
            DestroyMenu(menu);
        }
        return 0;

    case WM_CLOSE:
        if (g_settings.minimize_to_tray && !g_quit) {
            ShowWindow(hwnd, SW_HIDE);
            return 0;
        }
        DestroyWindow(hwnd);
        return 0;

    case WM_DESTROY:
        KillTimer(hwnd, IDT_PUMP);
        KillTimer(hwnd, IDT_REPAINT);
        disconnect_tunnel();
        tray_remove();
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

/* ------------------------------------------------------------------ entry */

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE prev, PWSTR cmdline, int show)
{
    WNDCLASSW wc;
    HANDLE mutex;
    int argc = 0;
    wchar_t **argv;

    (void)prev; (void)show;
    g_inst = inst;

    /* single instance */
    mutex = CreateMutexW(NULL, FALSE, L"ShirOKhorshid-SingleInstance");
    if (mutex && GetLastError() == ERROR_ALREADY_EXISTS) {
        HWND other = FindWindowW(L"ShirOKhorshidMainWindow", NULL);
        if (other) { ShowWindow(other, SW_RESTORE); SetForegroundWindow(other); }
        return 0;
    }

    argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    if (argv) {
        int i;
        for (i = 1; i < argc; i++) {
            if (!wcscmp(argv[i], L"--minimized") || !wcscmp(argv[i], L"-m"))
                g_start_minimized = 1;
            else if (!wcscmp(argv[i], L"--uninstall"))
                do_uninstall();
        }
        LocalFree(argv);
    }

    app_ensure_dirs();
    {
        wchar_t path[MAX_PATH_W];
        settings_get_path(path, MAX_PATH_W);
        if (!settings_load(&g_settings, path)) settings_init(&g_settings);
    }
    i18n_set_language(g_settings.persian);
    stats_load();
    if (!g_start_minimized) warn_if_unconfigured();

    ui_init(&g_ui);
    ui_load_icons(&g_ui, inst);
    g_ui.rtl = 1;

    InitCommonControls();

    ZeroMemory(&wc, sizeof(wc));
    wc.lpfnWndProc = wnd_proc;
    wc.hInstance = inst;
    wc.lpszClassName = L"ShirOKhorshidMainWindow";
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    wc.hIcon = g_ui.app_icon;
    wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    wc.style = CS_HREDRAW | CS_VREDRAW;
    RegisterClassW(&wc);

    {
        WNDCLASSW dlg;
        ZeroMemory(&dlg, sizeof(dlg));
        dlg.lpfnWndProc = upstream_proc;
        dlg.hInstance = inst;
        dlg.lpszClassName = L"ShirUpstreamDlg";
        dlg.hCursor = LoadCursor(NULL, IDC_ARROW);
        dlg.hbrBackground = (HBRUSH)(COLOR_BTNFACE + 1);
        RegisterClassW(&dlg);
    }

    g_hwnd = CreateWindowExW(0, L"ShirOKhorshidMainWindow",
        i18n_is_persian() ? APP_NAME : L"Shir o Khorshid",
        WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX |
        WS_MAXIMIZEBOX | WS_THICKFRAME | WS_CLIPCHILDREN,
        CW_USEDEFAULT, CW_USEDEFAULT, 900, 680,
        NULL, NULL, inst, NULL);
    if (!g_hwnd) return 1;

    g_main_hwnd = g_hwnd;
    if (g_ui.app_icon) {
        SendMessageW(g_hwnd, WM_SETICON, ICON_BIG, (LPARAM)g_ui.app_icon);
        SendMessageW(g_hwnd, WM_SETICON, ICON_SMALL, (LPARAM)
            LoadImageW(inst, MAKEINTRESOURCEW(1), IMAGE_ICON,
                       GetSystemMetrics(SM_CXSMICON),
                       GetSystemMetrics(SM_CYSMICON), LR_DEFAULTCOLOR));
    }

    ShowWindow(g_hwnd, g_start_minimized ? SW_HIDE : SW_SHOWNORMAL);
    UpdateWindow(g_hwnd);

    if (g_start_minimized && g_settings.auto_connect)
        connect_tunnel();

    {
        MSG msg;
        while (GetMessageW(&msg, NULL, 0, 0) > 0) {
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
    }

    save_settings();
    if (mutex) CloseHandle(mutex);
    return 0;
}

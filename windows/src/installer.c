/*
 * Shir o Khorshid for Windows - installer.
 * GPLv3. See shir.h for the full notice.
 *
 * The installer is a single self-contained executable: the application files
 * are appended to it as a small uncompressed archive by tools/make_installer.py.
 * Installing needs no elevation - the default location is the per-user
 * %LOCALAPPDATA%\\Programs\\ShirOKhorshid folder.
 */

#include <windows.h>
#include <shlobj.h>
#include <shlwapi.h>
#include <commctrl.h>
#include <objbase.h>
#include <objidl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define PAYLOAD_MAGIC   "SHIRPAY1"
#define TRAILER_MAGIC   "SHIREND1"

#define IDC_PATH        101
#define IDC_BROWSE      102
#define IDC_INSTALL     103
#define IDC_LAUNCH      104
#define IDC_PROGRESS    105
#define IDC_STATUS      106

static HINSTANCE g_inst;
static HWND g_hwnd, g_ed_path, g_btn_install, g_prog, g_status, g_cb_launch;
static wchar_t g_install_path[MAX_PATH];
static int g_installing = 0;
static int g_done = 0;

/* --------------------------------------------------------------- helpers */

static void set_status(const wchar_t *fmt, ...)
{
    wchar_t buf[512];
    va_list ap;
    va_start(ap, fmt);
    vswprintf(buf, 512, fmt, ap);
    va_end(ap);
    SetWindowTextW(g_status, buf);
}

static void ensure_dir(const wchar_t *path)
{
    wchar_t tmp[MAX_PATH];
    wchar_t *p;
    wcsncpy(tmp, path, MAX_PATH - 1);
    tmp[MAX_PATH - 1] = 0;
    /* create each level */
    for (p = tmp + 3; *p; p++) {
        if (*p == L'\\') {
            *p = 0;
            CreateDirectoryW(tmp, NULL);
            *p = L'\\';
        }
    }
    CreateDirectoryW(tmp, NULL);
}

static void default_install_path(wchar_t *out, size_t n)
{
    PWSTR local = NULL;
    out[0] = 0;
    if (SUCCEEDED(SHGetKnownFolderPath(&FOLDERID_LocalAppData, 0, NULL, &local)) && local) {
        swprintf(out, n, L"%ls\\Programs\\ShirOKhorshid", local);
        CoTaskMemFree(local);
    } else {
        GetEnvironmentVariableW(L"LOCALAPPDATA", out, (DWORD)n);
        wcsncat(out, L"\\Programs\\ShirOKhorshid", n - wcslen(out) - 1);
    }
}

static void shortcuts_path(wchar_t *out, size_t n, int desktop)
{
    PWSTR p = NULL;
    REFKNOWNFOLDERID id = desktop ? &FOLDERID_Desktop : &FOLDERID_StartMenu;
    out[0] = 0;
    if (SUCCEEDED(SHGetKnownFolderPath(id, 0, NULL, &p)) && p) {
        if (desktop) swprintf(out, n, L"%ls", p);
        else         swprintf(out, n, L"%ls\\Programs", p);
        CoTaskMemFree(p);
    }
}

static void make_shortcut(const wchar_t *folder, const wchar_t *name,
                          const wchar_t *target, const wchar_t *args,
                          const wchar_t *icon)
{
    IShellLinkW *link = NULL;
    IPersistFile *pf = NULL;
    wchar_t path[MAX_PATH];
    HRESULT hr;

    hr = CoCreateInstance(&CLSID_ShellLink, NULL, CLSCTX_INPROC_SERVER,
                          &IID_IShellLinkW, (void **)&link);
    if (FAILED(hr) || !link) return;
    hr = link->lpVtbl->QueryInterface(link, &IID_IPersistFile, (void **)&pf);
    if (FAILED(hr) || !pf) { link->lpVtbl->Release(link); return; }

    link->lpVtbl->SetPath(link, target);
    if (args) link->lpVtbl->SetArguments(link, args);
    if (icon) link->lpVtbl->SetIconLocation(link, icon, 0);
    link->lpVtbl->SetDescription(link, L"Shir o Khorshid");

    ensure_dir(folder);
    swprintf(path, MAX_PATH, L"%ls\\%ls.lnk", folder, name);
    pf->lpVtbl->Save(pf, path, TRUE);

    pf->lpVtbl->Release(pf);
    link->lpVtbl->Release(link);
}

static void register_uninstall(const wchar_t *dir)
{
    HKEY key;
    wchar_t exe[MAX_PATH], uninstall[MAX_PATH * 2], icon[MAX_PATH];
    DWORD zero = 0;

    swprintf(exe, MAX_PATH, L"%ls\\ShirOKhorshid.exe", dir);
    swprintf(icon, MAX_PATH, L"%ls\\ShirOKhorshid.exe,0", dir);
    swprintf(uninstall, MAX_PATH * 2,
             L"\"%ls\" --uninstall", exe);

    if (RegCreateKeyExW(HKEY_CURRENT_USER,
            L"Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\ShirOKhorshid",
            0, NULL, 0, KEY_SET_VALUE, NULL, &key, NULL) != ERROR_SUCCESS)
        return;

    {
        const wchar_t *name = L"Shir o Khorshid";
        const wchar_t *pub = L"Shir o Khorshid";
        const wchar_t *ver = L"1.0.0";
        RegSetValueExW(key, L"DisplayName", 0, REG_SZ, (const BYTE *)name,
                       (DWORD)((wcslen(name) + 1) * sizeof(wchar_t)));
        RegSetValueExW(key, L"Publisher", 0, REG_SZ, (const BYTE *)pub,
                       (DWORD)((wcslen(pub) + 1) * sizeof(wchar_t)));
        RegSetValueExW(key, L"DisplayVersion", 0, REG_SZ, (const BYTE *)ver,
                       (DWORD)((wcslen(ver) + 1) * sizeof(wchar_t)));
        RegSetValueExW(key, L"InstallLocation", 0, REG_SZ, (const BYTE *)dir,
                       (DWORD)((wcslen(dir) + 1) * sizeof(wchar_t)));
        RegSetValueExW(key, L"UninstallString", 0, REG_SZ, (const BYTE *)uninstall,
                       (DWORD)((wcslen(uninstall) + 1) * sizeof(wchar_t)));
        RegSetValueExW(key, L"DisplayIcon", 0, REG_SZ, (const BYTE *)icon,
                       (DWORD)((wcslen(icon) + 1) * sizeof(wchar_t)));
        RegSetValueExW(key, L"NoModify", 0, REG_DWORD, (const BYTE *)&zero, sizeof(zero));
        RegSetValueExW(key, L"NoRepair", 0, REG_DWORD, (const BYTE *)&zero, sizeof(zero));
    }
    RegCloseKey(key);
}

/* --------------------------------------------------------------- payload */

static unsigned long long find_payload_offset(void)
{
    wchar_t self[MAX_PATH];
    HANDLE f;
    LARGE_INTEGER size;
    unsigned long long off = 0;
    char tail[16];

    GetModuleFileNameW(NULL, self, MAX_PATH);
    f = CreateFileW(self, GENERIC_READ, FILE_SHARE_READ, NULL,
                    OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (f == INVALID_HANDLE_VALUE) return 0;
    if (!GetFileSizeEx(f, &size) || size.QuadPart < 64) { CloseHandle(f); return 0; }
    SetFilePointerEx(f, (LARGE_INTEGER){ .QuadPart = size.QuadPart - 16 },
                     NULL, FILE_BEGIN);
    {
        DWORD got = 0;
        ReadFile(f, tail, 16, &got, NULL);
    }
    CloseHandle(f);
    if (memcmp(tail + 8, TRAILER_MAGIC, 8) != 0) return 0;
    memcpy(&off, tail, 8);
    return off;
}

static int extract_payload(const wchar_t *dir)
{
    wchar_t self[MAX_PATH];
    HANDLE f;
    unsigned long long off;
    char magic[8];
    unsigned long count = 0, i;
    DWORD got;

    off = find_payload_offset();
    if (!off) { set_status(L"installer payload is missing or corrupt"); return 0; }

    GetModuleFileNameW(NULL, self, MAX_PATH);
    f = CreateFileW(self, GENERIC_READ, FILE_SHARE_READ, NULL,
                    OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (f == INVALID_HANDLE_VALUE) return 0;

    {
        LARGE_INTEGER li;
        li.QuadPart = (LONGLONG)off;
        SetFilePointerEx(f, li, NULL, FILE_BEGIN);
    }
    if (!ReadFile(f, magic, 8, &got, NULL) || got != 8 ||
        memcmp(magic, PAYLOAD_MAGIC, 8) != 0) {
        CloseHandle(f);
        set_status(L"installer payload is missing or corrupt");
        return 0;
    }
    ReadFile(f, &count, 4, &got, NULL);
    if (got != 4) { CloseHandle(f); return 0; }

    SendMessageW(g_prog, PBM_SETRANGE, 0, MAKELPARAM(0, (int)count));

    for (i = 0; i < count; i++) {
        unsigned long name_len = 0, data_len = 0;
        char *name8;
        wchar_t name[MAX_PATH];
        wchar_t target[MAX_PATH];
        BYTE *data;

        if (!ReadFile(f, &name_len, 4, &got, NULL) || got != 4) break;
        name8 = (char *)malloc(name_len + 1);
        if (!name8) break;
        if (!ReadFile(f, name8, name_len, &got, NULL) || got != name_len) {
            free(name8); break;
        }
        name8[name_len] = 0;
        if (!ReadFile(f, &data_len, 4, &got, NULL) || got != 4) {
            free(name8); break;
        }
        data = (BYTE *)malloc(data_len ? data_len : 1);
        if (!data) { free(name8); break; }
        if (data_len && (!ReadFile(f, data, data_len, &got, NULL) || got != data_len)) {
            free(name8); free(data); break;
        }

        {
            int n = MultiByteToWideChar(CP_UTF8, 0, name8, -1, name, MAX_PATH);
            if (n <= 0) { free(name8); free(data); continue; }
        }
        swprintf(target, MAX_PATH, L"%ls\\%ls", dir, name);
        {
            wchar_t *slash = wcsrchr(target, L'\\');
            if (slash) { *slash = 0; ensure_dir(target); *slash = L'\\'; }
        }
        {
            HANDLE out = CreateFileW(target, GENERIC_WRITE, 0, NULL,
                                     CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
            if (out != INVALID_HANDLE_VALUE) {
                DWORD written = 0;
                WriteFile(out, data, data_len, &written, NULL);
                CloseHandle(out);
            }
        }
        set_status(L"extracting %ls", name);
        SendMessageW(g_prog, PBM_SETPOS, (WPARAM)(i + 1), 0);
        free(name8);
        free(data);
    }

    CloseHandle(f);
    return (i == count);
}

/* ---------------------------------------------------------- install flow */

static void run_install(void)
{
    wchar_t dir[MAX_PATH];
    wchar_t exe[MAX_PATH];
    wchar_t menu[MAX_PATH], desk[MAX_PATH];

    if (g_installing) return;
    GetWindowTextW(g_ed_path, dir, MAX_PATH);
    if (!dir[0]) { set_status(L"please choose an install folder"); return; }

    g_installing = 1;
    EnableWindow(g_btn_install, FALSE);
    set_status(L"installing...");

    ensure_dir(dir);
    if (!extract_payload(dir)) {
        set_status(L"installation failed");
        g_installing = 0;
        EnableWindow(g_btn_install, TRUE);
        return;
    }

    swprintf(exe, MAX_PATH, L"%ls\\ShirOKhorshid.exe", dir);

    shortcuts_path(menu, MAX_PATH, 0);
    shortcuts_path(desk, MAX_PATH, 1);
    if (menu[0]) make_shortcut(menu, L"Shir o Khorshid", exe, NULL, exe);
    if (desk[0]) make_shortcut(desk, L"Shir o Khorshid", exe, NULL, exe);
    register_uninstall(dir);

    set_status(L"installed to %ls", dir);
    SendMessageW(g_prog, PBM_SETPOS, (WPARAM)100, 0);
    g_done = 1;
    SetWindowTextW(g_btn_install, L"Launch");
    EnableWindow(g_btn_install, TRUE);
    g_installing = 0;

    if (SendMessageW(g_cb_launch, BM_GETCHECK, 0, 0) == BST_CHECKED) {
        ShellExecuteW(NULL, L"open", exe, NULL, NULL, SW_SHOWNORMAL);
        PostQuitMessage(0);
    }
}

static void browse_folder(void)
{
    IFileDialog *dlg = NULL;
    HRESULT hr = CoCreateInstance(&CLSID_FileOpenDialog, NULL, CLSCTX_INPROC_SERVER,
                                  &IID_IFileDialog, (void **)&dlg);
    if (SUCCEEDED(hr) && dlg) {
        DWORD opts = 0;
        dlg->lpVtbl->GetOptions(dlg, &opts);
        dlg->lpVtbl->SetOptions(dlg, opts | FOS_PICKFOLDERS | FOS_PATHMUSTEXIST);
        if (SUCCEEDED(dlg->lpVtbl->Show(dlg, g_hwnd))) {
            IShellItem *item = NULL;
            if (SUCCEEDED(dlg->lpVtbl->GetResult(dlg, &item)) && item) {
                PWSTR path = NULL;
                if (SUCCEEDED(item->lpVtbl->GetDisplayName(item, SIGDN_FILESYSPATH, &path)) && path) {
                    SetWindowTextW(g_ed_path, path);
                    CoTaskMemFree(path);
                }
                item->lpVtbl->Release(item);
            }
        }
        dlg->lpVtbl->Release(dlg);
        return;
    }

    /* Fall back to the classic folder picker. */
    {
        BROWSEINFOW bi;
        LPITEMIDLIST pidl;
        ZeroMemory(&bi, sizeof(bi));
        bi.hwndOwner = g_hwnd;
        bi.ulFlags = BIF_RETURNONLYFSDIRS | BIF_NEWDIALOGSTYLE;
        pidl = SHBrowseForFolderW(&bi);
        if (pidl) {
            wchar_t path[MAX_PATH];
            if (SHGetPathFromIDListW(pidl, path)) SetWindowTextW(g_ed_path, path);
            CoTaskMemFree(pidl);
        }
    }
}

static LRESULT CALLBACK inst_proc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp)
{
    switch (msg) {
    case WM_CREATE: {
        HICON icon = (HICON)LoadImageW(g_inst, MAKEINTRESOURCEW(1), IMAGE_ICON,
                                       32, 32, LR_DEFAULTCOLOR);
        RECT rc;
        int w = 520, h = 250;
        HFONT f = (HFONT)GetStockObject(DEFAULT_GUI_FONT);
        HWND lbl;

        g_hwnd = hwnd;
        GetClientRect(hwnd, &rc);

        if (icon) {
            HWND iv = CreateWindowExW(0, L"STATIC", NULL,
                WS_VISIBLE | WS_CHILD | SS_ICON, 20, 20, 32, 32, hwnd, NULL, g_inst, NULL);
            SendMessageW(iv, STM_SETIMAGE, IMAGE_ICON, (LPARAM)icon);
            SendMessageW(hwnd, WM_SETICON, ICON_BIG, (LPARAM)icon);
        }

        lbl = CreateWindowExW(0, L"STATIC", L"Shir o Khorshid",
            WS_VISIBLE | WS_CHILD, 64, 20, 400, 26, hwnd, NULL, g_inst, NULL);
        SendMessageW(lbl, WM_SETFONT, (WPARAM)f, TRUE);

        lbl = CreateWindowExW(0, L"STATIC",
            L"Unofficial Windows client - not affiliated with Psiphon Inc.",
            WS_VISIBLE | WS_CHILD, 64, 46, 420, 20, hwnd, NULL, g_inst, NULL);
        SendMessageW(lbl, WM_SETFONT, (WPARAM)f, TRUE);

        lbl = CreateWindowExW(0, L"STATIC", L"Install to:",
            WS_VISIBLE | WS_CHILD, 20, 84, 90, 20, hwnd, NULL, g_inst, NULL);
        SendMessageW(lbl, WM_SETFONT, (WPARAM)f, TRUE);

        g_ed_path = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", g_install_path,
            WS_VISIBLE | WS_CHILD | ES_AUTOHSCROLL, 112, 82, 300, 24, hwnd,
            (HMENU)(INT_PTR)IDC_PATH, g_inst, NULL);
        SendMessageW(g_ed_path, WM_SETFONT, (WPARAM)f, TRUE);

        g_btn_install = CreateWindowExW(0, L"BUTTON", L"Browse...",
            WS_VISIBLE | WS_CHILD, 420, 82, 80, 24, hwnd,
            (HMENU)(INT_PTR)IDC_BROWSE, g_inst, NULL);
        SendMessageW(g_btn_install, WM_SETFONT, (WPARAM)f, TRUE);

        g_prog = CreateWindowExW(0, PROGRESS_CLASSW, NULL,
            WS_VISIBLE | WS_CHILD | PBS_SMOOTH, 20, 122, 480, 18, hwnd,
            (HMENU)(INT_PTR)IDC_PROGRESS, g_inst, NULL);

        g_status = CreateWindowExW(0, L"STATIC", L"Ready to install.",
            WS_VISIBLE | WS_CHILD, 20, 148, 480, 20, hwnd,
            (HMENU)(INT_PTR)IDC_STATUS, g_inst, NULL);
        SendMessageW(g_status, WM_SETFONT, (WPARAM)f, TRUE);

        g_cb_launch = CreateWindowExW(0, L"BUTTON", L"Launch Shir o Khorshid when finished",
            WS_VISIBLE | WS_CHILD | BS_AUTOCHECKBOX, 20, 176, 300, 22, hwnd,
            (HMENU)(INT_PTR)IDC_LAUNCH, g_inst, NULL);
        SendMessageW(g_cb_launch, WM_SETFONT, (WPARAM)f, TRUE);
        SendMessageW(g_cb_launch, BM_SETCHECK, BST_CHECKED, 0);

        {
            HWND ok = CreateWindowExW(0, L"BUTTON", L"Install",
                WS_VISIBLE | WS_CHILD | BS_DEFPUSHBUTTON, w - 200, 176, 90, 28,
                hwnd, (HMENU)(INT_PTR)IDC_INSTALL, g_inst, NULL);
            HWND cancel = CreateWindowExW(0, L"BUTTON", L"Close",
                WS_VISIBLE | WS_CHILD, w - 100, 176, 80, 28,
                hwnd, (HMENU)(INT_PTR)IDCANCEL, g_inst, NULL);
            SendMessageW(ok, WM_SETFONT, (WPARAM)f, TRUE);
            SendMessageW(cancel, WM_SETFONT, (WPARAM)f, TRUE);
            g_btn_install = ok;
        }

        (void)h;
        return 0;
    }

    case WM_COMMAND:
        switch (LOWORD(wp)) {
        case IDC_BROWSE: browse_folder(); return 0;
        case IDC_INSTALL:
            if (g_done) {
                wchar_t exe[MAX_PATH];
                GetWindowTextW(g_ed_path, g_install_path, MAX_PATH);
                swprintf(exe, MAX_PATH, L"%ls\\ShirOKhorshid.exe", g_install_path);
                ShellExecuteW(NULL, L"open", exe, NULL, NULL, SW_SHOWNORMAL);
                PostQuitMessage(0);
            } else {
                run_install();
            }
            return 0;
        case IDCANCEL:
            DestroyWindow(hwnd);
            return 0;
        }
        return 0;

    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE prev, PWSTR cmd, int show)
{
    WNDCLASSW wc;
    MSG msg;
    RECT rc;
    HWND hwnd;

    (void)prev; (void)cmd; (void)show;
    g_inst = inst;
    CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
    InitCommonControls();
    default_install_path(g_install_path, MAX_PATH);

    ZeroMemory(&wc, sizeof(wc));
    wc.lpfnWndProc = inst_proc;
    wc.hInstance = inst;
    wc.lpszClassName = L"ShirOKhorshidInstaller";
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)(COLOR_BTNFACE + 1);
    RegisterClassW(&wc);

    rc.left = 0; rc.top = 0; rc.right = 520; rc.bottom = 250;
    AdjustWindowRect(&rc, WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU |
                     WS_MINIMIZEBOX, FALSE);

    hwnd = CreateWindowExW(0, L"ShirOKhorshidInstaller",
        L"Shir o Khorshid - Setup",
        WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
        CW_USEDEFAULT, CW_USEDEFAULT,
        rc.right - rc.left, rc.bottom - rc.top,
        NULL, NULL, inst, NULL);
    if (!hwnd) return 1;

    ShowWindow(hwnd, SW_SHOWNORMAL);
    UpdateWindow(hwnd);

    while (GetMessageW(&msg, NULL, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    CoUninitialize();
    return 0;
}

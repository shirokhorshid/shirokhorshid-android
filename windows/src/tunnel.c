/*
 * Shir o Khorshid for Windows - tunnel-core process management.
 * GPLv3. See shir.h for the full notice.
 *
 * The Windows client drives the Psiphon tunnel engine exactly like the
 * official Psiphon for Windows client does: psiphon-tunnel-core.exe is
 * launched as a child process and its JSON notices are read line by line
 * from stdout.
 */

#include "shir.h"
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32

/* ------------------------------------------------------------ install dir */

void app_install_dir(wchar_t *out, size_t n)
{
    wchar_t buf[MAX_PATH_W];
    const wchar_t *slash;
    GetModuleFileNameW(NULL, buf, MAX_PATH_W);
    slash = wcsrchr(buf, L'\\');
    if (!slash) slash = wcsrchr(buf, L'/');
    if (slash) {
        size_t k = (size_t)(slash - buf);
        if (k >= n) k = n - 1;
        wcsncpy(out, buf, k);
        out[k] = 0;
    } else {
        wcsncpy(out, L".", n);
        out[n - 1] = 0;
    }
}

void app_install_file(wchar_t *out, size_t n, const wchar_t *name)
{
    wchar_t dir[MAX_PATH_W];
    app_install_dir(dir, MAX_PATH_W);
    swprintf(out, n, L"%ls\\%ls", dir, name);
}

/* -------------------------------------------------- embedded values (json) */

/* values.json mirrors the Android build's generated EmbeddedValues.java: it
   carries the propagation channel id, sponsor id, signature keys, remote
   server list URLs and the embedded server entries.  It is produced by
   tools/gen_values.py at build time and must never be edited by hand. */

static JValue *g_values = NULL;

static void values_load(void)
{
    wchar_t path[MAX_PATH_W];
    FILE *f;
    long n;
    char *buf;

    if (g_values) return;
    app_install_file(path, MAX_PATH_W, L"values.json");
    f = _wfopen(path, L"rb");
    if (!f) {
        /* Fall back to a copy kept next to the sources when running from a
           build tree (useful while developing). */
        app_data_file(path, MAX_PATH_W, L"values.json");
        f = _wfopen(path, L"rb");
        if (!f) return;
    }
    fseek(f, 0, SEEK_END);
    n = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (n <= 0) { fclose(f); return; }
    buf = (char *)malloc((size_t)n + 1);
    if (!buf) { fclose(f); return; }
    if (fread(buf, 1, (size_t)n, f) != (size_t)n) { free(buf); fclose(f); return; }
    buf[n] = 0;
    fclose(f);
    g_values = json_parse(buf);
    free(buf);
}

int values_lookup_string(const wchar_t *key, wchar_t *out, size_t n)
{
    char *k8;
    const char *v;
    wchar_t *w;

    values_load();
    if (!g_values) return 0;
    k8 = wchar_to_utf8(key);
    if (!k8) return 0;
    v = json_get_str(g_values, k8, NULL);
    free(k8);
    if (!v || !*v) return 0;
    w = utf8_to_wchar(v);
    if (!w) return 0;
    wcsncpy(out, w, n - 1);
    out[n - 1] = 0;
    free_conv(w);
    return 1;
}

int values_lookup_array(const wchar_t *key, char ***out, int *count)
{
    char *k8;
    JValue *arr, *m;
    int n = 0;
    char **items;

    *out = NULL;
    *count = 0;
    values_load();
    if (!g_values) return 0;
    k8 = wchar_to_utf8(key);
    if (!k8) return 0;
    arr = json_get(g_values, k8);
    free(k8);
    if (!arr || arr->type != JARR || arr->count <= 0) return 0;

    items = (char **)calloc((size_t)arr->count, sizeof(char *));
    if (!items) return 0;
    for (m = arr->items; m && n < arr->count; m = m->next) {
        if (m->type != JSTR || !m->str) continue;
        items[n] = _strdup(m->str);
        if (!items[n]) break;
        n++;
    }
    if (n == 0) { free(items); return 0; }
    *out = items;
    *count = n;
    return 1;
}

/* Write out the embedded server entry list next to the data directory. */
static int write_server_list(const wchar_t *path)
{
    char **items = NULL;
    int n = 0, i;
    FILE *f;
    int wrote = 0;

    if (!values_lookup_array(L"EmbeddedServerEntries", &items, &n) || n == 0)
        return 0;

    f = _wfopen(path, L"wb");
    if (!f) {
        for (i = 0; i < n; i++) free(items[i]);
        free(items);
        return 0;
    }
    for (i = 0; i < n; i++) {
        fprintf(f, "%s\n", items[i]);
        free(items[i]);
    }
    free(items);
    fclose(f);
    wrote = 1;
    return wrote;
}

/* ------------------------------------------------------------ tunnel state */

static TunnelStatus g_status;
static HANDLE g_child = NULL;
static HANDLE g_stdout_read = NULL;
static HANDLE g_stdout_write = NULL;
static HANDLE g_reader_thread = NULL;
static volatile int g_reader_stop = 0;
static CRITICAL_SECTION g_cs;
static int g_cs_ready = 0;
static wchar_t g_homepage[512];
static wchar_t g_available_regions[1024];

TunnelStatus *tunnel_status(void) { return &g_status; }

static void lock(void)
{
    if (!g_cs_ready) { InitializeCriticalSection(&g_cs); g_cs_ready = 1; }
    EnterCriticalSection(&g_cs);
}
static void unlock(void) { LeaveCriticalSection(&g_cs); }

int tunnel_is_running(void) { return g_child != NULL; }

const wchar_t *tunnel_homepage(void) { return g_homepage; }
const wchar_t *tunnel_available_regions(void) { return g_available_regions; }

/* --------------------------------------------------------- notice handling */

static void fmt_bytes(unsigned long long bytes, wchar_t *out, size_t n)
{
    const wchar_t *units[] = { L"B", L"KB", L"MB", L"GB", L"TB" };
    double v = (double)bytes;
    int u = 0;
    while (v >= 1024.0 && u < 4) { v /= 1024.0; u++; }
    if (u == 0) swprintf(out, n, L"%llu %ls", bytes, units[u]);
    else swprintf(out, n, L"%.1f %ls", v, units[u]);
}

/* Bounded wide-string append; never writes past dst[cap-1]. */
static void safe_cat(wchar_t *dst, size_t cap, const wchar_t *src)
{
    size_t used, room, n;
    if (!dst || !src || cap == 0) return;
    used = wcslen(dst);
    if (used >= cap - 1) return;
    room = cap - used - 1;
    n = wcslen(src);
    if (n > room) n = room;
    memcpy(dst + used, src, n * sizeof(wchar_t));
    dst[used + n] = 0;
}

static void handle_notice(const char *line)
{
    JValue *root, *data;
    const char *type;

    root = json_parse(line);
    if (!root || root->type != JOBJ) { json_free(root); return; }

    type = json_get_str(root, "noticeType", NULL);
    data = json_get(root, "data");

    lock();

    if (type && !strcmp(type, "Tunnels")) {
        int count = (int)json_get_num(data, "count", 0);
        if (count > 0 && g_status.state != TSTATE_CONNECTED) {
            g_status.state = TSTATE_CONNECTED;
            g_status.connected_at = GetTickCount();
        } else if (count == 0 && g_status.state == TSTATE_CONNECTED) {
            g_status.state = TSTATE_CONNECTING;
        }
    } else if (type && !strcmp(type, "BytesTransferred")) {
        unsigned long long sent = (unsigned long long)json_get_num(data, "sent", 0);
        unsigned long long recv = (unsigned long long)json_get_num(data, "received", 0);
        g_status.sent += sent;
        g_status.received += recv;
        stats_add(g_status.client_region, sent, recv);
    } else if (type && !strcmp(type, "TotalBytesTransferred")) {
        g_status.sent = (unsigned long long)json_get_num(data, "sent", g_status.sent);
        g_status.received = (unsigned long long)json_get_num(data, "received", g_status.received);
    } else if (type && !strcmp(type, "ListeningSocksProxyPort")) {
        g_status.socks_port = (int)json_get_num(data, "port", 0);
    } else if (type && !strcmp(type, "ListeningHttpProxyPort")) {
        g_status.http_port = (int)json_get_num(data, "port", 0);
    } else if (type && !strcmp(type, "ClientRegion")) {
        const char *r = json_get_str(data, "region", NULL);
        if (r) {
            wchar_t *w = utf8_to_wchar(r);
            if (w) { wcsncpy(g_status.client_region, w, 63); free_conv(w); }
        }
    } else if (type && !strcmp(type, "ConnectedServerRegion")) {
        const char *r = json_get_str(data, "serverRegion", NULL);
        if (r) {
            wchar_t *w = utf8_to_wchar(r);
            if (w) { wcsncpy(g_status.server_address, w, 127); free_conv(w); }
        }
    } else if (type && !strcmp(type, "ActiveTunnel")) {
        const char *p = json_get_str(data, "protocol", NULL);
        if (p) {
            wchar_t *w = utf8_to_wchar(p);
            if (w) { wcsncpy(g_status.protocol, w, 31); free_conv(w); }
        }
    } else if (type && !strcmp(type, "Homepage")) {
        const char *u = json_get_str(data, "url", NULL);
        if (u && !g_homepage[0]) {
            wchar_t *w = utf8_to_wchar(u);
            if (w) { wcsncpy(g_homepage, w, 511); free_conv(w); }
        }
    } else if (type && !strcmp(type, "AvailableEgressRegions")) {
        JValue *regs = json_get(data, "regions");
        if (regs && regs->type == JARR) {
            JValue *m;
            g_available_regions[0] = 0;
            for (m = regs->items; m; m = m->next) {
                if (m->type != JSTR || !m->str) continue;
                if (g_available_regions[0]) safe_cat(g_available_regions, 1024, L",");
                {
                    wchar_t *w = utf8_to_wchar(m->str);
                    if (w) {
                        safe_cat(g_available_regions, 1024, w);
                        free_conv(w);
                    }
                }
            }
        }
    }

    /* Log every notice so the Logs tab mirrors the Android client. */
    if (type) {
        int level = 0;
        wchar_t detail[256];
        const char *msg = json_get_str(data, "message", NULL);

        if (!strcmp(type, "Error")) level = 2;
        else if (!strcmp(type, "Warning")) level = 1;
        else if (!strcmp(type, "Info") || !strcmp(type, "UserLog")) level = 0;
        else level = 3;   /* diagnostic */

        detail[0] = 0;
        if (msg) {
            wchar_t *w = utf8_to_wchar(msg);
            if (w) { wcsncpy(detail, w, 255); detail[255] = 0; free_conv(w); }
        } else {
            /* Build a compact summary of the payload. */
            JValue *m;
            int first = 1;
            detail[0] = 0;
            for (m = data ? data->items : NULL; m; m = m->next) {
                wchar_t keyw[128], valw[160];
                wchar_t *kw = m->key ? utf8_to_wchar(m->key) : NULL;
                if (!kw) continue;
                wcsncpy(keyw, kw, 127); keyw[127] = 0; free_conv(kw);
                if (m->value && m->value->type == JSTR) {
                    wchar_t *vw = utf8_to_wchar(m->value->str);
                    if (!vw) continue;
                    wcsncpy(valw, vw, 159); valw[159] = 0; free_conv(vw);
                } else if (m->value && m->value->type == JNUM) {
                    swprintf(valw, 160, L"%g", m->value->num);
                } else {
                    continue;
                }
                if (!first) safe_cat(detail, 256, L", ");
                safe_cat(detail, 256, keyw);
                safe_cat(detail, 256, L"=");
                safe_cat(detail, 256, valw);
                first = 0;
            }
        }

        {
            wchar_t typew[96];
            wchar_t *tw = utf8_to_wchar(type);
            if (tw) { wcsncpy(typew, tw, 95); typew[95] = 0; free_conv(tw); }
            else typew[0] = 0;

            if (detail[0]) log_ring_add(level, L"%ls: %ls", typew, detail);
            else           log_ring_add(level, L"%ls", typew);
            wcsncpy(g_status.last_notice, detail[0] ? detail : typew, 511);
            g_status.last_notice[511] = 0;
        }
    }

    unlock();
    json_free(root);

    if (g_main_hwnd && WM_APP_NOTICE)
        PostMessageW(g_main_hwnd, WM_APP_NOTICE, 0, 0);
}

static DWORD WINAPI reader_thread(LPVOID arg)
{
    char buf[8192];
    char carry[16384];
    size_t carry_len = 0;
    DWORD got;

    (void)arg;
    carry[0] = 0;
    while (!g_reader_stop) {
        if (!ReadFile(g_stdout_read, buf, (DWORD)sizeof(buf) - 1, &got, NULL) || got == 0)
            break;
        buf[got] = 0;
        for (;;) {
            char *nl = strchr(buf, '\n');
            if (!nl) break;
            *nl = 0;
            if (carry_len) {
                size_t need = carry_len + strlen(buf) + 1;
                if (need < sizeof(carry)) {
                    strcat(carry, buf);
                    handle_notice(carry);
                }
                carry[0] = 0;
                carry_len = 0;
            } else {
                handle_notice(buf);
            }
            {
                size_t rest = strlen(nl + 1);
                memmove(buf, nl + 1, rest + 1);
            }
        }
        if (buf[0]) {
            size_t l = strlen(buf);
            if (carry_len + l + 1 < sizeof(carry)) {
                memcpy(carry + carry_len, buf, l + 1);
                carry_len += l;
            }
        }
    }
    return 0;
}

/* -------------------------------------------------------------- lifecycle */

int tunnel_core_available(void)
{
    wchar_t path[MAX_PATH_W];
    tunnel_core_path(path, MAX_PATH_W);
    return GetFileAttributesW(path) != INVALID_FILE_ATTRIBUTES;
}

const wchar_t *tunnel_core_path(wchar_t *out, size_t n)
{
    app_install_file(out, n, L"psiphon-tunnel-core.exe");
    return out;
}

void tunnel_set_state(TunnelState st)
{
    lock();
    g_status.state = st;
    unlock();
}

int tunnel_start(const Settings *s)
{
    wchar_t core[MAX_PATH_W], cfgpath[MAX_PATH_W], datadir[MAX_PATH_W];
    wchar_t serverlist[MAX_PATH_W], cmdline[MAX_PATH_W * 2];
    wchar_t quoted_core[MAX_PATH_W + 4];
    char *json = NULL;
    size_t jsonlen = 0;
    FILE *f;
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    SECURITY_ATTRIBUTES sa;
    HANDLE job = NULL;

    if (g_child) return 1;   /* already running */

    if (!tunnel_core_available()) {
        log_ring_add(2, L"%ls", tr(S_CORE_MISSING));
        return 0;
    }

    app_ensure_dirs();
    core_data_dir(datadir, MAX_PATH_W);
    CreateDirectoryW(datadir, NULL);

    if (!core_build_config_json(s, datadir, NULL, &json, &jsonlen) || !json) {
        log_ring_add(2, L"could not build the tunnel-core configuration");
        return 0;
    }
    swprintf(cfgpath, MAX_PATH_W, L"%ls\\psiphon.config", datadir);
    f = _wfopen(cfgpath, L"wb");
    if (!f) { free(json); return 0; }
    fwrite(json, 1, jsonlen, f);
    fclose(f);
    free(json);

    swprintf(serverlist, MAX_PATH_W, L"%ls\\server_entries.txt", datadir);
    if (!write_server_list(serverlist)) serverlist[0] = 0;

    /* Anonymous pipe for the child's stdout. */
    ZeroMemory(&sa, sizeof(sa));
    sa.nLength = sizeof(sa);
    sa.bInheritHandle = TRUE;
    sa.lpSecurityDescriptor = NULL;
    if (!CreatePipe(&g_stdout_read, &g_stdout_write, &sa, 0)) return 0;
    SetHandleInformation(g_stdout_read, HANDLE_FLAG_INHERIT, 0);

    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.hStdOutput = g_stdout_write;
    si.hStdError = g_stdout_write;
    si.hStdInput = NULL;
    si.wShowWindow = SW_HIDE;

    tunnel_core_path(core, MAX_PATH_W);
    swprintf(quoted_core, MAX_PATH_W + 3, L"\"%ls\"", core);

    if (serverlist[0]) {
        swprintf(cmdline, MAX_PATH_W * 2,
                 L"\"%ls\" -config \"%ls\" -serverList \"%ls\" -dataRootDirectory \"%ls\"",
                 core, cfgpath, serverlist, datadir);
    } else {
        swprintf(cmdline, MAX_PATH_W * 2,
                 L"\"%ls\" -config \"%ls\" -dataRootDirectory \"%ls\"",
                 core, cfgpath, datadir);
    }

    ZeroMemory(&pi, sizeof(pi));
    if (!CreateProcessW(NULL, cmdline, NULL, NULL, TRUE,
                        CREATE_NEW_PROCESS_GROUP | CREATE_NO_WINDOW,
                        NULL, datadir, &si, &pi)) {
        log_ring_add(2, L"failed to start psiphon-tunnel-core.exe (error %lu)",
                     (unsigned long)GetLastError());
        CloseHandle(g_stdout_read);
        CloseHandle(g_stdout_write);
        g_stdout_read = g_stdout_write = NULL;
        return 0;
    }

    /* Tie the child's lifetime to ours. */
    job = CreateJobObjectW(NULL, NULL);
    if (job) {
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION jeli;
        ZeroMemory(&jeli, sizeof(jeli));
        jeli.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        SetInformationJobObject(job, JobObjectExtendedLimitInformation,
                                &jeli, sizeof(jeli));
        AssignProcessToJobObject(job, pi.hProcess);
    }

    CloseHandle(g_stdout_write);
    g_stdout_write = NULL;
    g_child = pi.hProcess;
    CloseHandle(pi.hThread);

    lock();
    ZeroMemory(&g_status, sizeof(g_status));
    g_status.state = TSTATE_CONNECTING;
    g_status.started_at = GetTickCount();
    wcsncpy(g_status.client_region, s->region, 63);
    unlock();

    g_reader_stop = 0;
    g_reader_thread = CreateThread(NULL, 0, reader_thread, NULL, 0, NULL);

    log_ring_add(0, L"starting the tunnel (%ls)", region_display_name(s->region));
    return 1;
}

void tunnel_stop(void)
{
    if (!g_child) return;

    lock();
    g_status.state = TSTATE_STOPPING;
    unlock();

    /* Ask the core to shut down cleanly (it handles CTRL_BREAK). */
    GenerateConsoleCtrlEvent(CTRL_BREAK_EVENT, GetProcessId(g_child));

    if (WaitForSingleObject(g_child, 6000) == WAIT_TIMEOUT) {
        TerminateProcess(g_child, 1);
        WaitForSingleObject(g_child, 2000);
    }

    g_reader_stop = 1;
    if (g_reader_thread) {
        WaitForSingleObject(g_reader_thread, 3000);
        CloseHandle(g_reader_thread);
        g_reader_thread = NULL;
    }
    if (g_stdout_read) { CloseHandle(g_stdout_read); g_stdout_read = NULL; }
    CloseHandle(g_child);
    g_child = NULL;

    lock();
    g_status.state = TSTATE_STOPPED;
    g_status.socks_port = 0;
    g_status.http_port = 0;
    unlock();

    log_ring_add(0, L"tunnel stopped");
    stats_save();
    if (g_main_hwnd && WM_APP_NOTICE) PostMessageW(g_main_hwnd, WM_APP_NOTICE, 0, 0);
}

void tunnel_pump(void)
{
    if (g_child && WaitForSingleObject(g_child, 0) == WAIT_OBJECT_0) {
        /* The core exited on its own. */
        CloseHandle(g_child);
        g_child = NULL;
        g_reader_stop = 1;
        if (g_reader_thread) { WaitForSingleObject(g_reader_thread, 2000); CloseHandle(g_reader_thread); g_reader_thread = NULL; }
        if (g_stdout_read) { CloseHandle(g_stdout_read); g_stdout_read = NULL; }
        lock();
        if (g_status.state != TSTATE_STOPPED) {
            g_status.state = TSTATE_STOPPED;
            log_ring_add(1, L"the tunnel engine exited unexpectedly");
        }
        unlock();
        stats_save();
    }
}

/* Elapsed / status text helpers shared with the UI. */
void tunnel_elapsed_text(wchar_t *out, size_t n)
{
    unsigned long ms;
    if (g_status.state != TSTATE_CONNECTED || !g_status.connected_at) {
        wcsncpy(out, L"--:--:--", n);
        out[n - 1] = 0;
        return;
    }
    ms = GetTickCount() - g_status.connected_at;
    swprintf(out, n, L"%02lu:%02lu:%02lu",
             (ms / 3600000UL), (ms / 60000UL) % 60UL, (ms / 1000UL) % 60UL);
}

void tunnel_bytes_text(unsigned long long bytes, wchar_t *out, size_t n)
{
    fmt_bytes(bytes, out, n);
}

#endif /* _WIN32 */

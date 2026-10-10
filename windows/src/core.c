/*
 * Shir o Khorshid for Windows - settings, log ring, statistics and
 * psiphon-tunnel-core configuration assembly.
 * GPLv3. See shir.h for the full notice.
 */

#include "shir.h"
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#  include <shlobj.h>
#  include <knownfolders.h>
#else
#  include <wchar.h>
#  include <sys/stat.h>
#endif

/* ------------------------------------------------------------ directories */

static void get_known_dir(wchar_t *out, size_t n, int local_appdata)
{
    out[0] = 0;
#ifdef _WIN32
    {
        PWSTR p = NULL;
        HRESULT hr;
        hr = SHGetKnownFolderPath(local_appdata ? &FOLDERID_LocalAppData
                                                : &FOLDERID_RoamingAppData,
                                  0, NULL, &p);
        if (SUCCEEDED(hr) && p) {
            wcsncpy(out, p, n - 1);
            out[n - 1] = 0;
            CoTaskMemFree(p);
        } else {
            GetEnvironmentVariableW(L"APPDATA", out, (DWORD)n);
        }
    }
#else
    (void)local_appdata;
    {
        const char *home = getenv("HOME");
        if (home) mbstowcs(out, home, n - 1);
    }
#endif
}

void settings_get_path(wchar_t *out, size_t n)
{
    wchar_t dir[MAX_PATH_W];
    get_known_dir(dir, MAX_PATH_W, 0);
    swprintf(out, n, L"%ls\\ShirOKhorshid\\settings.json", dir);
}

void app_data_dir(wchar_t *out, size_t n)
{
    get_known_dir(out, n, 0);
    wcsncat(out, L"\\ShirOKhorshid", n - wcslen(out) - 1);
}

void app_data_file(wchar_t *out, size_t n, const wchar_t *name)
{
    wchar_t dir[MAX_PATH_W];
    get_known_dir(dir, MAX_PATH_W, 0);
    swprintf(out, n, L"%ls\\ShirOKhorshid\\%ls", dir, name);
}

/* Tunnel-core data store (server entries, dial parameters, ...). */
void core_data_dir(wchar_t *out, size_t n)
{
    wchar_t dir[MAX_PATH_W];
    get_known_dir(dir, MAX_PATH_W, 1);
    swprintf(out, n, L"%ls\\ShirOKhorshid\\tunnel-core", dir);
}

#ifdef _WIN32
static void ensure_dir(const wchar_t *path)
{
    CreateDirectoryW(path, NULL);
}

void app_ensure_dirs(void)
{
    wchar_t dir[MAX_PATH_W], sub[MAX_PATH_W];
    get_known_dir(dir, MAX_PATH_W, 0);
    swprintf(sub, MAX_PATH_W, L"%ls\\ShirOKhorshid", dir);
    ensure_dir(sub);
    core_data_dir(sub, MAX_PATH_W);
    ensure_dir(sub);
}
#endif

/* --------------------------------------------------------------- settings */

void settings_init(Settings *s)
{
    memset(s, 0, sizeof(*s));
    s->tunnel_whole_device    = 1;
    s->set_system_proxy       = 1;
    s->auto_open_homepage     = 1;
    s->disable_timeouts       = 0;
    s->notifications_sound    = 1;
    s->minimize_to_tray       = 1;
    s->start_with_windows     = 0;
    s->lan_proxy_sharing      = 0;
    s->lan_proxy_auth         = 0;
    s->upstream_proxy_enabled = 0;
    s->upstream_proxy_use_https = 0;
    s->socks_port             = 0;    /* 0 = let the core pick a free port */
    s->http_port              = 0;
    s->persian                = 1;
    s->auto_connect           = 0;
    wcsncpy(s->region, L"Any", 31);
    wcsncpy(s->protocol, L"", 31);
    wcsncpy(s->upstream_host, L"", 255);
    s->upstream_port = 8080;
}

int settings_load(Settings *s, const wchar_t *path)
{
    FILE *f;
    long n;
    char *buf, *u8;
    JValue *root;
    wchar_t *w;
    int ok = 0;

    settings_init(s);
    f = _wfopen(path, L"rb");
    if (!f) return 0;
    fseek(f, 0, SEEK_END);
    n = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (n <= 0) { fclose(f); return 0; }
    buf = (char *)malloc((size_t)n + 1);
    if (!buf) { fclose(f); return 0; }
    if (fread(buf, 1, (size_t)n, f) != (size_t)n) { free(buf); fclose(f); return 0; }
    buf[n] = 0;
    fclose(f);

    root = json_parse(buf);
    free(buf);
    if (!root) return 0;

#define RBOOL(field, key, dflt) s->field = json_get_bool(root, key, dflt)
#define RNUM(field, key, dflt)  s->field = (int)json_get_num(root, key, dflt)
#define RSTR(field, key, dflt)                                          \
    do {                                                                \
        const char *v = json_get_str(root, key, NULL);                  \
        if (v) { w = utf8_to_wchar(v); if (w) {                         \
            wcsncpy(s->field, w, (int)(sizeof(s->field)/sizeof(wchar_t)) - 1); \
            free_conv(w); }                                             \
        } else { wcsncpy(s->field, dflt, (int)(sizeof(s->field)/sizeof(wchar_t)) - 1); } \
    } while (0)

    RBOOL(tunnel_whole_device,    "tunnelWholeDevice",    s->tunnel_whole_device);
    RBOOL(set_system_proxy,       "setSystemProxy",       s->set_system_proxy);
    RBOOL(auto_open_homepage,     "autoOpenHomepage",     s->auto_open_homepage);
    RBOOL(disable_timeouts,       "disableTimeouts",      s->disable_timeouts);
    RBOOL(notifications_sound,    "notificationsSound",   s->notifications_sound);
    RBOOL(minimize_to_tray,       "minimizeToTray",       s->minimize_to_tray);
    RBOOL(start_with_windows,     "startWithWindows",     s->start_with_windows);
    RBOOL(lan_proxy_sharing,      "lanProxySharing",      s->lan_proxy_sharing);
    RBOOL(lan_proxy_auth,         "lanProxyAuth",         s->lan_proxy_auth);
    RBOOL(upstream_proxy_enabled, "upstreamProxyEnabled", s->upstream_proxy_enabled);
    RBOOL(upstream_proxy_use_https, "upstreamProxyUseHttps", s->upstream_proxy_use_https);
    RBOOL(persian,                "persian",              s->persian);
    RBOOL(auto_connect,           "autoConnect",          s->auto_connect);
    RNUM(socks_port,    "socksPort", s->socks_port);
    RNUM(http_port,     "httpPort",  s->http_port);
    RNUM(upstream_port, "upstreamPort", s->upstream_port);
    RSTR(region,        "region",   L"Any");
    RSTR(protocol,      "protocol", L"");
    RSTR(upstream_host, "upstreamHost", L"");
    RSTR(upstream_username, "upstreamUsername", L"");
    RSTR(upstream_password, "upstreamPassword", L"");
    RSTR(lan_user,      "lanUser", L"");
    RSTR(lan_pass,      "lanPass", L"");
#undef RBOOL
#undef RNUM
#undef RSTR
    (void)u8;
    json_free(root);
    ok = 1;
    return ok;
}

int settings_save(const Settings *s, const wchar_t *path)
{
    JsonBuf b;
    FILE *f;
    wchar_t dir[MAX_PATH_W];
    const wchar_t *slash;
    int ok = 0;

    /* make sure the containing directory exists */
    wcsncpy(dir, path, MAX_PATH_W - 1);
    dir[MAX_PATH_W - 1] = 0;
    slash = wcsrchr(dir, L'\\');
    if (!slash) slash = wcsrchr(dir, L'/');
    if (slash) {
        wchar_t parent[MAX_PATH_W];
        size_t k = (size_t)(slash - dir);
        wcsncpy(parent, dir, k);
        parent[k] = 0;
#ifdef _WIN32
        CreateDirectoryW(parent, NULL);
#else
        mkdir("dummy", 0755);
#endif
    }

    jb_init(&b);
    jb_begin(&b, '{');
    jb_bool(&b, "tunnelWholeDevice", s->tunnel_whole_device);
    jb_bool(&b, "setSystemProxy", s->set_system_proxy);
    jb_bool(&b, "autoOpenHomepage", s->auto_open_homepage);
    jb_bool(&b, "disableTimeouts", s->disable_timeouts);
    jb_bool(&b, "notificationsSound", s->notifications_sound);
    jb_bool(&b, "minimizeToTray", s->minimize_to_tray);
    jb_bool(&b, "startWithWindows", s->start_with_windows);
    jb_bool(&b, "lanProxySharing", s->lan_proxy_sharing);
    jb_bool(&b, "lanProxyAuth", s->lan_proxy_auth);
    jb_bool(&b, "upstreamProxyEnabled", s->upstream_proxy_enabled);
    jb_bool(&b, "upstreamProxyUseHttps", s->upstream_proxy_use_https);
    jb_bool(&b, "persian", s->persian);
    jb_bool(&b, "autoConnect", s->auto_connect);
    jb_num(&b, "socksPort", s->socks_port);
    jb_num(&b, "httpPort", s->http_port);
    jb_num(&b, "upstreamPort", s->upstream_port);
    jb_str_w(&b, "region", s->region);
    jb_str_w(&b, "protocol", s->protocol);
    jb_str_w(&b, "upstreamHost", s->upstream_host);
    jb_str_w(&b, "upstreamUsername", s->upstream_username);
    jb_str_w(&b, "upstreamPassword", s->upstream_password);
    jb_str_w(&b, "lanUser", s->lan_user);
    jb_str_w(&b, "lanPass", s->lan_pass);
    jb_end(&b, '}');

    f = _wfopen(path, L"wb");
    if (f) {
        size_t want = b.len;
        ok = (fwrite(b.data, 1, want, f) == want);
        fclose(f);
    }
    jb_free(&b);
    return ok;
}

/* -------------------------------------------------------------- log ring */

static LogEntry g_logs[LOG_CAPACITY];
static int g_log_head = 0;      /* next write slot */
static int g_log_count = 0;
static int g_log_total = 0;     /* lifetime count, never wraps */

void log_ring_add(int level, const wchar_t *fmt, ...)
{
    va_list ap;
    LogEntry *e;
    wchar_t stamp[64];
    time_t t;
    struct tm tmnow;

    e = &g_logs[g_log_head];
    g_log_head = (g_log_head + 1) % LOG_CAPACITY;
    if (g_log_count < LOG_CAPACITY) g_log_count++;
    g_log_total++;

    t = time(NULL);
#ifdef _WIN32
    localtime_s(&tmnow, &t);
#else
    tmnow = *localtime(&t);
#endif
    swprintf(stamp, 64, L"%02d:%02d:%02d  ",
             tmnow.tm_hour, tmnow.tm_min, tmnow.tm_sec);

    wcscpy(e->text, stamp);
    {
        size_t used = wcslen(stamp);
        va_start(ap, fmt);
        vswprintf(e->text + used, MAX_STR - used - 1, fmt, ap);
        va_end(ap);
    }
    e->text[MAX_STR - 1] = 0;
    e->level = level;
    e->when = (unsigned long)time(NULL);
}

int log_ring_count(void) { return g_log_count; }
int log_ring_total(void) { return g_log_total; }

const LogEntry *log_ring_at(int i)
{
    /* i = 0 is the oldest retained entry */
    int idx;
    if (i < 0 || i >= g_log_count) return NULL;
    idx = (g_log_head - g_log_count + i + LOG_CAPACITY) % LOG_CAPACITY;
    return &g_logs[idx];
}

void log_ring_clear(void)
{
    g_log_head = 0;
    g_log_count = 0;
}

/* ------------------------------------------------------------- statistics */

#define STATS_MAX 64
static RegionStat g_stats[STATS_MAX];
static int g_stats_count = 0;

void stats_add(const wchar_t *region, unsigned long long sent,
               unsigned long long received)
{
    int i;
    const wchar_t *name = region ? region : L"Any";
    if (!*name) name = L"Any";
    for (i = 0; i < g_stats_count; i++) {
        if (!wcscmp(g_stats[i].region, name)) {
            g_stats[i].sent += sent;
            g_stats[i].received += received;
            return;
        }
    }
    if (g_stats_count < STATS_MAX) {
        wcsncpy(g_stats[g_stats_count].region, name, 31);
        g_stats[g_stats_count].region[31] = 0;
        g_stats[g_stats_count].sent = sent;
        g_stats[g_stats_count].received = received;
        g_stats_count++;
    }
}

int stats_count(void) { return g_stats_count; }
const RegionStat *stats_at(int i)
{
    if (i < 0 || i >= g_stats_count) return NULL;
    return &g_stats[i];
}

void stats_reset(void) { g_stats_count = 0; }

void stats_load(void)
{
    wchar_t path[MAX_PATH_W];
    FILE *f;
    long n;
    char *buf;
    JValue *root, *arr, *m;

    g_stats_count = 0;
    app_data_file(path, MAX_PATH_W, L"stats.json");
    f = _wfopen(path, L"rb");
    if (!f) return;
    fseek(f, 0, SEEK_END);
    n = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (n <= 0) { fclose(f); return; }
    buf = (char *)malloc((size_t)n + 1);
    if (!buf) { fclose(f); return; }
    if (fread(buf, 1, (size_t)n, f) != (size_t)n) { free(buf); fclose(f); return; }
    buf[n] = 0;
    fclose(f);

    root = json_parse(buf);
    free(buf);
    if (!root) return;
    arr = json_get(root, "regions");
    if (!arr || arr->type != JARR) { json_free(root); return; }
    for (m = arr->items; m && g_stats_count < STATS_MAX; m = m->next) {
        const char *r = json_get_str(m, "region", "Any");
        wchar_t *w = utf8_to_wchar(r);
        if (!w) continue;
        wcsncpy(g_stats[g_stats_count].region, w, 31);
        g_stats[g_stats_count].region[31] = 0;
        free_conv(w);
        g_stats[g_stats_count].sent = (unsigned long long)json_get_num(m, "sent", 0);
        g_stats[g_stats_count].received = (unsigned long long)json_get_num(m, "received", 0);
        g_stats_count++;
    }
    json_free(root);
}

void stats_save(void)
{
    wchar_t path[MAX_PATH_W], dir[MAX_PATH_W];
    JsonBuf b;
    FILE *f;
    int i;

    get_known_dir(dir, MAX_PATH_W, 0);
    wcsncat(dir, L"\\ShirOKhorshid", MAX_PATH_W - wcslen(dir) - 1);
#ifdef _WIN32
    CreateDirectoryW(dir, NULL);
#endif
    app_data_file(path, MAX_PATH_W, L"stats.json");

    jb_init(&b);
    jb_begin(&b, '{');
    jb_key(&b, "regions");
    jb_begin(&b, '[');
    for (i = 0; i < g_stats_count; i++) {
        jb_begin(&b, '{');
        jb_str_w(&b, "region", g_stats[i].region);
        jb_num(&b, "sent", (double)g_stats[i].sent);
        jb_num(&b, "received", (double)g_stats[i].received);
        jb_end(&b, '}');
    }
    jb_end(&b, ']');
    jb_end(&b, '}');

    f = _wfopen(path, L"wb");
    if (f) { fwrite(b.data, 1, b.len, f); fclose(f); }
    jb_free(&b);
}

/* ---------------------------------------------------------- tunnel config */

/*
 * The "embedded values" (propagation channel id, sponsor id, signature
 * keys, remote server list URLs and the embedded server entry list) are
 * supplied by whoever builds the app -- exactly like the Android build's
 * generated EmbeddedValues.java.  They live next to the executable in
 * values.json and are baked in by tools/gen_values.py at build time.
 */
int values_lookup_string(const wchar_t *key, wchar_t *out, size_t n);
int values_lookup_array(const wchar_t *key, char ***out, int *count);

int core_build_config_json(const Settings *s, const wchar_t *data_dir,
                           const wchar_t *server_list_path,
                           char **out_json, size_t *out_len)
{
    JsonBuf b;
    wchar_t tmp[MAX_PATH_W];
    char *u8;

    if (!out_json) return 0;
    *out_json = NULL;
    if (out_len) *out_len = 0;

    jb_init(&b);
    jb_begin(&b, '{');

    /* Identity ------------------------------------------------------- */
    if (values_lookup_string(L"PropagationChannelId", tmp, MAX_PATH_W))
        jb_str_w(&b, "PropagationChannelId", tmp);
    if (values_lookup_string(L"SponsorId", tmp, MAX_PATH_W))
        jb_str_w(&b, "SponsorId", tmp);
    if (values_lookup_string(L"ClientVersion", tmp, MAX_PATH_W))
        jb_str_w(&b, "ClientVersion", tmp);
    else
        jb_str(&b, "ClientVersion", "45");

    if (values_lookup_string(L"RemoteServerListSignaturePublicKey", tmp, MAX_PATH_W))
        jb_str_w(&b, "RemoteServerListSignaturePublicKey", tmp);
    if (values_lookup_string(L"ServerEntrySignaturePublicKey", tmp, MAX_PATH_W))
        jb_str_w(&b, "ServerEntrySignaturePublicKey", tmp);

    /* Server list sources -------------------------------------------- */
    {
        char **items = NULL;
        int n = 0, i;
        if (values_lookup_array(L"RemoteServerListURLs", &items, &n) && n > 0) {
            jb_key(&b, "RemoteServerListURLs");
            jb_begin(&b, '[');
            for (i = 0; i < n; i++) jb_item_str(&b, items[i]);
            jb_end(&b, ']');
            for (i = 0; i < n; i++) free(items[i]);
        }
        free(items);
        items = NULL; n = 0;
        if (values_lookup_array(L"ObfuscatedServerListRootURLs", &items, &n) && n > 0) {
            jb_key(&b, "ObfuscatedServerListRootURLs");
            jb_begin(&b, '[');
            for (i = 0; i < n; i++) jb_item_str(&b, items[i]);
            jb_end(&b, ']');
            for (i = 0; i < n; i++) free(items[i]);
        }
        free(items);
    }

    /* Local proxies --------------------------------------------------- */
    jb_num(&b, "LocalSocksProxyPort", s->socks_port);
    jb_num(&b, "LocalHttpProxyPort", s->http_port);
    if (s->lan_proxy_sharing) jb_str(&b, "ListenInterface", "0.0.0.0");
    else                      jb_str(&b, "ListenInterface", "127.0.0.1");

    /* Region / protocol ----------------------------------------------- */
    if (s->region[0] && wcscmp(s->region, L"Any") != 0)
        jb_str_w(&b, "EgressRegion", s->region);

    if (s->protocol[0]) {
        const char *p = NULL;
        if (!wcscmp(s->protocol, L"SSH")) p = "SSH";
        else if (!wcscmp(s->protocol, L"OSSH")) p = "OSSH";
        else if (!wcscmp(s->protocol, L"UNFRONTED-MEEK-OSSH")) p = "UNFRONTED-MEEK-OSSH";
        else if (!wcscmp(s->protocol, L"UNFRONTED-MEEK-HTTPS")) p = "UNFRONTED-MEEK-HTTPS";
        else if (!wcscmp(s->protocol, L"FRONTED-MEEK-OSSH")) p = "FRONTED-MEEK-OSSH";
        else if (!wcscmp(s->protocol, L"FRONTED-MEEK-HTTP")) p = "FRONTED-MEEK-HTTP";
        else if (!wcscmp(s->protocol, L"QUIC-OSSH")) p = "QUIC-OSSH";
        else if (!wcscmp(s->protocol, L"TLS-OSSH")) p = "TLS-OSSH";
        if (p) {
            jb_key(&b, "LimitTunnelProtocols");
            jb_begin(&b, '[');
            jb_item_str(&b, p);
            jb_end(&b, ']');
        }
    }

    /* Upstream proxy --------------------------------------------------- */
    if (s->upstream_proxy_enabled && s->upstream_host[0]) {
        char url[640];
        char host[512], user[256], pass[256];
        u8 = wchar_to_utf8(s->upstream_host);
        snprintf(host, sizeof host, "%s", u8 ? u8 : "");
        free_conv(u8);
        u8 = wchar_to_utf8(s->upstream_username);
        snprintf(user, sizeof user, "%s", u8 ? u8 : "");
        free_conv(u8);
        u8 = wchar_to_utf8(s->upstream_password);
        snprintf(pass, sizeof pass, "%s", u8 ? u8 : "");
        free_conv(u8);
        if (user[0]) {
            snprintf(url, sizeof url, "%s://%s:%s@%s:%d",
                     s->upstream_proxy_use_https ? "https" : "http",
                     user, pass, host, s->upstream_port);
        } else {
            snprintf(url, sizeof url, "%s://%s:%d",
                     s->upstream_proxy_use_https ? "https" : "http",
                     host, s->upstream_port);
        }
        jb_str(&b, "UpstreamProxyUrl", url);
    }

    /* Timeouts ---------------------------------------------------------- */
    if (s->disable_timeouts) {
        jb_num(&b, "EstablishTunnelTimeoutSeconds", 0);
        jb_num(&b, "TunnelConnectTimeoutSeconds", 0);
    }

    /* Storage & notices -------------------------------------------------- */
    jb_bool(&b, "EmitBytesTransferred", 1);
    jb_bool(&b, "EmitDiagnosticNotices", 1);
    jb_str_w(&b, "DataRootDirectory", data_dir);

    jb_end(&b, '}');

    (void)server_list_path;
    *out_json = b.data;
    if (out_len) *out_len = b.len;
    b.data = NULL;      /* ownership transferred */
    return 1;
}

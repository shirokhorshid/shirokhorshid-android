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
    wcsncpy(s->upstream_host, L"", 255);
    s->upstream_port = 8080;

    /* Defaults match the Android client's more_options_preferences.xml:
       protocolSelection=auto, conduitMode=auto, conduitTimeout=180,
       beastMode=true, rejectCensoredCountryProxies=true. */
    wcsncpy(s->protocol_selection, L"auto", sizeof(s->protocol_selection) / sizeof(wchar_t) - 1);
    wcsncpy(s->conduit_mode, L"auto", sizeof(s->conduit_mode) / sizeof(wchar_t) - 1);
    s->conduit_timeout         = 180;
    s->beast_mode              = 1;
    s->reject_censored_proxies = 1;
    s->diagnostics_upload      = 1;
    s->cdn_ip_list[0]          = 0;
    s->cdn_sni[0]              = 0;
    s->upstream_domain[0]      = 0;
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
    RSTR(protocol_selection, "protocolSelection", L"auto");
    RSTR(conduit_mode,  "conduitMode", L"auto");
    RSTR(cdn_ip_list,   "cdnIpList", L"");
    RSTR(cdn_sni,       "cdnSni", L"");
    RNUM(conduit_timeout, "conduitTimeout", s->conduit_timeout);
    RBOOL(beast_mode,   "beastMode", s->beast_mode);
    RBOOL(reject_censored_proxies, "rejectCensoredProxies", s->reject_censored_proxies);
    RBOOL(diagnostics_upload, "diagnosticsUpload", s->diagnostics_upload);
    RSTR(upstream_host, "upstreamHost", L"");
    RSTR(upstream_domain, "upstreamDomain", L"");
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
    jb_str_w(&b, "protocolSelection", s->protocol_selection);
    jb_str_w(&b, "conduitMode", s->conduit_mode);
    jb_num(&b, "conduitTimeout", s->conduit_timeout);
    jb_str_w(&b, "cdnIpList", s->cdn_ip_list);
    jb_str_w(&b, "cdnSni", s->cdn_sni);
    jb_bool(&b, "beastMode", s->beast_mode);
    jb_bool(&b, "rejectCensoredProxies", s->reject_censored_proxies);
    jb_bool(&b, "diagnosticsUpload", s->diagnostics_upload);
    jb_str_w(&b, "upstreamHost", s->upstream_host);
    jb_str_w(&b, "upstreamDomain", s->upstream_domain);
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

/* ------------------------------------------------- protocol tables
   These mirror TunnelManager.java in the Android client so both builds ask the
   tunnel core for exactly the same transports.                              */

static const char *const kConduitProtocols[] = {
    "INPROXY-WEBRTC-SSH",
    "INPROXY-WEBRTC-OSSH",
    "INPROXY-WEBRTC-TLS-OSSH",
    "INPROXY-WEBRTC-UNFRONTED-MEEK-OSSH",
    "INPROXY-WEBRTC-UNFRONTED-MEEK-HTTPS-OSSH",
    "INPROXY-WEBRTC-UNFRONTED-MEEK-SESSION-TICKET-OSSH",
    "INPROXY-WEBRTC-FRONTED-MEEK-OSSH",
    "INPROXY-WEBRTC-FRONTED-MEEK-HTTP-OSSH",
    "INPROXY-WEBRTC-QUIC-OSSH",
    "INPROXY-WEBRTC-FRONTED-MEEK-QUIC-OSSH",
    "INPROXY-WEBRTC-SHADOWSOCKS-OSSH",
};

static const char *const kCdnFrontingProtocols[] = {
    "FRONTED-MEEK-CDN-OSSH",
    "FRONTED-MEEK-CDN-HTTP-OSSH",
    "FRONTED-MEEK-CDN-QUIC-OSSH",
};

static const char *const kDirectProtocols[] = {
    "SSH",
    "OSSH",
    "TLS-OSSH",
    "UNFRONTED-MEEK-OSSH",
    "UNFRONTED-MEEK-HTTPS-OSSH",
    "UNFRONTED-MEEK-SESSION-TICKET-OSSH",
    "QUIC-OSSH",
    "SHADOWSOCKS-OSSH",
    "FRONTED-MEEK-OSSH",
    "FRONTED-MEEK-CDN-OSSH",
    "FRONTED-MEEK-HTTP-OSSH",
    "FRONTED-MEEK-CDN-HTTP-OSSH",
    "FRONTED-MEEK-QUIC-OSSH",
    "FRONTED-MEEK-CDN-QUIC-OSSH",
};

/* CENSORED_COUNTRY_CODES in the Android client. */
static const char *const kCensoredCountryCodes[] = {
    "IR",   /* Iran         */
    "CN",   /* China        */
    "RU",   /* Russia       */
    "BY",   /* Belarus      */
    "TM",   /* Turkmenistan */
    "KP"    /* North Korea  */
};

static const char *const kEdgeDialAddresses[] = {
    "23.215.0.206", "23.215.0.203", "23.212.250.91", "23.212.250.78",
    "23.12.147.13", "23.12.147.29", "23.73.207.8",   "23.73.207.15",
    "92.123.102.43"
};

static const char *const kEdgeOverrideIds[] = {
    "edge-a-1", "edge-a-2", "edge-b-1", "edge-b-2", "edge-c-1",
    "edge-c-2", "edge-d-1", "edge-d-2", "edge-original"
};

static const char *const kEdgeVerifyExtra[] = {
    "a248.e.akamai.net", "a.akamaized.net", "a.akamaized-staging.net",
    "a.akamaihd.net", "a.akamaihd-staging.net", "www.akamai.com"
};

static const char *const kFastlyVerifyServerNames[] = {
    "www.python.org", "pypi.org", "fastly.com", "www.fastly.com",
    "developer.fastly.com", "githubassets.com", "github.com", "github.io",
    "githubusercontent.com"
};

/* conduit_mode_status in the Android client: "Conduit mode: %s".  When auto
   mode has already fallen back we render it as "auto → public". */
static const wchar_t *fmt_conduit_mode(const wchar_t *a, const wchar_t *b)
{
    static wchar_t buf[256];
    const wchar_t *fmt = tr(S_CONDUIT_MODE_STATUS);
    if (!wcschr(fmt, L'%')) fmt = L"%ls";
    if (b && b[0]) {
        wchar_t joined[192];
        _snwprintf(joined, 191, L"%ls \x2192 %ls", a, b);
        joined[191] = 0;
        _snwprintf(buf, 255, fmt, joined);
    } else {
        _snwprintf(buf, 255, fmt, a);
    }
    buf[255] = 0;
    return buf;
}

/* Set while a conduit session is running in auto mode: after the fallback
   timeout we restart the tunnel using public conduits instead of the private
   Shir o Khorshid compartment. */
static volatile int g_conduit_fallback = 0;

void core_set_conduit_fallback(int on) { g_conduit_fallback = on ? 1 : 0; }
int  core_get_conduit_fallback(void)   { return g_conduit_fallback; }

/* Split a user supplied list on whitespace / commas / semicolons, dropping
   empties and duplicates - the C equivalent of split("[\\s,;]+") + a Set. */
static int split_tokens(const wchar_t *src, wchar_t out[][128], int max)
{
    const wchar_t *p = src;
    int n = 0, i;
    if (!src) return 0;
    while (*p && n < max) {
        wchar_t tok[128];
        int len = 0;
        while (*p && (*p == L' ' || *p == L'\t' || *p == L'\r' || *p == L'\n' ||
                      *p == L',' || *p == L';')) p++;
        if (!*p) break;
        while (*p && !(*p == L' ' || *p == L'\t' || *p == L'\r' || *p == L'\n' ||
                       *p == L',' || *p == L';')) {
            if (len < 127) tok[len++] = *p;
            p++;
        }
        tok[len] = 0;
        if (!len) continue;
        for (i = 0; i < n; i++) if (!wcscmp(out[i], tok)) break;
        if (i == n) { wcsncpy(out[n], tok, 127); out[n][127] = 0; n++; }
    }
    return n;
}

/* Loosely validate an IPv4 address or IPv4 CIDR, matching isValidIPv4Address /
   isValidIPv4Cidr so malformed entries are silently ignored. */
static int is_ipv4_or_cidr(const wchar_t *s)
{
    /* isValidIPv4Address(): exactly four dot-separated parts, each 1-3
       digits with a value of 0-255.  isValidIPv4Cidr(): the same followed by
       "/0".."/32". */
    int octets = 0, digits = 0, value = 0, has_cidr = 0, mask = 0, mask_digits = 0;
    const wchar_t *p = s;
    if (!s || !*s) return 0;
    for (; *p; p++) {
        if (*p >= L'0' && *p <= L'9') {
            if (has_cidr) {
                mask_digits++;
                if (mask_digits > 2) return 0;
                mask = mask * 10 + (*p - L'0');
                if (mask > 32) return 0;
            } else {
                digits++;
                if (digits > 3) return 0;
                value = value * 10 + (*p - L'0');
                if (value > 255) return 0;
            }
        } else if (*p == L'.') {
            if (has_cidr || !digits) return 0;
            octets++;
            if (octets > 3) return 0;
            digits = 0; value = 0;
        } else if (*p == L'/') {
            if (!digits || octets != 3 || has_cidr) return 0;
            has_cidr = 1;
        } else {
            return 0;
        }
    }
    if (octets != 3 || !digits) return 0;
    if (has_cidr && !mask_digits) return 0;
    return 1;
}

/* isValidHostname: dot separated labels of letters, digits and hyphens. */
static int is_hostname(const wchar_t *s)
{
    const wchar_t *p = s;
    int len, label_len = 0, labels = 0;
    if (!s) return 0;
    len = (int)wcslen(s);
    if (len < 1 || len > 253) return 0;
    for (p = s; *p; p++) {
        if (*p == L'.') {
            if (label_len < 1 || label_len > 63) return 0;
            labels++;
            label_len = 0;
        } else if ((*p >= L'a' && *p <= L'z') || (*p >= L'0' && *p <= L'9') ||
                   *p == L'-' || *p == L'_') {
            label_len++;
        } else {
            return 0;
        }
    }
    if (label_len < 1 || label_len > 63) return 0;
    return labels >= 1;
}

/* normalizeHostname(): trim, lowercase, drop a single trailing dot. */
static void normalize_hostname(wchar_t *s)
{
    size_t n;
    wchar_t *p;
    if (!s) return;
    while (*s == L' ' || *s == L'\t' || *s == L'\r' || *s == L'\n') {
        wchar_t *q = s;
        while (*q) { *q = *(q + 1); q++; }
    }
    n = wcslen(s);
    while (n && (s[n - 1] == L' ' || s[n - 1] == L'\t' ||
                 s[n - 1] == L'\r' || s[n - 1] == L'\n')) s[--n] = 0;
    if (n && s[n - 1] == L'.') s[--n] = 0;
    for (p = s; *p; p++)
        if (*p >= L'A' && *p <= L'Z') *p = (wchar_t)(*p - L'A' + L'a');
}

static int file_exists(const wchar_t *path)
{
    FILE *f = _wfopen(path, L"rb");
    if (!f) return 0;
    fclose(f);
    return 1;
}

/* Look for a GeoIP database next to the app (install dir, then %APPDATA%).
   The Android client ships GeoLite2-Country.mmdb as an asset; on Windows we
   accept a user-supplied copy so InproxyRejectProxyCountryCodes can work. */
static int geoip_database_path(wchar_t *out, size_t n)
{
    static const wchar_t *const names[] = {
        L"GeoLite2-Country.mmdb",
        L"GeoIP.dat",
        L"GeoLite2-Country.mmdb.gz"
    };
    wchar_t dir[MAX_PATH_W];
    size_t i;
    for (i = 0; i < sizeof(names) / sizeof(names[0]); i++) {
        app_install_file(dir, MAX_PATH_W, names[i]);
        if (file_exists(dir)) { wcsncpy(out, dir, n - 1); out[n - 1] = 0; return 1; }
        app_data_file(dir, MAX_PATH_W, names[i]);
        if (file_exists(dir)) { wcsncpy(out, dir, n - 1); out[n - 1] = 0; return 1; }
    }
    return 0;
}

static void put_string_array_w(JsonBuf *b, wchar_t items[][128], int n)
{
    int i;
    char *u8;
    jb_begin(b, '[');
    for (i = 0; i < n; i++) {
        u8 = wchar_to_utf8(items[i]);
        jb_item_str(b, u8 ? u8 : "");
        free_conv(u8);
    }
    jb_end(b, ']');
}

static void put_edge_override(JsonBuf *b, const char *id, const char *ip,
                              const char *sni)
{
    int i;
    jb_begin(b, '{');
    jb_str(b, "OverrideID", id);
    jb_key(b, "MatchDialAddressRegexes");
    jb_begin(b, '[');
    jb_item_str(b, ".*");
    jb_end(b, ']');
    jb_key(b, "DialAddresses");
    jb_begin(b, '[');
    jb_item_str(b, ip);
    jb_end(b, ']');
    jb_str(b, "SNIServerName", sni && sni[0] ? sni : ip);
    jb_key(b, "VerifyServerNames");
    jb_begin(b, '[');
    jb_item_str(b, sni && sni[0] ? sni : ip);
    jb_item_str(b, ip);
    for (i = 0; i < (int)(sizeof(kEdgeVerifyExtra) / sizeof(kEdgeVerifyExtra[0])); i++)
        jb_item_str(b, kEdgeVerifyExtra[i]);
    jb_end(b, ']');
    jb_key(b, "ALPNProtocols");
    jb_begin(b, '[');
    jb_item_str(b, "http/1.1");
    jb_end(b, ']');
    jb_str(b, "TLSProfile", "Chrome-83");
    jb_end(b, '}');
}

static void put_fastly_override(JsonBuf *b, const char *id, int match_provider)
{
    int i;
    jb_begin(b, '{');
    jb_str(b, "OverrideID", id);
    if (match_provider) {
        jb_key(b, "MatchFrontingProviderIDRegexes");
        jb_begin(b, '[');
        jb_item_str(b, "(?i)fastly");
        jb_end(b, ']');
    } else {
        jb_key(b, "MatchDialAddressRegexes");
        jb_begin(b, '[');
        jb_item_str(b, "(?i)(fastly|pypi|python|github)");
        jb_end(b, ']');
    }
    jb_key(b, "DialAddresses");
    jb_begin(b, '[');
    jb_item_str(b, "pypi.org");
    jb_end(b, ']');
    jb_str(b, "SNIServerName", "pypi.org");
    jb_key(b, "VerifyServerNames");
    jb_begin(b, '[');
    for (i = 0; i < (int)(sizeof(kFastlyVerifyServerNames) /
                          sizeof(kFastlyVerifyServerNames[0])); i++)
        jb_item_str(b, kFastlyVerifyServerNames[i]);
    jb_end(b, ']');
    jb_key(b, "ALPNProtocols");
    jb_begin(b, '[');
    jb_item_str(b, "h2");
    jb_item_str(b, "http/1.1");
    jb_end(b, ']');
    jb_str(b, "TLSProfile", "Chrome-83");
    jb_end(b, '}');
}

/* putCdnFrontingConfig() from the Android client. */
static void put_cdn_fronting_config(JsonBuf *b, const wchar_t *ip_list,
                                    const wchar_t *sni_list)
{
    static wchar_t sni_toks[64][128];
    static wchar_t ip_toks[64][128];
    char edge_sni[256];
    char *u8;
    int n_ip, n_sni, i;

    n_sni = split_tokens(sni_list, sni_toks, 64);
    for (i = 0; i < n_sni; i++) normalize_hostname(sni_toks[i]);
    {
        int kept = 0, j;
        for (i = 0; i < n_sni; i++) {
            if (!is_hostname(sni_toks[i])) continue;
            for (j = 0; j < kept; j++) if (!wcscmp(sni_toks[j], sni_toks[i])) break;
            if (j == kept) {
                if (kept != i) wcscpy(sni_toks[kept], sni_toks[i]);
                kept++;
            }
        }
        n_sni = kept;
    }
    u8 = n_sni ? wchar_to_utf8(sni_toks[0]) : NULL;   /* normalizeCdnFrontingCustomSni */
    snprintf(edge_sni, sizeof edge_sni, "%s", u8 ? u8 : "");
    free_conv(u8);

    jb_key(b, "FrontedMeekDialOverrides");
    jb_begin(b, '[');
    put_fastly_override(b, "fastly-provider", 1);
    put_fastly_override(b, "fastly-address", 0);
    for (i = 0; i < (int)(sizeof(kEdgeDialAddresses) /
                          sizeof(kEdgeDialAddresses[0])); i++)
        put_edge_override(b, kEdgeOverrideIds[i], kEdgeDialAddresses[i], edge_sni);
    jb_end(b, ']');
    jb_num(b, "FrontedMeekDialOverridesProbability", 1.0);
    jb_bool(b, "FrontedMeekCDNScanUseBuiltInSpec", 1);

    n_ip = split_tokens(ip_list, ip_toks, 64);
    if (n_ip > 0) {
        int kept = 0;
        for (i = 0; i < n_ip; i++)
            if (is_ipv4_or_cidr(ip_toks[i])) {
                if (kept != i) wcscpy(ip_toks[kept], ip_toks[i]);
                kept++;
            }
        if (kept > 0) {
            jb_key(b, "FrontedMeekCDNScanSpec");
            jb_begin(b, '{');
            jb_key(b, "IPCandidates");
            put_string_array_w(b, ip_toks, kept);
            if (n_sni > 0) {
                jb_key(b, "SNIServerNames");
                put_string_array_w(b, sni_toks, n_sni);
            }
            jb_end(b, '}');
        }
    }
}

/* Feedback upload is only possible when the build actually received both a
   feedback key and at least one upload URL (EnableFeedbackUpload in Android). */
static int values_have_feedback_target(void)
{
    wchar_t key[MAX_PATH_W];
    char **items = NULL;
    int n = 0, i, have;

    if (!values_lookup_string(L"FeedbackEncryptionPublicKey", key, MAX_PATH_W) || !key[0])
        return 0;
    have = (values_lookup_array(L"FeedbackUploadURLs", &items, &n) && n > 0);
    for (i = 0; i < n; i++) free(items[i]);
    free(items);
    return have;
}

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

    /* When network sharing is on, the local proxies can require credentials
       (shareProxyOnNetworkUsername / ...Password in the Android client). */
    if (s->lan_proxy_sharing && s->lan_proxy_auth && s->lan_user[0]) {
        jb_str_w(&b, "LocalProxyUsername", s->lan_user);
        jb_str_w(&b, "LocalProxyPassword", s->lan_pass);
    }

    /* Region / protocol ----------------------------------------------- */
    if (s->region[0] && wcscmp(s->region, L"Any") != 0)
        jb_str_w(&b, "EgressRegion", s->region);

    /* The protocol matrix below mirrors TunnelManager.buildTunnelCoreConfig():
       protocolSelection selects the transport family, and conduit mode decides
       whether the personal compartment id (private Shir o Khorshid conduits)
       is offered to the core. */
    {
        int use_compartment_id = 0;
        int is_conduit = (wcscmp(s->protocol_selection, L"conduit") == 0);
        int enable_cdn_fronting =
            !s->protocol_selection[0] ||
            wcscmp(s->protocol_selection, L"auto") == 0 ||
            wcscmp(s->protocol_selection, L"direct") == 0 ||
            wcscmp(s->protocol_selection, L"cdn_fronting") == 0;

        if (enable_cdn_fronting)
            put_cdn_fronting_config(&b, s->cdn_ip_list, s->cdn_sni);

        if (is_conduit) {
            jb_str_array(&b, "LimitTunnelProtocols",
                         kConduitProtocols,
                         (int)(sizeof(kConduitProtocols) / sizeof(kConduitProtocols[0])));

            if (wcscmp(s->conduit_mode, L"shirokhorshid") == 0) {
                use_compartment_id = 1;
                log_ring_add(0, L"%ls", fmt_conduit_mode(
                                 tr(S_CONDUIT_MODE_LABEL_SHIROKHORSHID), NULL));
            } else if (wcscmp(s->conduit_mode, L"public") == 0) {
                use_compartment_id = 0;
                log_ring_add(0, L"%ls", fmt_conduit_mode(
                                 tr(S_CONDUIT_MODE_LABEL_PUBLIC), NULL));
            } else {
                /* auto: use the private compartment unless we already fell
                   back to public conduits after the fallback timeout. */
                use_compartment_id = g_conduit_fallback ? 0 : 1;
                log_ring_add(0, L"%ls", g_conduit_fallback
                             ? fmt_conduit_mode(tr(S_CONDUIT_MODE_LABEL_AUTO),
                                                tr(S_CONDUIT_MODE_LABEL_PUBLIC))
                             : fmt_conduit_mode(tr(S_CONDUIT_MODE_LABEL_AUTO), NULL));
            }

            if (use_compartment_id) {
                if (values_lookup_string(L"ConduitCompartmentId", tmp, MAX_PATH_W) && tmp[0])
                    jb_str_w(&b, "InproxyClientPersonalCompartmentID", tmp);
                else
                    log_ring_add(1, L"conduit mode: PSIPHON_CONDUIT_COMPARTMENT_ID was not set at build time");
            }

            /* Country rejection needs a GeoIP database; only ask for it when
               one is actually available, exactly like the Android client. */
            if (geoip_database_path(tmp, MAX_PATH_W)) {
                jb_str_w(&b, "GeoIPDatabasePath", tmp);
                if (s->reject_censored_proxies) {
                    jb_str_array(&b, "InproxyRejectProxyCountryCodes",
                                 kCensoredCountryCodes,
                                 (int)(sizeof(kCensoredCountryCodes) /
                                       sizeof(kCensoredCountryCodes[0])));
                }
            } else if (s->reject_censored_proxies) {
                log_ring_add(1, L"rejecting censored-country proxies needs a GeoIP database (GeoLite2-Country.mmdb)");
            }
        } else if (wcscmp(s->protocol_selection, L"cdn_fronting") == 0) {
            jb_str_array(&b, "LimitTunnelProtocols",
                         kCdnFrontingProtocols,
                         (int)(sizeof(kCdnFrontingProtocols) /
                               sizeof(kCdnFrontingProtocols[0])));
            jb_bool(&b, "DisableTactics", 1);
        } else if (wcscmp(s->protocol_selection, L"direct") == 0) {
            jb_str_array(&b, "LimitTunnelProtocols",
                         kDirectProtocols,
                         (int)(sizeof(kDirectProtocols) /
                               sizeof(kDirectProtocols[0])));
            jb_bool(&b, "DisableTactics", 1);
        }
        /* "auto" leaves LimitTunnelProtocols unset so the core picks. */
    }

    /* Beast mode: aggressive establishment, try all protocols on all servers */
    if (s->beast_mode) jb_bool(&b, "AggressiveEstablishment", 1);

    jb_bool(&b, "EmitServerAlerts", 1);

    /* Upstream proxy --------------------------------------------------- */
    if (s->upstream_proxy_enabled && s->upstream_host[0]) {
        char url[1600];
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
        {
            char dom[256];
            u8 = wchar_to_utf8(s->upstream_domain);
            snprintf(dom, sizeof dom, "%s", u8 ? u8 : "");
            free_conv(u8);

            if (user[0]) {
                /* domain\user is the NTLM form the Android client builds via
                   useProxyDomainPreference. */
                if (dom[0])
                    snprintf(url, sizeof url, "%s://%s\\%s:%s@%s:%d",
                             s->upstream_proxy_use_https ? "https" : "http",
                             dom, user, pass, host, s->upstream_port);
                else
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
    }

    /* Timeouts ---------------------------------------------------------- */
    if (s->disable_timeouts) {
        /* Same as the Android client: keep waiting on slow networks instead of
           giving up, by flattening the latency multiplier curve. */
        jb_num(&b, "NetworkLatencyMultiplierLambda", 0.1);
    }

    /* Diagnostics --------------------------------------------------------*/
    if (s->diagnostics_upload && values_have_feedback_target()) {
        jb_bool(&b, "EnableFeedbackUpload", 1);
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

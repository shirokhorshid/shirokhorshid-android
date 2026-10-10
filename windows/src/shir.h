/*
 * Shir o Khorshid for Windows
 *
 * An unofficial, independently maintained Windows build of the Psiphon
 * open-source client (https://github.com/Psiphon-Inc/psiphon-android).
 *
 * Copyright (c) 2026, Shir o Khorshid contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This application is NOT developed, endorsed, sponsored, or affiliated
 * with Psiphon Inc. in any way.
 */

#ifndef SHIR_H
#define SHIR_H

#ifdef _WIN32
#  ifndef UNICODE
#    define UNICODE
#  endif
#  ifndef _UNICODE
#    define _UNICODE
#  endif
#  ifndef WIN32_LEAN_AND_MEAN
#    define WIN32_LEAN_AND_MEAN
#  endif
#  include <windows.h>
#  include <commctrl.h>
#  include <shellapi.h>
#  include <shlwapi.h>
#else
/* The portable layers (JSON, config assembly) are also compiled natively for
   unit tests, so provide the handful of types they rely on. */
#  include <stddef.h>
#  include <stdint.h>
#  include <string.h>
#  include <stdlib.h>
#  include <wchar.h>
#  include <stdio.h>
/* Provided by tests/stubs.c when building host-side unit tests. */
FILE *_wfopen(const wchar_t *path, const wchar_t *mode);
int   _snwprintf(wchar_t *buf, size_t n, const wchar_t *fmt, ...);
#endif

#include <stdio.h>
#include <stdarg.h>
#include <time.h>

#define APP_NAME        L"\u0634\u06cc\u0631 \u0648 \u062e\u0648\u0631\u0634\u06cc\u062f"   /* شیر و خورشید */
#define APP_NAME_ASCII  "ShirOKhorshid"
#define APP_VERSION     "1.0.0"

#define MAX_STR         512
#define MAX_PATH_W      1024
#define LOG_CAPACITY    4000

/* ------------------------------------------------------------------ strings */

typedef enum {
    S_APP_NAME,
    S_TAB_HOME,
    S_TAB_STATISTICS,
    S_TAB_SETTINGS,
    S_TAB_LOGS,
    S_START,
    S_STOP,
    S_CONNECTING,
    S_CONNECTED,
    S_DISCONNECTED,
    S_STOPPING,
    S_OPEN_BROWSER,
    S_WAITING,
    S_TUNNEL_WHOLE_DEVICE,
    S_TUNNEL_WHOLE_DEVICE_SUMMARY,
    S_REGION,
    S_REGION_ANY,
    S_REGION_BEST,
    S_SENT,
    S_RECEIVED,
    S_ELAPSED,
    S_STATUS,
    S_SERVER,
    S_PROTOCOL,
    S_LOCAL_PROXY,
    S_LOCAL_PROXY_SUMMARY,
    S_OPTIONS_VPN,
    S_OPTIONS_PROXY,
    S_OPTIONS_MORE,
    S_CLEAR_LOGS,
    S_COPY_LOGS,
    S_SEND_FEEDBACK,
    S_LOG_FILTER_ALL,
    S_LOG_FILTER_INFO,
    S_LOG_FILTER_DIAGNOSTIC,
    S_SETTINGS_LANGUAGE,
    S_SETTINGS_LANGUAGE_SUMMARY,
    S_SETTINGS_NOTIFICATIONS_SOUND,
    S_SETTINGS_NOTIFICATIONS_SOUND_SUMMARY,
    S_SETTINGS_DISABLE_TIMEOUTS,
    S_SETTINGS_DISABLE_TIMEOUTS_SUMMARY,
    S_SETTINGS_AUTO_OPEN_HOMEPAGE,
    S_SETTINGS_AUTO_OPEN_HOMEPAGE_SUMMARY,
    S_SETTINGS_SYSTEM_PROXY,
    S_SETTINGS_SYSTEM_PROXY_SUMMARY,
    S_SETTINGS_LAN_SHARING,
    S_SETTINGS_LAN_SHARING_SUMMARY,
    S_SETTINGS_MINIMIZE_TRAY,
    S_SETTINGS_MINIMIZE_TRAY_SUMMARY,
    S_SETTINGS_START_WITH_WINDOWS,
    S_SETTINGS_START_WITH_WINDOWS_SUMMARY,
    S_SETTINGS_UPSTREAM_PROXY,
    S_SETTINGS_UPSTREAM_PROXY_SUMMARY,
    S_SETTINGS_PROTOCOL,
    S_SETTINGS_PROTOCOL_SUMMARY,
    S_UPSTREAM_HOST,
    S_UPSTREAM_PORT,
    S_UPSTREAM_USERNAME,
    S_UPSTREAM_PASSWORD,
    S_UPSTREAM_HTTPS,
    S_UPSTREAM_HTTPS_SUMMARY,
    S_APPLY,
    S_CANCEL,
    S_STATISTICS_TOTAL,
    S_STATISTICS_PER_REGION,
    S_STATISTICS_EMPTY,
    S_NO_LOGS,
    S_DISCLAIMER,
    S_TRAY_OPEN,
    S_TRAY_CONNECT,
    S_TRAY_DISCONNECT,
    S_TRAY_EXIT,
    S_ABOUT,
    S_BUTTON_OK,
    S_ERROR_TITLE,
    S_CORE_MISSING,
    S_LANGUAGE_FA,
    S_LANGUAGE_EN,
    S_PROTOCOL_ALL,

    /* "More options" - the Android client's protocol-selection matrix. */
    S_SETTINGS_PROTOCOL_SELECTION,
    S_SETTINGS_PROTOCOL_SELECTION_SUM,
    S_PROTOCOL_AUTO,
    S_PROTOCOL_DIRECT,
    S_PROTOCOL_CDN_FRONTING,
    S_PROTOCOL_CONDUIT,
    S_SETTINGS_BEAST_MODE,
    S_SETTINGS_BEAST_MODE_SUM,
    S_SETTINGS_CONDUIT_MODE,
    S_SETTINGS_CONDUIT_MODE_SUM,
    S_CONDUIT_MODE_LABEL_AUTO,
    S_CONDUIT_MODE_LABEL_SHIROKHORSHID,
    S_CONDUIT_MODE_LABEL_PUBLIC,
    S_CONDUIT_MODE_AUTO,
    S_CONDUIT_MODE_SHIROKHORSHID,
    S_CONDUIT_MODE_PUBLIC,
    S_SETTINGS_CONDUIT_TIMEOUT,
    S_SETTINGS_CONDUIT_TIMEOUT_SUM,
    S_CONDUIT_TIMEOUT_2MIN,
    S_CONDUIT_TIMEOUT_3MIN,
    S_CONDUIT_TIMEOUT_5MIN,
    S_CONDUIT_TIMEOUT_10MIN,
    S_SETTINGS_CDN_IP_LIST,
    S_SETTINGS_CDN_IP_LIST_SUM,
    S_SETTINGS_CDN_SNI,
    S_SETTINGS_CDN_SNI_SUM,
    S_SETTINGS_REJECT_CENSORED,
    S_SETTINGS_REJECT_CENSORED_SUM,
    S_SETTINGS_UPSTREAM_DOMAIN,
    S_SETTINGS_DIAGNOSTICS,
    S_SETTINGS_DIAGNOSTICS_SUM,
    S_CDN_COUNT,
    S_LAN_SHARING_ACTIVE,
    S_LAN_HTTP_ADDRESS,
    S_LAN_SOCKS_ADDRESS,
    S_LAN_USERNAME,
    S_LAN_PASSWORD,
    S_LAN_CONFIGURE_HINT,
    S_CONDUIT_MODE_STATUS,
    S_COUNT_ /* must be last */
} StrId;

const wchar_t *tr(StrId id);
void i18n_set_language(int persian);
int  i18n_is_persian(void);

/* ------------------------------------------------------------------- config */

/* "Any" plus the 49 region flags shipped by the Android client. */
#define REGION_COUNT 50

typedef struct {
    int  tunnel_whole_device;      /* route all system traffic (system proxy) */
    int  set_system_proxy;
    int  auto_open_homepage;
    int  disable_timeouts;
    int  notifications_sound;
    int  minimize_to_tray;
    int  start_with_windows;
    int  lan_proxy_sharing;
    int  lan_proxy_auth;
    int  upstream_proxy_enabled;
    int  upstream_proxy_use_https;
    int  socks_port;
    int  http_port;
    int  persian;                  /* UI language: 1 = فارسی, 0 = English */
    int  auto_connect;
    wchar_t region[32];

    /* Protocol selection, mirroring the Android client's
       protocolSelectionPreference / conduitModePreference / beastModePreference. */
    wchar_t protocol_selection[24];   /* auto | direct | cdn_fronting | conduit */
    wchar_t conduit_mode[24];         /* auto | shirokhorshid | public          */
    int     conduit_timeout;          /* seconds: 120 | 180 | 300 | 600         */
    wchar_t cdn_ip_list[1024];        /* extra CDN edge IPs / CIDRs             */
    wchar_t cdn_sni[512];             /* extra CDN SNI hostnames                */
    int     beast_mode;               /* AggressiveEstablishment                */
    int     reject_censored_proxies;  /* InproxyRejectProxyCountryCodes         */

    wchar_t upstream_host[256];
    int     upstream_port;
    wchar_t upstream_username[128];
    wchar_t upstream_password[128];
    wchar_t upstream_domain[128];
    int     diagnostics_upload;
    wchar_t lan_user[64];
    wchar_t lan_pass[64];
} Settings;

typedef enum {
    TSTATE_STOPPED = 0,
    TSTATE_STARTING,
    TSTATE_CONNECTING,
    TSTATE_CONNECTED,
    TSTATE_STOPPING
} TunnelState;

typedef struct {
    TunnelState      state;
    unsigned long    started_at;     /* GetTickCount() */
    unsigned long    connected_at;
    unsigned long long sent;
    unsigned long long received;
    wchar_t          client_region[64];
    wchar_t          server_address[128];
    wchar_t          protocol[32];
    wchar_t          status[256];
    wchar_t          last_notice[512];
    int              socks_port;
    int              http_port;
    int              page_views;
} TunnelStatus;

typedef struct {
    int         level;      /* 0=info 1=warning 2=error 3=diagnostic */
    unsigned long when;
    wchar_t     text[MAX_STR];
} LogEntry;

typedef struct {
    wchar_t region[32];
    unsigned long long sent;
    unsigned long long received;
} RegionStat;

void settings_init(Settings *s);
int  settings_load(Settings *s, const wchar_t *path);
int  settings_save(const Settings *s, const wchar_t *path);
void settings_get_path(wchar_t *out, size_t n);

/* Directories / files used by the app. */
void app_data_dir(wchar_t *out, size_t n);         /* %APPDATA%\ShirOKhorshid      */
void app_data_file(wchar_t *out, size_t n, const wchar_t *name);
void core_data_dir(wchar_t *out, size_t n);        /* tunnel-core datastore        */
void app_install_dir(wchar_t *out, size_t n);      /* folder holding the .exe      */
void app_install_file(wchar_t *out, size_t n, const wchar_t *name);
void app_ensure_dirs(void);

/* Build-time "embedded values" (see tools/gen_values.py). */
int values_lookup_string(const wchar_t *key, wchar_t *out, size_t n);
int values_lookup_array(const wchar_t *key, char ***out, int *count);

/* Conduit fallback state (see conduitTimeoutPreference in the Android client).
   In "auto" conduit mode the app starts with the private Shir o Khorshid
   compartment and, if no tunnel comes up within the fallback timeout, restarts
   the engine using public conduits instead. */
void core_set_conduit_fallback(int on);
int  core_get_conduit_fallback(void);

void log_ring_add(int level, const wchar_t *fmt, ...);
int  log_ring_count(void);
const LogEntry *log_ring_at(int i);
void log_ring_clear(void);
int  log_ring_total(void);

/* Region table (mirrors the Android client's region list). */
extern const wchar_t *const kRegionCodes[REGION_COUNT];
extern const wchar_t *const kRegionNamesFa[REGION_COUNT];
extern const wchar_t *const kRegionNamesEn[REGION_COUNT];
const wchar_t *region_display_name(const wchar_t *code);

/* --------------------------------------------------------------------- json */

/* Minimal JSON value used for both parsing tunnel-core notices and writing the
   client configuration file. */

typedef enum { JNULL, JBOOL, JNUM, JSTR, JARR, JOBJ } JType;

typedef struct JValue JValue;

struct JValue {
    JType   type;
    double  num;
    int     boolean;
    char   *str;                 /* UTF-8, JSTR */
    JValue *items;               /* array/object backing store (linked via next) */
    int     count;
    struct JValue *next;         /* sibling in array/object */
    char   *key;                 /* member name in object */
    struct JValue *value;        /* member value in object */
};

JValue *json_parse(const char *text);
void    json_free(JValue *v);
JValue *json_get(const JValue *obj, const char *key);
const char *json_get_str(const JValue *obj, const char *key, const char *dflt);
double  json_get_num(const JValue *obj, const char *key, double dflt);
int     json_get_bool(const JValue *obj, const char *key, int dflt);

/* String builder used to emit the tunnel-core configuration. */
typedef struct {
    char  *data;
    size_t len;
    size_t cap;
} JsonBuf;

void jb_init(JsonBuf *b);
void jb_free(JsonBuf *b);
void jb_raw(JsonBuf *b, const char *s);
void jb_begin(JsonBuf *b, char brace);
void jb_end(JsonBuf *b, char brace);
void jb_key(JsonBuf *b, const char *k);
void jb_str(JsonBuf *b, const char *k, const char *v);
void jb_str_w(JsonBuf *b, const char *k, const wchar_t *v);
void jb_num(JsonBuf *b, const char *k, double v);
void jb_bool(JsonBuf *b, const char *k, int v);
void jb_str_array(JsonBuf *b, const char *k, const char *const *items, int n);
/* Raw building blocks: emit a value for a member name already written with
   jb_key(), or an item inside an array opened with jb_begin(). */
void jb_value_str(JsonBuf *b, const char *v);
void jb_item_str(JsonBuf *b, const char *v);

/* UTF-8 <-> UTF-16 helpers (also used on non-Windows builds). */
char    *wchar_to_utf8(const wchar_t *s);
wchar_t *utf8_to_wchar(const char *s);
void     free_conv(void *p);

/* Build the psiphon-tunnel-core client configuration. */
int core_build_config_json(const Settings *s, const wchar_t *data_dir,
                           const wchar_t *server_list_path,
                           char **out_json, size_t *out_len);

/* -------------------------------------------------------------------- tunnel
 * These entry points are implemented per-platform. On Windows they manage the
 * psiphon-tunnel-core.exe child process; the rest of the app talks to them. */

int  tunnel_start(const Settings *s);
void tunnel_stop(void);
int  tunnel_is_running(void);
TunnelStatus *tunnel_status(void);
void tunnel_set_state(TunnelState st);
void tunnel_pump(void);            /* called from the message loop timer */
const wchar_t *tunnel_core_path(wchar_t *out, size_t n);
int  tunnel_core_available(void);
const wchar_t *tunnel_homepage(void);
const wchar_t *tunnel_available_regions(void);
/* Best-effort IPv4 address of this machine on the local network (for the
   network-sharing card).  Returns 0 when no non-loopback address exists. */
int tunnel_lan_ip(wchar_t *out, size_t n);
void tunnel_elapsed_text(wchar_t *out, size_t n);
void tunnel_bytes_text(unsigned long long bytes, wchar_t *out, size_t n);

/* Shared with the window procedure so the reader thread can trigger a repaint. */
#ifdef _WIN32
extern UINT WM_APP_NOTICE;
extern HWND g_main_hwnd;
#endif

/* Statistics persistence (bytes transferred, aggregated per region). */
void stats_add(const wchar_t *region, unsigned long long sent,
               unsigned long long received);
int  stats_count(void);
const RegionStat *stats_at(int i);
void stats_load(void);
void stats_save(void);
void stats_reset(void);

#endif /* SHIR_H */

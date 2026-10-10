/*
 * Shir o Khorshid for Windows - custom-drawn Material-style UI.
 * GPLv3. See shir.h for the full notice.
 */

#ifndef SHIR_UI_H
#define SHIR_UI_H

#include "shir.h"

enum {
    TAB_HOME = 0,
    TAB_STATISTICS,
    TAB_SETTINGS,
    TAB_LOGS,
    TAB_COUNT
};

/* Widget identifiers (also used as the action code of a click). */
enum {
    WID_NONE = 0,
    WID_CONNECT = 1,
    WID_BROWSER = 2,
    WID_CLEAR_LOGS = 3,
    WID_COPY_LOGS = 4,
    WID_TAB = 100,           /* + tab index */
    WID_FILTER = 110,        /* + filter level */
    WID_REGION = 20,
    WID_PROTOCOL_SELECTION = 21,
    WID_LANG = 22,
    WID_UPSTREAM = 23,
    WID_STAT_RESET = 24,
    WID_CONDUIT_MODE = 25,
    WID_CONDUIT_TIMEOUT = 26,
    WID_CDN_IP_LIST = 27,
    WID_CDN_SNI = 28,
    WID_SETTING = 200        /* + setting index */
};

typedef enum {
    ROW_SWITCH = 0,
    ROW_COMBO,
    ROW_ACTION
} RowKind;

typedef struct {
    int     id;          /* WID_SETTING + index */
    RowKind kind;
    StrId   title;
    StrId   summary;
    int    *flag;        /* for ROW_SWITCH */
    int     disabled;
} SettingRow;

typedef struct {
    HDC   hdc;
    RECT  cr;            /* client rect */
    int   rtl;
    int   dpi;
    int   scale;         /* dpi / 96, at least 1 */

    HFONT f_caption, f_title, f_body, f_small, f_big, f_mono;

    /* Hit regions, rebuilt on every paint. */
    struct { int id; RECT rc; } hits[256];
    int   nhits;
    int   hot;

    int   tab;
    int   scroll[TAB_COUNT];
    int   content_h[TAB_COUNT];
    int   view_h;

    HICON app_icon;
    HICON tray_on;
    HICON tray_off;

    HBITMAP surface;
    HDC     surf_dc;
    int     surf_w, surf_h;
} UI;

void ui_init(UI *u);
void ui_dpi_changed(UI *u, int dpi);
void ui_load_icons(UI *u, HINSTANCE inst);
void ui_begin(UI *u, HDC hdc, const RECT *client);
void ui_end(UI *u, HDC hdc);
void ui_paint(UI *u, const Settings *s);
int  ui_hit_test(UI *u, int x, int y);
void ui_add_hit(UI *u, int id, RECT rc);

/* Popup pickers for combo rows. */
int  ui_popup_pick(HWND hwnd, const wchar_t *const *labels, int count, int current,
                   RECT anchor);
int  ui_popup_pick_strings(HWND hwnd, const wchar_t *const *labels, int count,
                           int current, RECT anchor);

void ui_set_log_filter(int level);
int  ui_get_log_filter(void);

/* Layout metrics, shared with the window procedure. */
int  ui_toolbar_h(const UI *u);
int  ui_tabbar_h(const UI *u);
int  ui_bottom_h(const UI *u);

#endif /* SHIR_UI_H */

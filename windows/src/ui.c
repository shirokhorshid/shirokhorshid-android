/*
 * Shir o Khorshid for Windows - custom-drawn Material-style UI.
 * GPLv3. See shir.h for the full notice.
 *
 * Everything below the title bar is drawn by hand into an off-screen bitmap,
 * so the app keeps the same look on every Windows version and mirrors
 * correctly for Persian/RTL.
 */

#include "ui.h"
#include "shir.h"
#include <stdlib.h>
#include <string.h>

#ifndef DT_NOCLIP
#define DT_NOCLIP 0x00000100
#endif

/* ------------------------------------------------------------------ palette */

static const COLORREF C_BRAND    = RGB(0x1A, 0x23, 0x7E);
static const COLORREF C_BRAND_DK = RGB(0x12, 0x18, 0x5B);
static const COLORREF C_ACCENT   = RGB(0x00, 0xA0, 0x50);
static const COLORREF C_BG       = RGB(0xF5, 0xF6, 0xF8);
static const COLORREF C_CARD     = RGB(0xFF, 0xFF, 0xFF);
static const COLORREF C_TEXT     = RGB(0x1F, 0x22, 0x28);
static const COLORREF C_TEXT2    = RGB(0x6B, 0x70, 0x79);
static const COLORREF C_DIVIDER  = RGB(0xE3, 0xE6, 0xEA);
static const COLORREF C_OK       = RGB(0x2E, 0x9B, 0x57);
static const COLORREF C_WARN     = RGB(0xD9, 0x83, 0x10);
static const COLORREF C_ERR      = RGB(0xD4, 0x3B, 0x33);
static const COLORREF C_HOVER    = RGB(0xEE, 0xF0, 0xF4);

static HFONT make_font(const wchar_t *face, int height, int bold)
{
    return CreateFontW(-height, 0, 0, 0, bold ? FW_SEMIBOLD : FW_NORMAL,
                       FALSE, FALSE, FALSE, DEFAULT_CHARSET,
                       OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                       CLEARTYPE_QUALITY,
                       DEFAULT_PITCH | FF_DONTCARE, face);
}

void ui_init(UI *u)
{
    memset(u, 0, sizeof(*u));
    u->dpi = 96;
    u->scale = 1;
    u->rtl = 1;
    u->tab = TAB_HOME;
    ui_dpi_changed(u, 96);
}

void ui_dpi_changed(UI *u, int dpi)
{
    int s;
    if (dpi < 96) dpi = 96;
    u->dpi = dpi;
    s = (dpi + 48) / 96;
    if (s < 1) s = 1;
    u->scale = s;

    if (u->f_caption) DeleteObject(u->f_caption);
    if (u->f_title)   DeleteObject(u->f_title);
    if (u->f_body)    DeleteObject(u->f_body);
    if (u->f_small)   DeleteObject(u->f_small);
    if (u->f_big)     DeleteObject(u->f_big);
    if (u->f_mono)    DeleteObject(u->f_mono);

    u->f_caption = make_font(L"Segoe UI", 15 * s, 1);
    u->f_title   = make_font(L"Segoe UI", 14 * s, 1);
    u->f_body    = make_font(L"Segoe UI", 13 * s, 0);
    u->f_small   = make_font(L"Segoe UI", 11 * s, 0);
    u->f_big     = make_font(L"Segoe UI", 20 * s, 1);
    u->f_mono    = make_font(L"Consolas", 12 * s, 0);
}

void ui_load_icons(UI *u, HINSTANCE inst)
{
    wchar_t path[MAX_PATH_W];

    u->app_icon = (HICON)LoadImageW(inst, MAKEINTRESOURCEW(1), IMAGE_ICON,
                                   0, 0, LR_DEFAULTCOLOR);
    if (!u->app_icon) {
        app_install_file(path, MAX_PATH_W, L"app.ico");
        u->app_icon = (HICON)LoadImageW(NULL, path, IMAGE_ICON, 0, 0,
                                        LR_LOADFROMFILE | LR_DEFAULTCOLOR);
    }
    u->tray_on = (HICON)LoadImageW(inst, MAKEINTRESOURCEW(3), IMAGE_ICON,
                                   GetSystemMetrics(SM_CXSMICON),
                                   GetSystemMetrics(SM_CYSMICON), LR_DEFAULTCOLOR);
    u->tray_off = (HICON)LoadImageW(inst, MAKEINTRESOURCEW(2), IMAGE_ICON,
                                    GetSystemMetrics(SM_CXSMICON),
                                    GetSystemMetrics(SM_CYSMICON), LR_DEFAULTCOLOR);
}

int ui_toolbar_h(const UI *u) { return 48 * u->scale; }
int ui_tabbar_h(const UI *u)  { return 42 * u->scale; }
int ui_bottom_h(const UI *u)  { return 58 * u->scale; }

/* ---------------------------------------------------------------- primitives */

static RECT mk(const UI *u, int x, int y, int w, int h)
{
    RECT r;
    int width = u->cr.right - u->cr.left;
    if (u->rtl) x = width - x - w;
    r.left   = u->cr.left + x;
    r.top    = u->cr.top + y;
    r.right  = r.left + w;
    r.bottom = r.top + h;
    return r;
}

void ui_add_hit(UI *u, int id, RECT rc)
{
    if (u->nhits >= 256 || id == WID_NONE) return;
    u->hits[u->nhits].id = id;
    u->hits[u->nhits].rc = rc;
    u->nhits++;
}

int ui_hit_test(UI *u, int x, int y)
{
    int i;
    POINT pt;
    pt.x = x; pt.y = y;
    for (i = u->nhits - 1; i >= 0; i--) {
        if (PtInRect(&u->hits[i].rc, pt)) return u->hits[i].id;
    }
    return WID_NONE;
}

static void fill(UI *u, RECT rc, COLORREF c)
{
    HBRUSH b = CreateSolidBrush(c);
    FillRect(u->hdc, &rc, b);
    DeleteObject(b);
}

static void frame(UI *u, RECT rc, COLORREF c)
{
    HBRUSH b = CreateSolidBrush(c);
    FrameRect(u->hdc, &rc, b);
    DeleteObject(b);
}

static void round_fill(UI *u, RECT rc, int radius, COLORREF fill_c, COLORREF line_c)
{
    HGDIOBJ old;
    HPEN pen = CreatePen(PS_SOLID, 1, line_c);
    HBRUSH br = CreateSolidBrush(fill_c);
    old = SelectObject(u->hdc, pen);
    SelectObject(u->hdc, br);
    RoundRect(u->hdc, rc.left, rc.top, rc.right, rc.bottom, radius, radius);
    SelectObject(u->hdc, old);
    DeleteObject(pen);
    DeleteObject(br);
}

/* align: 0 = start (right in RTL), 1 = centre, 2 = end */
static void text(UI *u, const wchar_t *t, RECT rc, COLORREF c, HFONT f, int align)
{
    UINT fmt = DT_SINGLELINE | DT_VCENTER | DT_END_ELLIPSIS | DT_NOCLIP | DT_NOPREFIX;
    HFONT old;
    int old_bk, old_fc;

    if (u->rtl) fmt |= DT_RTLREADING;
    if (align == 0) fmt |= (u->rtl ? DT_RIGHT : DT_LEFT);
    else if (align == 1) fmt |= DT_CENTER;
    else fmt |= (u->rtl ? DT_LEFT : DT_RIGHT);

    old = (HFONT)SelectObject(u->hdc, f);
    old_bk = SetBkMode(u->hdc, TRANSPARENT);
    old_fc = SetTextColor(u->hdc, c);
    DrawTextW(u->hdc, t, -1, &rc, fmt);
    SetTextColor(u->hdc, old_fc);
    SetBkMode(u->hdc, old_bk);
    SelectObject(u->hdc, old);
}

static int text_h(UI *u, HFONT f)
{
    TEXTMETRICW tm;
    HFONT old = (HFONT)SelectObject(u->hdc, f);
    GetTextMetricsW(u->hdc, &tm);
    SelectObject(u->hdc, old);
    return tm.tmHeight;
}

static void draw_icon(UI *u, HICON icon, int x, int y, int size)
{
    if (!icon) return;
    DrawIconEx(u->hdc, x, y, icon, size, size, 0, NULL, DI_NORMAL);
}

/* ------------------------------------------------------------------ widgets */

static void button(UI *u, int id, RECT rc, const wchar_t *label,
                   COLORREF bg, COLORREF fg, int radius)
{
    round_fill(u, rc, radius, bg, bg);
    text(u, label, rc, fg, u->f_body, 1);
    ui_add_hit(u, id, rc);
}

static void toggle_switch(UI *u, RECT rc, int on, int enabled)
{
    int w = 40 * u->scale, h = 20 * u->scale;
    int x = u->rtl ? rc.right - w : rc.left;
    int y = rc.top + ((rc.bottom - rc.top) - h) / 2;
    RECT track, knob;
    COLORREF bg = !enabled ? RGB(0xC7, 0xCB, 0xD1)
                           : (on ? RGB(0xA6, 0xD3, 0xB8) : RGB(0xC7, 0xCB, 0xD1));
    COLORREF knob_c = !enabled ? RGB(0xE9, 0xEB, 0xEE)
                               : (on ? C_ACCENT : RGB(0xFF, 0xFF, 0xFF));

    track.left = x; track.top = y; track.right = x + w; track.bottom = y + h;
    round_fill(u, track, h, bg, bg);

    {
        int d = h - 6 * u->scale;
        int kx = on ? (u->rtl ? track.left + 3 * u->scale
                              : track.right - d - 3 * u->scale)
                    : (u->rtl ? track.right - d - 3 * u->scale
                              : track.left + 3 * u->scale);
        knob.left = kx; knob.top = y + 3 * u->scale;
        knob.right = knob.left + d; knob.bottom = knob.top + d;
        round_fill(u, knob, d, knob_c, knob_c);
    }
}

static void card(UI *u, RECT rc)
{
    round_fill(u, rc, 6 * u->scale, C_CARD, C_DIVIDER);
}

/* A Material-style settings row: title + summary on the start side, control on
   the end side. */
static int setting_row(UI *u, int id, int x, int y, int w, int h,
                       const wchar_t *title, const wchar_t *summary,
                       int kind, int *flag, const wchar_t *value, int disabled)
{
    RECT rc = mk(u, x, y, w, h);
    RECT t, s, v;
    int pad = 16 * u->scale;
    int ctrl_w = 56 * u->scale;

    fill(u, rc, (u->hot == id) ? C_HOVER : C_CARD);

    t = rc;
    t.left += pad; t.right -= pad + ctrl_w;
    t.top += 8 * u->scale; t.bottom = t.top + text_h(u, u->f_body);
    text(u, title, t, disabled ? C_TEXT2 : C_TEXT, u->f_body, 0);

    if (summary && summary[0]) {
        s = rc;
        s.left += pad; s.right -= pad + ctrl_w;
        s.top = t.bottom + 2 * u->scale;
        s.bottom = rc.bottom - 6 * u->scale;
        text(u, summary, s, C_TEXT2, u->f_small, 0);
    }

    if (kind == ROW_SWITCH && flag) {
        RECT c = rc;
        c.left = u->rtl ? rc.left + 8 * u->scale : rc.right - ctrl_w;
        c.right = c.left + ctrl_w - 8 * u->scale;
        toggle_switch(u, c, *flag, !disabled);
    } else if ((kind == ROW_COMBO || kind == ROW_ACTION) && value) {
        v = rc;
        v.left = u->rtl ? rc.left + pad : rc.right - 220 * u->scale;
        v.right = u->rtl ? rc.left + 220 * u->scale : rc.right - pad;
        text(u, value, v, disabled ? C_TEXT2 : C_ACCENT, u->f_body, 2);
    }

    {
        RECT line = rc;
        line.top = rc.bottom - 1; line.bottom = rc.bottom;
        fill(u, line, C_DIVIDER);
    }

    ui_add_hit(u, id, rc);
    return h;
}

/* ------------------------------------------------------------- tab: home */

static void paint_home(UI *u, const Settings *s)
{
    TunnelStatus *st = tunnel_status();
    int w = u->cr.right - u->cr.left;
    int y = 12 * u->scale;
    int badge = 132 * u->scale;
    RECT rc;
    COLORREF badge_c;
    const wchar_t *state_txt;
    wchar_t buf[256];

    (void)s;

    /* status badge */
    switch (st->state) {
    case TSTATE_CONNECTED: badge_c = C_ACCENT; state_txt = tr(S_CONNECTED); break;
    case TSTATE_CONNECTING:
    case TSTATE_STARTING:  badge_c = C_WARN;   state_txt = tr(S_CONNECTING); break;
    case TSTATE_STOPPING:  badge_c = C_TEXT2;  state_txt = tr(S_STOPPING); break;
    default:               badge_c = RGB(0xB0, 0xB6, 0xBD); state_txt = tr(S_DISCONNECTED); break;
    }

    {
        RECT b = mk(u, (w - badge) / 2, y, badge, badge);
        int cx = (b.left + b.right) / 2, cy = (b.top + b.bottom) / 2;
        int r = badge / 2;
        HBRUSH br = CreateSolidBrush(badge_c);
        HPEN pen = CreatePen(PS_SOLID, 1, badge_c);
        HGDIOBJ op = SelectObject(u->hdc, pen), ob = SelectObject(u->hdc, br);
        Ellipse(u->hdc, cx - r, cy - r, cx + r, cy + r);
        SelectObject(u->hdc, op); SelectObject(u->hdc, ob);
        DeleteObject(pen); DeleteObject(br);
        if (u->app_icon)
            draw_icon(u, u->app_icon, cx - (int)(badge * 0.33),
                      cy - (int)(badge * 0.33), (int)(badge * 0.66));
    }
    y += badge + 14 * u->scale;

    rc = mk(u, 0, y, w, 26 * u->scale);
    text(u, state_txt, rc, badge_c == C_ACCENT ? C_OK : C_TEXT, u->f_big, 1);
    y += 30 * u->scale;

    if (st->last_notice[0]) {
        rc = mk(u, 24 * u->scale, y, w - 48 * u->scale, 18 * u->scale);
        text(u, st->last_notice, rc, C_TEXT2, u->f_small, 1);
    }
    y += 26 * u->scale;

    /* Region selector */
    {
        RECT row = mk(u, 16 * u->scale, y, w - 32 * u->scale, 52 * u->scale);
        wchar_t val[128];
        RECT lab = row, val_rc = row;
        card(u, row);
        lab.left += 16 * u->scale; lab.right -= 200 * u->scale;
        text(u, tr(S_REGION), lab, C_TEXT, u->f_body, 0);
        swprintf(val, 128, L"%ls", region_display_name(s->region));
        val_rc.left = u->rtl ? row.left + 16 * u->scale : row.right - 220 * u->scale;
        val_rc.right = u->rtl ? row.left + 220 * u->scale : row.right - 16 * u->scale;
        text(u, val, val_rc, C_ACCENT, u->f_body, 2);
        ui_add_hit(u, WID_REGION, row);
        y += 60 * u->scale;
    }

    /* facts card */
    {
        int rows = 5;
        int card_h = (34 * rows + 20) * u->scale;
        RECT c = mk(u, 16 * u->scale, y, w - 32 * u->scale, card_h);
        int ry = c.top + 10 * u->scale;
        card(u, c);

        if (st->state == TSTATE_CONNECTED) {
            struct { const wchar_t *k; wchar_t v[160]; } facts[5];
            wchar_t a[64], b[64];
            tunnel_elapsed_text(a, 64);
            tunnel_bytes_text(st->sent, b, 64);

            wcscpy(facts[0].v, region_display_name(st->client_region));
            facts[0].k = tr(S_REGION);
            swprintf(facts[1].v, 160, L"%ls", a); facts[1].k = tr(S_ELAPSED);
            swprintf(facts[2].v, 160, L"%ls", b);  facts[2].k = tr(S_SENT);
            tunnel_bytes_text(st->received, b, 64);
            swprintf(facts[3].v, 160, L"%ls", b);  facts[3].k = tr(S_RECEIVED);
            swprintf(facts[4].v, 160, L"%ls%ls%ls",
                     st->protocol[0] ? st->protocol : L"-",
                     st->server_address[0] ? L"  •  " : L"",
                     st->server_address[0] ? st->server_address : L"");
            facts[4].k = tr(S_PROTOCOL);

            {
                int i;
                for (i = 0; i < rows; i++) {
                    RECT k = c, vr = c;
                    k.left += 18 * u->scale; k.right -= 18 * u->scale;
                    k.top = ry; k.bottom = ry + 30 * u->scale;
                    vr = k;
                    text(u, facts[i].k, k, C_TEXT2, u->f_small, 0);
                    text(u, facts[i].v, vr, C_TEXT, u->f_body, 2);
                    ry += 34 * u->scale;
                }
            }
        } else {
            RECT k = c;
            k.left += 18 * u->scale; k.right -= 18 * u->scale;
            k.top = ry; k.bottom = ry + 40 * u->scale;
            text(u, st->state == TSTATE_STOPPED ? tr(S_DISCONNECTED)
                                                : tr(S_WAITING),
                 k, C_TEXT2, u->f_body, 1);
        }
        y += card_h + 12 * u->scale;
    }

    /* local proxy card */
    if (st->socks_port || st->http_port) {
        int card_h = (st->socks_port && st->http_port) ? 78 * u->scale : 56 * u->scale;
        RECT c = mk(u, 16 * u->scale, y, w - 32 * u->scale, card_h);
        int ry = c.top + 10 * u->scale;
        card(u, c);
        {
            RECT t = c;
            t.left += 18 * u->scale; t.right -= 18 * u->scale;
            t.top = ry; t.bottom = ry + 24 * u->scale;
            text(u, tr(S_LOCAL_PROXY), t, C_TEXT, u->f_body, 0);
            ry += 26 * u->scale;
        }
        if (st->socks_port) {
            RECT t = c;
            t.left += 18 * u->scale; t.right -= 18 * u->scale;
            t.top = ry; t.bottom = ry + 22 * u->scale;
            swprintf(buf, 256, L"SOCKS\t127.0.0.1:%d", st->socks_port);
            text(u, buf, t, C_TEXT2, u->f_mono, 0);
            ry += 24 * u->scale;
        }
        if (st->http_port) {
            RECT t = c;
            t.left += 18 * u->scale; t.right -= 18 * u->scale;
            t.top = ry; t.bottom = ry + 22 * u->scale;
            swprintf(buf, 256, L"HTTP\t127.0.0.1:%d", st->http_port);
            text(u, buf, t, C_TEXT2, u->f_mono, 0);
        }
        y += card_h + 12 * u->scale;
    }

    /* disclaimer */
    {
        RECT t = mk(u, 24 * u->scale, y, w - 48 * u->scale, 34 * u->scale);
        UINT fmt = DT_WORDBREAK | DT_CENTER | DT_NOPREFIX;
        HFONT old;
        if (u->rtl) fmt |= DT_RTLREADING;
        old = (HFONT)SelectObject(u->hdc, u->f_small);
        SetBkMode(u->hdc, TRANSPARENT);
        SetTextColor(u->hdc, C_TEXT2);
        DrawTextW(u->hdc, tr(S_DISCLAIMER), -1, &t, fmt);
        SelectObject(u->hdc, old);
    }
}

/* ------------------------------------------------------- tab: statistics */

static void paint_statistics(UI *u, const Settings *s)
{
    int w = u->cr.right - u->cr.left;
    int y = 14 * u->scale;
    unsigned long long total_s = 0, total_r = 0;
    int i, n = stats_count();
    wchar_t buf[256], a[64], b[64];

    (void)s;

    for (i = 0; i < n; i++) {
        total_s += stats_at(i)->sent;
        total_r += stats_at(i)->received;
    }

    /* totals card */
    {
        RECT c = mk(u, 16 * u->scale, y, w - 32 * u->scale, 96 * u->scale);
        card(u, c);
        {
            RECT t = c;
            t.left += 18 * u->scale; t.right -= 18 * u->scale;
            t.top += 12 * u->scale; t.bottom = t.top + 24 * u->scale;
            text(u, tr(S_STATISTICS_TOTAL), t, C_TEXT, u->f_title, 0);
        }
        tunnel_bytes_text(total_r, a, 64);
        tunnel_bytes_text(total_s, b, 64);
        {
            RECT t = c;
            t.left += 18 * u->scale; t.right -= 18 * u->scale;
            t.top += 42 * u->scale; t.bottom = t.top + 22 * u->scale;
            text(u, tr(S_RECEIVED), t, C_TEXT2, u->f_small, 0);
            text(u, a, t, C_TEXT, u->f_body, 2);
        }
        {
            RECT t = c;
            t.left += 18 * u->scale; t.right -= 18 * u->scale;
            t.top += 66 * u->scale; t.bottom = t.top + 22 * u->scale;
            text(u, tr(S_SENT), t, C_TEXT2, u->f_small, 0);
            text(u, b, t, C_TEXT, u->f_body, 2);
        }
        y += 108 * u->scale;
    }

    /* per-region bars */
    {
        RECT t = mk(u, 16 * u->scale, y, w - 32 * u->scale, 24 * u->scale);
        text(u, tr(S_STATISTICS_PER_REGION), t, C_TEXT, u->f_title, 0);
        y += 30 * u->scale;
    }

    if (n == 0) {
        RECT t = mk(u, 16 * u->scale, y, w - 32 * u->scale, 30 * u->scale);
        text(u, tr(S_STATISTICS_EMPTY), t, C_TEXT2, u->f_body, 1);
    } else {
        unsigned long long maxv = 1;
        for (i = 0; i < n; i++) {
            unsigned long long v = stats_at(i)->sent + stats_at(i)->received;
            if (v > maxv) maxv = v;
        }
        for (i = 0; i < n; i++) {
            const RegionStat *r = stats_at(i);
            unsigned long long v = r->sent + r->received;
            int bar_max = (w - 32 * u->scale) - 140 * u->scale;
            int bar_w = (int)((double)v / (double)maxv * bar_max);
            int row_h = 40 * u->scale;
            RECT name = mk(u, 16 * u->scale, y, 140 * u->scale, 22 * u->scale);
            RECT track = mk(u, 156 * u->scale, y + 4 * u->scale, bar_max, 14 * u->scale);
            RECT val = mk(u, 16 * u->scale, y + 22 * u->scale,
                          w - 32 * u->scale, 18 * u->scale);

            text(u, region_display_name(r->region), name, C_TEXT, u->f_body, 0);
            round_fill(u, track, 7 * u->scale, C_DIVIDER, C_DIVIDER);
            if (bar_w > 2) {
                RECT bar = track;
                if (u->rtl) bar.left = bar.right - bar_w;
                else        bar.right = bar.left + bar_w;
                round_fill(u, bar, 7 * u->scale, C_ACCENT, C_ACCENT);
            }
            tunnel_bytes_text(v, a, 64);
            swprintf(buf, 256, L"%ls", a);
            text(u, buf, val, C_TEXT2, u->f_small, 0);
            (void)b;
            y += row_h;
        }
    }
}

/* --------------------------------------------------------- tab: settings */

typedef struct { int id; RowKind kind; StrId title; StrId summary; int *flag;
                 const wchar_t *value; int disabled; } RowDef;

static const wchar_t *protocol_label(const wchar_t *code)
{
    if (!code || !*code) return tr(S_PROTOCOL_ALL);
    return code;
}

static void paint_settings(UI *u, Settings *s)
{
    int w = u->cr.right - u->cr.left;
    int y = 0;
    (void)w;
    RowDef rows[12];
    int n = 0, i;
    int scroll = u->scroll[TAB_SETTINGS];

    (void)u;
    rows[n].id = WID_SETTING + 0; rows[n].kind = ROW_SWITCH;
    rows[n].title = S_TUNNEL_WHOLE_DEVICE; rows[n].summary = S_TUNNEL_WHOLE_DEVICE_SUMMARY;
    rows[n].flag = &s->tunnel_whole_device; rows[n].value = NULL; rows[n].disabled = 0; n++;

    rows[n].id = WID_SETTING + 1; rows[n].kind = ROW_SWITCH;
    rows[n].title = S_SETTINGS_SYSTEM_PROXY; rows[n].summary = S_SETTINGS_SYSTEM_PROXY_SUMMARY;
    rows[n].flag = &s->set_system_proxy; rows[n].value = NULL;
    rows[n].disabled = !s->tunnel_whole_device; n++;

    rows[n].id = WID_REGION; rows[n].kind = ROW_COMBO;
    rows[n].title = S_REGION; rows[n].summary = S_REGION_BEST;
    rows[n].flag = NULL; rows[n].value = region_display_name(s->region); rows[n].disabled = 0; n++;

    rows[n].id = WID_PROTOCOL; rows[n].kind = ROW_COMBO;
    rows[n].title = S_SETTINGS_PROTOCOL; rows[n].summary = S_SETTINGS_PROTOCOL_SUMMARY;
    rows[n].flag = NULL; rows[n].value = protocol_label(s->protocol); rows[n].disabled = 0; n++;

    rows[n].id = WID_SETTING + 4; rows[n].kind = ROW_SWITCH;
    rows[n].title = S_SETTINGS_UPSTREAM_PROXY; rows[n].summary = S_SETTINGS_UPSTREAM_PROXY_SUMMARY;
    rows[n].flag = &s->upstream_proxy_enabled; rows[n].value = NULL; rows[n].disabled = 0; n++;

    rows[n].id = WID_UPSTREAM; rows[n].kind = ROW_ACTION;
    rows[n].title = S_OPTIONS_PROXY; rows[n].summary = S_UPSTREAM_HOST;
    rows[n].flag = NULL;
    rows[n].value = s->upstream_host[0] ? s->upstream_host : L"-";
    rows[n].disabled = !s->upstream_proxy_enabled; n++;

    rows[n].id = WID_SETTING + 6; rows[n].kind = ROW_SWITCH;
    rows[n].title = S_SETTINGS_START_WITH_WINDOWS; rows[n].summary = S_SETTINGS_START_WITH_WINDOWS_SUMMARY;
    rows[n].flag = &s->start_with_windows; rows[n].value = NULL; rows[n].disabled = 0; n++;

    rows[n].id = WID_SETTING + 7; rows[n].kind = ROW_SWITCH;
    rows[n].title = S_SETTINGS_MINIMIZE_TRAY; rows[n].summary = S_SETTINGS_MINIMIZE_TRAY_SUMMARY;
    rows[n].flag = &s->minimize_to_tray; rows[n].value = NULL; rows[n].disabled = 0; n++;

    rows[n].id = WID_SETTING + 8; rows[n].kind = ROW_SWITCH;
    rows[n].title = S_SETTINGS_AUTO_OPEN_HOMEPAGE; rows[n].summary = S_SETTINGS_AUTO_OPEN_HOMEPAGE_SUMMARY;
    rows[n].flag = &s->auto_open_homepage; rows[n].value = NULL; rows[n].disabled = 0; n++;

    rows[n].id = WID_SETTING + 9; rows[n].kind = ROW_SWITCH;
    rows[n].title = S_SETTINGS_DISABLE_TIMEOUTS; rows[n].summary = S_SETTINGS_DISABLE_TIMEOUTS_SUMMARY;
    rows[n].flag = &s->disable_timeouts; rows[n].value = NULL; rows[n].disabled = 0; n++;

    rows[n].id = WID_SETTING + 10; rows[n].kind = ROW_SWITCH;
    rows[n].title = S_SETTINGS_LAN_SHARING; rows[n].summary = S_SETTINGS_LAN_SHARING_SUMMARY;
    rows[n].flag = &s->lan_proxy_sharing; rows[n].value = NULL; rows[n].disabled = 0; n++;

    rows[n].id = WID_LANG; rows[n].kind = ROW_COMBO;
    rows[n].title = S_SETTINGS_LANGUAGE; rows[n].summary = S_SETTINGS_LANGUAGE_SUMMARY;
    rows[n].flag = NULL;
    rows[n].value = s->persian ? tr(S_LANGUAGE_FA) : tr(S_LANGUAGE_EN);
    rows[n].disabled = 0; n++;

    u->content_h[TAB_SETTINGS] = y + n * (62 * u->scale);

    for (i = 0; i < n; i++) {
        int ry = y + i * (62 * u->scale) - scroll;
        if (ry > u->view_h || ry + 62 * u->scale < ui_tabbar_h(u)) continue;
        setting_row(u, rows[i].id, 0, ry, w, 62 * u->scale,
                    tr(rows[i].title), tr(rows[i].summary),
                    rows[i].kind, rows[i].flag, rows[i].value, rows[i].disabled);
    }
}

/* ------------------------------------------------------------- tab: logs */

static int g_log_filter = 0;   /* 0 all, 1 info, 2 diagnostic */

void ui_set_log_filter(int level) { g_log_filter = level; }
int  ui_get_log_filter(void) { return g_log_filter; }

static void paint_logs(UI *u, const Settings *s)
{
    int w = u->cr.right - u->cr.left;
    int y = 0;
    int i, n = log_ring_count();
    int line_h = 20 * u->scale;
    int view_top = 0;
    int view_h = u->view_h;
    int first;
    int shown = 0;
    int idx[LOG_CAPACITY];
    int scroll = u->scroll[TAB_LOGS];

    (void)s;

    /* filter chips */
    {
        const StrId labels[3] = { S_LOG_FILTER_ALL, S_LOG_FILTER_INFO, S_LOG_FILTER_DIAGNOSTIC };
        int cw = 92 * u->scale, gap = 8 * u->scale;
        int k;
        for (k = 0; k < 3; k++) {
            RECT rc = mk(u, 16 * u->scale + k * (cw + gap), y + 10 * u->scale,
                         cw, 30 * u->scale);
            COLORREF bg = (g_log_filter == k) ? C_BRAND : C_CARD;
            COLORREF fg = (g_log_filter == k) ? RGB(255,255,255) : C_TEXT;
            round_fill(u, rc, 15 * u->scale, bg, (g_log_filter == k) ? bg : C_DIVIDER);
            text(u, tr(labels[k]), rc, fg, u->f_body, 1);
            ui_add_hit(u, WID_FILTER + k, rc);
        }
    }
    y += 52 * u->scale;
    view_top = y;
    view_h = u->view_h - y;

    for (i = 0; i < n; i++) {
        const LogEntry *e = log_ring_at(i);
        if (!e) continue;
        if (g_log_filter == 1 && e->level > 1) continue;
        if (g_log_filter == 2 && e->level != 3) continue;
        idx[shown++] = i;
    }

    u->content_h[TAB_LOGS] = shown * line_h;

    /* buttons */
    {
        int bw = 120 * u->scale;
        RECT rc = mk(u, w - 16 * u->scale - bw, u->view_h - 40 * u->scale, bw, 30 * u->scale);
        button(u, WID_COPY_LOGS, rc, tr(S_COPY_LOGS), C_CARD, C_TEXT, 4 * u->scale);
        frame(u, rc, C_DIVIDER);
        rc = mk(u, w - 16 * u->scale - bw * 2 - 8 * u->scale,
                u->view_h - 40 * u->scale, bw, 30 * u->scale);
        button(u, WID_CLEAR_LOGS, rc, tr(S_CLEAR_LOGS), C_CARD, C_TEXT, 4 * u->scale);
        frame(u, rc, C_DIVIDER);
    }

    if (shown == 0) {
        RECT t = mk(u, 0, view_top + 30 * u->scale, w, 30 * u->scale);
        text(u, tr(S_NO_LOGS), t, C_TEXT2, u->f_body, 1);
        return;
    }

    first = scroll / line_h;
    if (first < 0) first = 0;

    {
        /* clip the list to its viewport */
        HRGN clip = CreateRectRgn(u->cr.left, u->cr.top + view_top,
                                  u->cr.right, u->cr.top + view_top + view_h
                                              - 46 * u->scale);
        SelectClipRgn(u->hdc, clip);
        for (i = first; i < shown; i++) {
            const LogEntry *e = log_ring_at(idx[i]);
            RECT rc = mk(u, 16 * u->scale,
                         view_top + (i * line_h) - scroll,
                         w - 32 * u->scale, line_h);
            COLORREF c;
            if ((i * line_h) - scroll > view_h) break;
            switch (e->level) {
            case 2:  c = C_ERR;  break;
            case 1:  c = C_WARN; break;
            case 3:  c = C_TEXT2; break;
            default: c = C_TEXT; break;
            }
            text(u, e->text, rc, c, u->f_mono, 0);
        }
        SelectClipRgn(u->hdc, NULL);
        DeleteObject(clip);
    }
}

/* -------------------------------------------------------------- top level */

void ui_begin(UI *u, HDC hdc, const RECT *client)
{
    int w = client->right - client->left;
    int h = client->bottom - client->top;

    u->hdc = hdc;
    u->cr = *client;
    u->nhits = 0;

    if (!u->surface || u->surf_w != w || u->surf_h != h) {
        if (u->surface) { DeleteObject(u->surface); DeleteDC(u->surf_dc); }
        u->surf_dc = CreateCompatibleDC(hdc);
        u->surface = CreateCompatibleBitmap(hdc, w, h);
        u->surf_w = w; u->surf_h = h;
    }
    SelectObject(u->surf_dc, u->surface);
    u->hdc = u->surf_dc;
}

void ui_end(UI *u, HDC hdc)
{
    BitBlt(hdc, u->cr.left, u->cr.top,
           u->cr.right - u->cr.left, u->cr.bottom - u->cr.top,
           u->surf_dc, 0, 0, SRCCOPY);
    u->hdc = hdc;
}

static void paint_toolbar(UI *u)
{
    int h = ui_toolbar_h(u);
    RECT bar = { u->cr.left, u->cr.top, u->cr.right, u->cr.top + h };
    RECT icon_rc, title_rc;
    int pad = 12 * u->scale;

    fill(u, bar, C_BRAND);
    icon_rc.left = u->rtl ? bar.right - pad - 28 * u->scale : bar.left + pad;
    icon_rc.right = icon_rc.left + 28 * u->scale;
    icon_rc.top = bar.top + (h - 28 * u->scale) / 2;
    icon_rc.bottom = icon_rc.top + 28 * u->scale;
    draw_icon(u, u->app_icon, icon_rc.left, icon_rc.top, 28 * u->scale);

    title_rc = bar;
    title_rc.left = u->rtl ? bar.left : icon_rc.right + 10 * u->scale;
    title_rc.right = u->rtl ? icon_rc.left - 10 * u->scale : bar.right;
    text(u, tr(S_APP_NAME), title_rc, RGB(0xFF, 0xFF, 0xFF), u->f_caption, 0);

    {
        RECT line = { bar.left, bar.bottom - 1, bar.right, bar.bottom };
        fill(u, line, C_BRAND_DK);
    }
}

static void paint_tabbar(UI *u)
{
    int w = u->cr.right - u->cr.left;
    int h = ui_tabbar_h(u);
    int top = ui_toolbar_h(u);
    RECT bar = { u->cr.left, u->cr.top + top, u->cr.right, u->cr.top + top + h };
    const StrId names[TAB_COUNT] = { S_TAB_HOME, S_TAB_STATISTICS,
                                     S_TAB_SETTINGS, S_TAB_LOGS };
    int tw = w / TAB_COUNT;
    int i;

    fill(u, bar, C_BRAND);
    for (i = 0; i < TAB_COUNT; i++) {
        RECT rc = mk(u, i * tw, top, tw, h);
        COLORREF fg = (u->tab == i) ? RGB(0xFF, 0xFF, 0xFF)
                                    : RGB(0xB8, 0xBE, 0xDB);
        text(u, tr(names[i]), rc, fg, u->f_title, 1);
        ui_add_hit(u, WID_TAB + i, rc);
        if (u->tab == i) {
            RECT ind = { rc.left + 6 * u->scale, rc.bottom - 3 * u->scale,
                         rc.right - 6 * u->scale, rc.bottom };
            fill(u, ind, RGB(0xFF, 0xFF, 0xFF));
        }
    }
}

static void paint_bottom(UI *u, const Settings *s)
{
    int w = u->cr.right - u->cr.left;
    int h = ui_bottom_h(u);
    int top = u->cr.bottom - h;
    TunnelStatus *st = tunnel_status();
    int running = (st->state != TSTATE_STOPPED);

    (void)s;
    {
        RECT bar = { u->cr.left, top, u->cr.right, u->cr.bottom };
        fill(u, bar, C_CARD);
        {
            RECT line = { bar.left, bar.top, bar.right, bar.top + 1 };
            fill(u, line, C_DIVIDER);
        }
    }

    /* connecting progress strip */
    if (st->state == TSTATE_CONNECTING || st->state == TSTATE_STARTING) {
        static int phase = 0;
        int seg_w = 40 * u->scale;
        RECT ind = { u->cr.left, top - 3 * u->scale, u->cr.right, top };
        fill(u, ind, C_DIVIDER);
        {
            RECT moving;
            int pos = (phase % (w + seg_w)) - seg_w;
            moving.left = u->cr.left + (u->rtl ? (w - pos - seg_w) : pos);
            moving.right = moving.left + seg_w;
            moving.top = top - 3 * u->scale;
            moving.bottom = top;
            fill(u, moving, C_ACCENT);
        }
        phase += 6;
    }

    {
        int pad = 12 * u->scale;
        int bw = (w - pad * 3) / 2;
        RECT b1 = mk(u, pad, top + 11 * u->scale, bw, 36 * u->scale);
        RECT b2 = mk(u, pad * 2 + bw, top + 11 * u->scale, bw, 36 * u->scale);
        if (running) {
            button(u, WID_CONNECT, b1, tr(S_STOP),
                   RGB(0xD4, 0x3B, 0x33), RGB(0xFF, 0xFF, 0xFF), 4 * u->scale);
        } else {
            button(u, WID_CONNECT, b1, tr(S_START), C_ACCENT,
                   RGB(0xFF, 0xFF, 0xFF), 4 * u->scale);
        }
        button(u, WID_BROWSER, b2, tr(S_OPEN_BROWSER), C_CARD, C_TEXT,
               4 * u->scale);
        frame(u, b2, C_DIVIDER);
    }
}

void ui_paint(UI *u, const Settings *s)
{
    RECT content;
    int top = ui_toolbar_h(u) + ui_tabbar_h(u);
    int bottom = ui_bottom_h(u);

    fill(u, u->cr, C_BG);
    paint_toolbar(u);
    paint_tabbar(u);

    content.left = u->cr.left;
    content.right = u->cr.right;
    content.top = u->cr.top + top;
    content.bottom = u->cr.bottom - bottom;
    u->view_h = content.bottom - content.top;

    /* Render the tab into the content viewport. */
    {
        RECT saved = u->cr;
        HRGN clip;
        u->cr = content;
        clip = CreateRectRgn(content.left, content.top,
                             content.right, content.bottom);
        SelectClipRgn(u->hdc, clip);

        /* translate the origin to the top-left of the content area */
        SaveDC(u->hdc);
        SetViewportOrgEx(u->hdc, content.left, content.top, NULL);
        u->cr.left = 0; u->cr.right = content.right - content.left;
        u->cr.top = 0;  u->cr.bottom = content.bottom - content.top;

        switch (u->tab) {
        case TAB_HOME:      paint_home(u, s); break;
        case TAB_STATISTICS:paint_statistics(u, s); break;
        case TAB_SETTINGS:  paint_settings(u, (Settings *)s); break;
        case TAB_LOGS:      paint_logs(u, s); break;
        }

        RestoreDC(u->hdc, -1);
        SelectClipRgn(u->hdc, NULL);
        DeleteObject(clip);
        u->cr = saved;
    }

    paint_bottom(u, s);
}

/* -------------------------------------------------------------- popups */

int ui_popup_pick_strings(HWND hwnd, const wchar_t *const *labels, int count,
                          int current, RECT anchor)
{
    HMENU menu = CreatePopupMenu();
    int i, choice = -1;
    POINT pt;

    if (!menu) return -1;
    for (i = 0; i < count; i++) {
        UINT flags = MF_STRING | (i == current ? MF_CHECKED : 0);
        AppendMenuW(menu, flags, (UINT_PTR)(i + 1), labels[i]);
    }
    pt.x = anchor.left;
    pt.y = anchor.bottom;
    ClientToScreen(hwnd, &pt);

    {
        UINT cmd = TrackPopupMenu(menu, TPM_RETURNCMD | TPM_RIGHTALIGN | TPM_TOPALIGN,
                                  pt.x, pt.y, 0, hwnd, NULL);
        if (cmd >= 1 && cmd <= (UINT)count) choice = (int)cmd - 1;
    }
    DestroyMenu(menu);
    return choice;
}

int ui_popup_pick(HWND hwnd, const wchar_t *const *labels, int count,
                  int current, RECT anchor)
{
    return ui_popup_pick_strings(hwnd, labels, count, current, anchor);
}

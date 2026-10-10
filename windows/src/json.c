/*
 * Shir o Khorshid for Windows - minimal JSON parser / writer.
 * GPLv3. See shir.h for the full notice.
 */

#include "shir.h"
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

/* ------------------------------------------------------------------ helpers */

char *wchar_to_utf8(const wchar_t *s)
{
#ifdef _WIN32
    int n;
    char *out;
    if (!s) return NULL;
    n = WideCharToMultiByte(CP_UTF8, 0, s, -1, NULL, 0, NULL, NULL);
    if (n <= 0) return NULL;
    out = (char *)malloc((size_t)n + 1);
    if (!out) return NULL;
    WideCharToMultiByte(CP_UTF8, 0, s, -1, out, n, NULL, NULL);
    out[n] = 0;
    return out;
#else
    size_t i, n = 0;
    char *out;
    if (!s) return NULL;
    for (n = 0; s[n]; n++) { /* length */ }
    out = (char *)malloc(n * 4 + 1);
    if (!out) return NULL;
    {
        size_t o = 0;
        for (i = 0; i < n; i++) {
            unsigned long c = (unsigned long)s[i];
            if (c < 0x80) out[o++] = (char)c;
            else if (c < 0x800) {
                out[o++] = (char)(0xC0 | (c >> 6));
                out[o++] = (char)(0x80 | (c & 0x3F));
            } else {
                out[o++] = (char)(0xE0 | (c >> 12));
                out[o++] = (char)(0x80 | ((c >> 6) & 0x3F));
                out[o++] = (char)(0x80 | (c & 0x3F));
            }
        }
        out[o] = 0;
    }
    return out;
#endif
}

wchar_t *utf8_to_wchar(const char *s)
{
#ifdef _WIN32
    int n;
    wchar_t *out;
    if (!s) return NULL;
    n = MultiByteToWideChar(CP_UTF8, 0, s, -1, NULL, 0);
    if (n <= 0) return NULL;
    out = (wchar_t *)malloc(((size_t)n + 1) * sizeof(wchar_t));
    if (!out) return NULL;
    MultiByteToWideChar(CP_UTF8, 0, s, -1, out, n);
    out[n] = 0;
    return out;
#else
    size_t i = 0, o = 0, n;
    wchar_t *out;
    if (!s) return NULL;
    n = strlen(s);
    out = (wchar_t *)malloc((n + 1) * sizeof(wchar_t));
    if (!out) return NULL;
    while (i < n) {
        unsigned char c = (unsigned char)s[i];
        unsigned long cp;
        int extra;
        if (c < 0x80) { cp = c; extra = 0; }
        else if ((c & 0xE0) == 0xC0) { cp = c & 0x1F; extra = 1; }
        else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; extra = 2; }
        else if ((c & 0xF8) == 0xF0) { cp = c & 0x07; extra = 3; }
        else { cp = c; extra = 0; }
        i++;
        while (extra-- > 0 && i < n) cp = (cp << 6) | ((unsigned char)s[i++] & 0x3F);
        out[o++] = (wchar_t)cp;
    }
    out[o] = 0;
    return out;
#endif
}

void free_conv(void *p) { free(p); }

/* ------------------------------------------------------------------- parser */

typedef struct { const char *p; int ok; } JP;

static JValue *jv_new(JType t)
{
    JValue *v = (JValue *)calloc(1, sizeof(JValue));
    if (v) v->type = t;
    return v;
}

static void jv_free_list(JValue *v)
{
    while (v) {
        JValue *next = v->next;
        free(v->str);
        free(v->key);
        jv_free_list(v->value);
        jv_free_list(v->items);
        free(v);
        v = next;
    }
}

void json_free(JValue *v) { jv_free_list(v); }

static void skip_ws(JP *j)
{
    while (*j->p && (unsigned char)*j->p <= ' ') j->p++;
}

static char *parse_string_raw(JP *j)
{
    const char *p;
    char *out;
    size_t cap, len;
    if (*j->p != '"') { j->ok = 0; return NULL; }
    j->p++;
    cap = 64; len = 0;
    out = (char *)malloc(cap);
    if (!out) { j->ok = 0; return NULL; }
    p = j->p;
    while (*p && *p != '"') {
        unsigned long cp = 0;
        if (len + 8 >= cap) {
            cap *= 2;
            { char *t = (char *)realloc(out, cap); if (!t) { free(out); j->ok = 0; return NULL; } out = t; }
        }
        if (*p == '\\') {
            p++;
            switch (*p) {
            case 'n': out[len++] = '\n'; p++; break;
            case 't': out[len++] = '\t'; p++; break;
            case 'r': out[len++] = '\r'; p++; break;
            case 'b': out[len++] = '\b'; p++; break;
            case 'f': out[len++] = '\f'; p++; break;
            case '/': out[len++] = '/';  p++; break;
            case '"': out[len++] = '"';  p++; break;
            case '\\': out[len++] = '\\'; p++; break;
            case 'u': {
                int k;
                p++;
                cp = 0;
                for (k = 0; k < 4 && *p; k++, p++) {
                    char c = *p;
                    cp <<= 4;
                    if (c >= '0' && c <= '9') cp |= (unsigned long)(c - '0');
                    else if (c >= 'a' && c <= 'f') cp |= (unsigned long)(c - 'a' + 10);
                    else if (c >= 'A' && c <= 'F') cp |= (unsigned long)(c - 'A' + 10);
                }
                if (cp < 0x80) out[len++] = (char)cp;
                else if (cp < 0x800) {
                    out[len++] = (char)(0xC0 | (cp >> 6));
                    out[len++] = (char)(0x80 | (cp & 0x3F));
                } else {
                    out[len++] = (char)(0xE0 | (cp >> 12));
                    out[len++] = (char)(0x80 | ((cp >> 6) & 0x3F));
                    out[len++] = (char)(0x80 | (cp & 0x3F));
                }
                break;
            }
            default: out[len++] = *p++; break;
            }
        } else {
            out[len++] = *p++;
        }
    }
    if (*p != '"') { free(out); j->ok = 0; return NULL; }
    j->p = p + 1;
    out[len] = 0;
    return out;
}

static JValue *parse_value(JP *j);

static JValue *parse_object(JP *j)
{
    JValue *o = jv_new(JOBJ);
    JValue *tail = NULL;
    j->p++; /* '{' */
    skip_ws(j);
    if (*j->p == '}') { j->p++; return o; }
    for (;;) {
        JValue *m;
        skip_ws(j);
        if (*j->p != '"') { j->ok = 0; break; }
        m = jv_new(JNULL);
        m->key = parse_string_raw(j);
        skip_ws(j);
        if (*j->p != ':') { j->ok = 0; free(m->key); free(m); break; }
        j->p++;
        skip_ws(j);
        m->value = parse_value(j);
        if (tail) tail->next = m; else o->items = m;
        tail = m;
        o->count++;
        skip_ws(j);
        if (*j->p == ',') { j->p++; continue; }
        if (*j->p == '}') { j->p++; }
        else j->ok = 0;
        break;
    }
    return o;
}

static JValue *parse_array(JP *j)
{
    JValue *a = jv_new(JARR);
    JValue *tail = NULL;
    j->p++; /* '[' */
    skip_ws(j);
    if (*j->p == ']') { j->p++; return a; }
    for (;;) {
        JValue *m = parse_value(j);
        if (!m) break;
        if (tail) tail->next = m; else a->items = m;
        tail = m;
        a->count++;
        skip_ws(j);
        if (*j->p == ',') { j->p++; continue; }
        if (*j->p == ']') { j->p++; }
        else j->ok = 0;
        break;
    }
    return a;
}

static JValue *parse_value(JP *j)
{
    JValue *v = NULL;
    skip_ws(j);
    switch (*j->p) {
    case '{': v = parse_object(j); break;
    case '[': v = parse_array(j); break;
    case '"': {
        v = jv_new(JSTR);
        v->str = parse_string_raw(j);
        break;
    }
    case 't':
        if (!strncmp(j->p, "true", 4)) { v = jv_new(JBOOL); v->boolean = 1; j->p += 4; }
        else j->ok = 0;
        break;
    case 'f':
        if (!strncmp(j->p, "false", 5)) { v = jv_new(JBOOL); v->boolean = 0; j->p += 5; }
        else j->ok = 0;
        break;
    case 'n':
        if (!strncmp(j->p, "null", 4)) { v = jv_new(JNULL); j->p += 4; }
        else j->ok = 0;
        break;
    default:
        if (*j->p == '-' || isdigit((unsigned char)*j->p)) {
            char *end;
            v = jv_new(JNUM);
            v->num = strtod(j->p, &end);
            if (end == j->p) j->ok = 0;
            j->p = end;
        } else {
            j->ok = 0;
        }
        break;
    }
    return v;
}

JValue *json_parse(const char *text)
{
    JP j;
    JValue *v;
    if (!text) return NULL;
    j.p = text; j.ok = 1;
    skip_ws(&j);
    v = parse_value(&j);
    if (v && !j.ok) { json_free(v); return NULL; }
    return v;
}

JValue *json_get(const JValue *obj, const char *key)
{
    JValue *m;
    if (!obj || obj->type != JOBJ) return NULL;
    for (m = obj->items; m; m = m->next)
        if (m->key && !strcmp(m->key, key)) return m->value;
    return NULL;
}

const char *json_get_str(const JValue *obj, const char *key, const char *dflt)
{
    JValue *v = json_get(obj, key);
    return (v && v->type == JSTR) ? v->str : dflt;
}

double json_get_num(const JValue *obj, const char *key, double dflt)
{
    JValue *v = json_get(obj, key);
    if (!v) return dflt;
    if (v->type == JNUM) return v->num;
    if (v->type == JSTR) return strtod(v->str, NULL);
    return dflt;
}

int json_get_bool(const JValue *obj, const char *key, int dflt)
{
    JValue *v = json_get(obj, key);
    if (!v) return dflt;
    if (v->type == JBOOL) return v->boolean;
    if (v->type == JNUM) return v->num != 0;
    if (v->type == JSTR) return !strcmp(v->str, "true");
    return dflt;
}

/* ------------------------------------------------------------------- writer */

#define JB_DEPTH 24

struct JsonBufPriv {
    int need_comma[JB_DEPTH];
    int depth;
};

/* The public struct only exposes data/len/cap; keep state alongside it. */
static struct JsonBufPriv jb_state;
static int jb_expect_value;   /* 1 immediately after a member name was written */

void jb_init(JsonBuf *b)
{
    b->cap = 8192;
    b->len = 0;
    b->data = (char *)malloc(b->cap);
    if (b->data) b->data[0] = 0;
    jb_state.depth = 0;
    jb_expect_value = 0;
    memset(jb_state.need_comma, 0, sizeof(jb_state.need_comma));
}

void jb_free(JsonBuf *b) { free(b->data); b->data = NULL; b->len = b->cap = 0; }

static void jb_grow(JsonBuf *b, size_t extra)
{
    if (b->len + extra + 1 < b->cap) return;
    while (b->len + extra + 1 >= b->cap) b->cap *= 2;
    b->data = (char *)realloc(b->data, b->cap);
}

static void jb_comma(JsonBuf *b)
{
    int d = jb_state.depth;
    if (jb_expect_value) { jb_expect_value = 0; return; }
    if (d > 0 && jb_state.need_comma[d - 1]) jb_raw(b, ",");
}

void jb_raw(JsonBuf *b, const char *s)
{
    size_t n;
    if (!s || !b->data) return;
    n = strlen(s);
    jb_grow(b, n);
    memcpy(b->data + b->len, s, n);
    b->len += n;
    b->data[b->len] = 0;
}

void jb_begin(JsonBuf *b, char brace)
{
    jb_comma(b);
    jb_raw(b, brace == '{' ? "{" : "[");
    if (jb_state.depth < JB_DEPTH) {
        jb_state.need_comma[jb_state.depth] = 0;
        jb_state.depth++;
    }
}

void jb_end(JsonBuf *b, char brace)
{
    if (jb_state.depth > 0) jb_state.depth--;
    /* Accept either the opening or the closing brace/bracket. */
    jb_raw(b, (brace == '[' || brace == ']') ? "]" : "}");
    if (jb_state.depth > 0) jb_state.need_comma[jb_state.depth - 1] = 1;
}

static void jb_str_value(JsonBuf *b, const char *v);

/* Emit a value inside an array, inserting a separator when needed. */
void jb_item_str(JsonBuf *b, const char *v)
{
    int d = jb_state.depth;
    if (d > 0 && jb_state.need_comma[d - 1]) jb_raw(b, ",");
    jb_str_value(b, v);
    if (d > 0) jb_state.need_comma[d - 1] = 1;
}

/* Emit a quoted string as the value of an already-written member name. */
void jb_value_str(JsonBuf *b, const char *v)
{
    jb_str_value(b, v);
    if (jb_state.depth > 0) jb_state.need_comma[jb_state.depth - 1] = 1;
    jb_expect_value = 0;
}

void jb_key(JsonBuf *b, const char *k)
{
    char *esc;
    size_t i, n, cap;
    int d = jb_state.depth;
    if (jb_expect_value) jb_expect_value = 0;
    else if (d > 0 && jb_state.need_comma[d - 1]) jb_raw(b, ",");
    n = strlen(k);
    cap = n * 6 + 4;
    esc = (char *)malloc(cap);
    if (!esc) return;
    {
        size_t o = 0;
        esc[o++] = '"';
        for (i = 0; i < n; i++) {
            unsigned char c = (unsigned char)k[i];
            switch (c) {
            case '"':  memcpy(esc + o, "\\\"", 2); o += 2; break;
            case '\\': memcpy(esc + o, "\\\\", 2); o += 2; break;
            case '\n': memcpy(esc + o, "\\n", 2);  o += 2; break;
            case '\r': memcpy(esc + o, "\\r", 2);  o += 2; break;
            case '\t': memcpy(esc + o, "\\t", 2);  o += 2; break;
            default:
                if (c < 0x20) { o += (size_t)sprintf(esc + o, "\\u%04x", c); }
                else esc[o++] = (char)c;
            }
        }
        esc[o++] = '"';
        esc[o++] = ':';
        esc[o] = 0;
    }
    jb_raw(b, esc);
    free(esc);
    jb_expect_value = 1;
}

static void jb_str_value(JsonBuf *b, const char *v)
{
    size_t i, n;
    jb_grow(b, strlen(v) * 6 + 3);
    b->data[b->len++] = '"';
    n = strlen(v);
    for (i = 0; i < n; i++) {
        unsigned char c = (unsigned char)v[i];
        switch (c) {
        case '"':  memcpy(b->data + b->len, "\\\"", 2); b->len += 2; break;
        case '\\': memcpy(b->data + b->len, "\\\\", 2); b->len += 2; break;
        case '\n': memcpy(b->data + b->len, "\\n", 2);  b->len += 2; break;
        case '\r': memcpy(b->data + b->len, "\\r", 2);  b->len += 2; break;
        case '\t': memcpy(b->data + b->len, "\\t", 2);  b->len += 2; break;
        default:
            if (c < 0x20) b->len += (size_t)sprintf(b->data + b->len, "\\u%04x", c);
            else b->data[b->len++] = (char)c;
        }
    }
    b->data[b->len++] = '"';
    b->data[b->len] = 0;
}

void jb_str(JsonBuf *b, const char *k, const char *v)
{
    jb_key(b, k);
    jb_value_str(b, v ? v : "");
}

void jb_str_w(JsonBuf *b, const char *k, const wchar_t *v)
{
    char *u = wchar_to_utf8(v ? v : L"");
    jb_key(b, k);
    jb_value_str(b, u ? u : "");
    free(u);
}

void jb_num(JsonBuf *b, const char *k, double v)
{
    char tmp[64];
    if (v == (double)(long long)v) snprintf(tmp, sizeof tmp, "%lld", (long long)v);
    else snprintf(tmp, sizeof tmp, "%.10g", v);
    jb_key(b, k);
    jb_raw(b, tmp);
    if (jb_state.depth > 0) jb_state.need_comma[jb_state.depth - 1] = 1;
    jb_expect_value = 0;
}

void jb_bool(JsonBuf *b, const char *k, int v)
{
    jb_key(b, k);
    jb_raw(b, v ? "true" : "false");
    if (jb_state.depth > 0) jb_state.need_comma[jb_state.depth - 1] = 1;
    jb_expect_value = 0;
}

void jb_str_array(JsonBuf *b, const char *k, const char *const *items, int n)
{
    int i;
    jb_key(b, k);
    jb_begin(b, '[');
    for (i = 0; i < n; i++) jb_item_str(b, items[i]);
    jb_end(b, ']');
}

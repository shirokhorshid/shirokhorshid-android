/*
 * Unit tests for the portable JSON layer. Compiled natively (gcc/clang) as
 * well as for Windows, so the config writer can be validated without a
 * Windows machine.
 */

#include "../src/shir.h"
#include <string.h>
#include <stdio.h>

static int failures = 0;
static int checks = 0;

static void ok(const char *name, int cond)
{
    checks++;
    if (!cond) { failures++; printf("  FAIL  %s\n", name); }
    else       { printf("  ok    %s\n", name); }
}

static void test_parse(void)
{
    const char *txt =
        "{\"noticeType\":\"Tunnels\",\"data\":{\"count\":3},\"id\":\"abc\","
        "\"flag\":true,\"ratio\":1.5,\"list\":[\"a\",\"b\"],\"nul\":null,"
        "\"escaped\":\"line\\n\\\"q\\\" \\u0634\\u06cc\\u0631\"}";
    JValue *v = json_parse(txt);
    ok("parse root", v != NULL && v->type == JOBJ);
    if (!v) return;
    ok("string member", !strcmp(json_get_str(v, "noticeType", ""), "Tunnels"));
    ok("bool member", json_get_bool(v, "flag", 0) == 1);
    ok("num member", json_get_num(v, "ratio", 0) == 1.5);
    ok("missing uses default", !strcmp(json_get_str(v, "nope", "dflt"), "dflt"));
    {
        JValue *d = json_get(v, "data");
        ok("nested object", d && d->type == JOBJ);
        ok("nested num", json_get_num(d, "count", 0) == 3);
    }
    {
        JValue *l = json_get(v, "list");
        ok("array type", l && l->type == JARR && l->count == 2);
        ok("array item 0", l && l->items && !strcmp(l->items->str, "a"));
    }
    {
        const char *e = json_get_str(v, "escaped", "");
        wchar_t *w = utf8_to_wchar(e);
        /* شیر = U+0634 U+06CC U+0631 */
        ok("utf8 escape round-trip",
           w && wcslen(w) >= 3 && w[wcslen(w) - 3] == 0x0634
           && w[wcslen(w) - 2] == 0x06CC && w[wcslen(w) - 1] == 0x0631);
        free_conv(w);
    }
    json_free(v);
}

static void test_invalid(void)
{
    ok("rejects truncated", json_parse("{\"a\":") == NULL);
    ok("rejects garbage", json_parse("not json") == NULL);
    ok("rejects empty", json_parse("") == NULL);
}

static void test_writer(void)
{
    JsonBuf b;
    const char *items[3] = { "SSH", "OSSH", "QUIC-OSSH" };
    jb_init(&b);
    jb_begin(&b, '{');
    jb_str(&b, "PropagationChannelId", "AAA-BBB");
    jb_num(&b, "LocalSocksProxyPort", 1080);
    jb_bool(&b, "EmitDiagnosticNotices", 1);
    jb_str_array(&b, "LimitTunnelProtocols", items, 3);
    jb_key(&b, "Nested");
    jb_begin(&b, '{');
    jb_str(&b, "inner", "value");
    jb_end(&b, '}');
    jb_str_w(&b, "Unicode", L"شیر و خورشید");
    jb_end(&b, '}');

    printf("  json: %s\n", b.data);
    {
        JValue *v = json_parse(b.data);
        ok("writer output parses", v != NULL && v->type == JOBJ);
        if (v) {
            ok("writer string", !strcmp(json_get_str(v, "PropagationChannelId", ""), "AAA-BBB"));
            ok("writer number", json_get_num(v, "LocalSocksProxyPort", 0) == 1080);
            ok("writer bool", json_get_bool(v, "EmitDiagnosticNotices", 0) == 1);
            {
                JValue *a = json_get(v, "LimitTunnelProtocols");
                ok("writer array", a && a->type == JARR && a->count == 3);
                ok("writer array item", a && a->items && !strcmp(a->items->str, "SSH"));
            }
            {
                JValue *n = json_get(v, "Nested");
                ok("writer nested", n && !strcmp(json_get_str(n, "inner", ""), "value"));
            }
            {
                const char *u = json_get_str(v, "Unicode", "");
                wchar_t *w = utf8_to_wchar(u);
                ok("writer wide string", w && !wcscmp(w, L"شیر و خورشید"));
                free_conv(w);
            }
            json_free(v);
        }
    }
    jb_free(&b);
}

static void test_conversion(void)
{
    const wchar_t *src = L"شیر و خورشید — Psiphon";
    char *u8 = wchar_to_utf8(src);
    wchar_t *back = utf8_to_wchar(u8);
    ok("utf16 -> utf8 -> utf16", back && !wcscmp(back, src));
    free_conv(u8);
    free_conv(back);
}

int main(void)
{
    printf("json tests\n");
    test_parse();
    test_invalid();
    test_writer();
    test_conversion();
    printf("%d checks, %d failures\n", checks, failures);
    return failures ? 1 : 0;
}

/*
 * Tests for core_build_config_json(): the tunnel-core configuration must match
 * what the Android client's TunnelManager.buildTunnelCoreConfig() produces for
 * the same preferences. Compiled natively (gcc/clang) and parsed back with the
 * project's own JSON layer so no Windows machine is needed.
 *
 *   cc -std=c11 -I src -o /tmp/test_config tests/test_config.c \
 *       src/json.c src/core.c src/i18n.c tests/stubs.c && /tmp/test_config
 */

#include "../src/shir.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

static int failures = 0;
static int checks = 0;

static void ok(const char *name, int cond)
{
    checks++;
    if (!cond) { failures++; printf("  FAIL  %s\n", name); }
    else       { printf("  ok    %s\n", name); }
}

static JValue *build(const Settings *s)
{
    char *json = NULL;
    size_t len = 0;
    JValue *v;
    if (!core_build_config_json(s, L"C:\\data", NULL, &json, &len) || !json)
        return NULL;
    v = json_parse(json);
    if (!v) printf("    (unparseable: %s)\n", json);
    free(json);
    return v;
}

static int arr_has(JValue *arr, const char *want)
{
    JValue *it;
    if (!arr || arr->type != JARR) return 0;
    for (it = arr->items; it; it = it->next)
        if (it->type == JSTR && it->str && !strcmp(it->str, want)) return 1;
    return 0;
}

static int arr_len(JValue *arr)
{
    int n = 0;
    JValue *it;
    if (!arr || arr->type != JARR) return -1;
    for (it = arr->items; it; it = it->next) n++;
    return n;
}

static JValue *find_override(JValue *overrides, const char *id)
{
    JValue *it;
    if (!overrides || overrides->type != JARR) return NULL;
    for (it = overrides->items; it; it = it->next)
        if (it->type == JOBJ && !strcmp(json_get_str(it, "OverrideID", ""), id))
            return it;
    return NULL;
}

/* ---- auto (the default): no LimitTunnelProtocols, fronting on, beast on */
static void test_auto(void)
{
    Settings s; JValue *v;
    settings_init(&s);
    v = build(&s);
    printf("auto:\n");
    ok("builds", v != NULL);
    if (!v) return;
    ok("auto leaves LimitTunnelProtocols unset", json_get(v, "LimitTunnelProtocols") == NULL);
    ok("auto does not disable tactics", json_get(v, "DisableTactics") == NULL);
    ok("beast mode -> AggressiveEstablishment", json_get_bool(v, "AggressiveEstablishment", 0) == 1);
    ok("EmitServerAlerts", json_get_bool(v, "EmitServerAlerts", 0) == 1);
    ok("fronting overrides present", json_get(v, "FrontedMeekDialOverrides") != NULL);
    ok("fronting probability 1.0", json_get_num(v, "FrontedMeekDialOverridesProbability", 0) == 1.0);
    ok("built-in CDN scan spec", json_get_bool(v, "FrontedMeekCDNScanUseBuiltInSpec", 0) == 1);
    ok("no custom scan spec without IPs", json_get(v, "FrontedMeekCDNScanSpec") == NULL);
    ok("no latency lambda unless timeouts disabled", json_get(v, "NetworkLatencyMultiplierLambda") == NULL);
    {
        JValue *ov = json_get(v, "FrontedMeekDialOverrides");
        JValue *fp = find_override(ov, "fastly-provider");
        JValue *fa = find_override(ov, "fastly-address");
        JValue *eo = find_override(ov, "edge-original");
        ok("11 overrides (2 fastly + 9 edge)", arr_len(ov) == 11);
        ok("fastly-provider matches provider regex",
           fp && arr_has(json_get(fp, "MatchFrontingProviderIDRegexes"), "(?i)fastly"));
        ok("fastly-address matches dial regex",
           fa && arr_has(json_get(fa, "MatchDialAddressRegexes"), "(?i)(fastly|pypi|python|github)"));
        ok("fastly dial address pypi.org", fp && arr_has(json_get(fp, "DialAddresses"), "pypi.org"));
        ok("fastly ALPN h2", fp && arr_has(json_get(fp, "ALPNProtocols"), "h2"));
        ok("fastly verify github.com", fp && arr_has(json_get(fp, "VerifyServerNames"), "github.com"));
        ok("fastly TLS profile", fp && !strcmp(json_get_str(fp, "TLSProfile", ""), "Chrome-83"));
        ok("edge-original dial 92.123.102.43", eo && arr_has(json_get(eo, "DialAddresses"), "92.123.102.43"));
        ok("edge SNI defaults to the IP", eo && !strcmp(json_get_str(eo, "SNIServerName", ""), "92.123.102.43"));
        ok("edge verifies akamai names", eo && arr_has(json_get(eo, "VerifyServerNames"), "a248.e.akamai.net"));
        ok("edge ALPN http/1.1 only", eo && arr_len(json_get(eo, "ALPNProtocols")) == 1);
        ok("edge matches any dial address", eo && arr_has(json_get(eo, "MatchDialAddressRegexes"), ".*"));
    }
    json_free(v);
}

/* ---- direct: 14 direct protocols, tactics disabled, fronting still on */
static void test_direct(void)
{
    Settings s; JValue *v, *lp;
    settings_init(&s);
    wcscpy(s.protocol_selection, L"direct");
    v = build(&s);
    printf("direct:\n");
    ok("builds", v != NULL);
    if (!v) return;
    lp = json_get(v, "LimitTunnelProtocols");
    ok("14 direct protocols", arr_len(lp) == 14);
    ok("includes SSH", arr_has(lp, "SSH"));
    ok("includes FRONTED-MEEK-CDN-QUIC-OSSH", arr_has(lp, "FRONTED-MEEK-CDN-QUIC-OSSH"));
    ok("excludes INPROXY protocols", !arr_has(lp, "INPROXY-WEBRTC-OSSH"));
    ok("DisableTactics", json_get_bool(v, "DisableTactics", 0) == 1);
    ok("fronting config still emitted", json_get(v, "FrontedMeekDialOverrides") != NULL);
    json_free(v);
}

/* ---- cdn_fronting: 3 CDN protocols, tactics disabled */
static void test_cdn(void)
{
    Settings s; JValue *v, *lp, *spec;
    settings_init(&s);
    wcscpy(s.protocol_selection, L"cdn_fronting");
    wcscpy(s.cdn_ip_list, L"1.2.3.4, 10.0.0.0/8;bogus 300.1.1.1\n1.2.3.4 5.6.7.8");
    wcscpy(s.cdn_sni, L"Example.COM. cdn.test.org, not a host!, example.com");
    v = build(&s);
    printf("cdn_fronting:\n");
    ok("builds", v != NULL);
    if (!v) return;
    lp = json_get(v, "LimitTunnelProtocols");
    ok("3 CDN protocols", arr_len(lp) == 3);
    ok("FRONTED-MEEK-CDN-OSSH", arr_has(lp, "FRONTED-MEEK-CDN-OSSH"));
    ok("DisableTactics", json_get_bool(v, "DisableTactics", 0) == 1);
    spec = json_get(v, "FrontedMeekCDNScanSpec");
    ok("custom scan spec emitted", spec != NULL);
    ok("IPCandidates: valid, de-duplicated (3)", spec && arr_len(json_get(spec, "IPCandidates")) == 3);
    ok("IPCandidates keeps CIDR", spec && arr_has(json_get(spec, "IPCandidates"), "10.0.0.0/8"));
    ok("IPCandidates drops 300.1.1.1", spec && !arr_has(json_get(spec, "IPCandidates"), "300.1.1.1"));
    ok("IPCandidates drops 'bogus'", spec && !arr_has(json_get(spec, "IPCandidates"), "bogus"));
    ok("SNIServerNames normalised + de-duplicated (2)", spec && arr_len(json_get(spec, "SNIServerNames")) == 2);
    ok("SNI lower-cased, trailing dot removed", spec && arr_has(json_get(spec, "SNIServerNames"), "example.com"));
    {
        JValue *eo = find_override(json_get(v, "FrontedMeekDialOverrides"), "edge-a-1");
        ok("edge SNI uses first custom hostname",
           eo && !strcmp(json_get_str(eo, "SNIServerName", ""), "example.com"));
        ok("edge verify includes custom SNI then IP",
           eo && arr_has(json_get(eo, "VerifyServerNames"), "example.com")
              && arr_has(json_get(eo, "VerifyServerNames"), "23.215.0.206"));
    }
    json_free(v);
}

/* ---- conduit: in-proxy protocols, compartment handling, country rejection */
static void test_conduit(void)
{
    Settings s; JValue *v, *lp;
    settings_init(&s);
    wcscpy(s.protocol_selection, L"conduit");

    printf("conduit / auto:\n");
    core_set_conduit_fallback(0);
    v = build(&s);
    ok("builds", v != NULL);
    if (!v) return;
    lp = json_get(v, "LimitTunnelProtocols");
    ok("11 in-proxy protocols", arr_len(lp) == 11);
    ok("INPROXY-WEBRTC-SSH", arr_has(lp, "INPROXY-WEBRTC-SSH"));
    ok("INPROXY-WEBRTC-SHADOWSOCKS-OSSH", arr_has(lp, "INPROXY-WEBRTC-SHADOWSOCKS-OSSH"));
    ok("no fronting overrides in conduit mode", json_get(v, "FrontedMeekDialOverrides") == NULL);
    ok("no DisableTactics in conduit mode", json_get(v, "DisableTactics") == NULL);
    ok("compartment id from build values (auto, not fallen back)",
       !strcmp(json_get_str(v, "InproxyClientPersonalCompartmentID", ""), "test-compartment"));
    ok("no GeoIP path when no database is present", json_get(v, "GeoIPDatabasePath") == NULL);
    ok("no country rejection without GeoIP", json_get(v, "InproxyRejectProxyCountryCodes") == NULL);
    json_free(v);

    printf("conduit / auto after fallback:\n");
    core_set_conduit_fallback(1);
    v = build(&s);
    ok("builds", v != NULL);
    if (v) {
        ok("compartment id dropped after fallback",
           json_get(v, "InproxyClientPersonalCompartmentID") == NULL);
        json_free(v);
    }
    core_set_conduit_fallback(0);

    printf("conduit / shirokhorshid:\n");
    wcscpy(s.conduit_mode, L"shirokhorshid");
    core_set_conduit_fallback(1);   /* must be ignored outside auto mode */
    v = build(&s);
    ok("builds", v != NULL);
    if (v) {
        ok("always uses the private compartment",
           !strcmp(json_get_str(v, "InproxyClientPersonalCompartmentID", ""), "test-compartment"));
        json_free(v);
    }
    core_set_conduit_fallback(0);

    printf("conduit / public:\n");
    wcscpy(s.conduit_mode, L"public");
    v = build(&s);
    ok("builds", v != NULL);
    if (v) {
        ok("never uses the private compartment",
           json_get(v, "InproxyClientPersonalCompartmentID") == NULL);
        json_free(v);
    }
}

/* ---- misc options that map onto tunnel-core fields */
static void test_misc(void)
{
    Settings s; JValue *v;
    settings_init(&s);
    s.beast_mode = 0;
    s.disable_timeouts = 1;
    s.lan_proxy_sharing = 1;
    s.lan_proxy_auth = 1;
    wcscpy(s.lan_user, L"user");
    wcscpy(s.lan_pass, L"pa ss");
    s.upstream_proxy_enabled = 1;
    wcscpy(s.upstream_host, L"proxy.local");
    s.upstream_port = 3128;
    wcscpy(s.upstream_username, L"bob");
    wcscpy(s.upstream_password, L"secret");
    wcscpy(s.upstream_domain, L"CORP");
    wcscpy(s.region, L"DE");
    v = build(&s);
    printf("misc:\n");
    ok("builds", v != NULL);
    if (!v) return;
    ok("beast off -> no AggressiveEstablishment", json_get(v, "AggressiveEstablishment") == NULL);
    ok("disable timeouts -> NetworkLatencyMultiplierLambda 0.1",
       json_get_num(v, "NetworkLatencyMultiplierLambda", 0) == 0.1);
    ok("no hard timeout zeroing (Android does not do that)",
       json_get(v, "EstablishTunnelTimeoutSeconds") == NULL);
    ok("LAN sharing listens on 0.0.0.0", !strcmp(json_get_str(v, "ListenInterface", ""), "0.0.0.0"));
    ok("LAN auth user", !strcmp(json_get_str(v, "LocalProxyUsername", ""), "user"));
    ok("LAN auth password", !strcmp(json_get_str(v, "LocalProxyPassword", ""), "pa ss"));
    ok("upstream NTLM domain\\user form",
       !strcmp(json_get_str(v, "UpstreamProxyUrl", ""), "http://CORP\\bob:secret@proxy.local:3128"));
    ok("egress region", !strcmp(json_get_str(v, "EgressRegion", ""), "DE"));
    ok("EmitBytesTransferred", json_get_bool(v, "EmitBytesTransferred", 0) == 1);
    ok("EmitDiagnosticNotices", json_get_bool(v, "EmitDiagnosticNotices", 0) == 1);
    ok("PropagationChannelId from values", !strcmp(json_get_str(v, "PropagationChannelId", ""), "TESTCHAN"));
    ok("SponsorId from values", !strcmp(json_get_str(v, "SponsorId", ""), "TESTSPONSOR"));
    json_free(v);

    settings_init(&s);
    s.lan_proxy_sharing = 0;
    s.lan_proxy_auth = 1;
    wcscpy(s.lan_user, L"user");
    v = build(&s);
    ok("no LAN auth unless sharing is on", v && json_get(v, "LocalProxyUsername") == NULL);
    ok("loopback listener by default", v && !strcmp(json_get_str(v, "ListenInterface", ""), "127.0.0.1"));
    if (v) json_free(v);
}

/* ---- settings round-trip through settings_save / settings_load */
static void test_settings_roundtrip(void)
{
    Settings a, b;
    const wchar_t *path = L"/tmp/shir-test-settings.json";
    settings_init(&a);
    wcscpy(a.protocol_selection, L"conduit");
    wcscpy(a.conduit_mode, L"public");
    a.conduit_timeout = 600;
    a.beast_mode = 0;
    a.reject_censored_proxies = 0;
    a.diagnostics_upload = 0;
    wcscpy(a.cdn_ip_list, L"1.1.1.1\n2.2.2.2");
    wcscpy(a.cdn_sni, L"a.example");
    wcscpy(a.upstream_domain, L"DOM");
    printf("settings round-trip:\n");
    ok("save", settings_save(&a, path));
    settings_init(&b);
    ok("load", settings_load(&b, path));
    ok("protocol_selection", !wcscmp(b.protocol_selection, L"conduit"));
    ok("conduit_mode", !wcscmp(b.conduit_mode, L"public"));
    ok("conduit_timeout", b.conduit_timeout == 600);
    ok("beast_mode", b.beast_mode == 0);
    ok("reject_censored_proxies", b.reject_censored_proxies == 0);
    ok("diagnostics_upload", b.diagnostics_upload == 0);
    ok("cdn_ip_list", !wcscmp(b.cdn_ip_list, L"1.1.1.1\n2.2.2.2"));
    ok("cdn_sni", !wcscmp(b.cdn_sni, L"a.example"));
    ok("upstream_domain", !wcscmp(b.upstream_domain, L"DOM"));

    /* defaults when keys are missing (older settings files) */
    {
        FILE *f = fopen("/tmp/shir-test-settings.json", "wb");
        if (f) { fputs("{\"region\":\"US\"}", f); fclose(f); }
    }
    settings_init(&b);
    ok("load legacy", settings_load(&b, path));
    ok("legacy -> auto protocol", !wcscmp(b.protocol_selection, L"auto"));
    ok("legacy -> auto conduit mode", !wcscmp(b.conduit_mode, L"auto"));
    ok("legacy -> 180s timeout", b.conduit_timeout == 180);
    ok("legacy -> beast on", b.beast_mode == 1);
    ok("legacy -> reject censored on", b.reject_censored_proxies == 1);
}

int main(void)
{
    test_auto();
    test_direct();
    test_cdn();
    test_conduit();
    test_misc();
    test_settings_roundtrip();
    printf("\n%d checks, %d failures\n", checks, failures);
    return failures ? 1 : 0;
}

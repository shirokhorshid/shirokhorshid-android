/*
 * Host-side stand-ins for the Windows-only pieces that core.c depends on, so
 * the configuration builder can be exercised with a native compiler.
 */

#include "../src/shir.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include <stdarg.h>

#ifndef _WIN32

/* CRT shims --------------------------------------------------------------- */

FILE *_wfopen(const wchar_t *path, const wchar_t *mode)
{
    char p[1024], m[16];
    wcstombs(p, path, sizeof p - 1); p[sizeof p - 1] = 0;
    wcstombs(m, mode, sizeof m - 1); m[sizeof m - 1] = 0;
    return fopen(p, m);
}

int _snwprintf(wchar_t *buf, size_t n, const wchar_t *fmt, ...)
{
    va_list ap;
    int r;
    va_start(ap, fmt);
    r = vswprintf(buf, n, fmt, ap);
    va_end(ap);
    return r;
}

/* Install-dir lookups: point at a scratch directory with no GeoIP file. */
void app_install_dir(wchar_t *out, size_t n)
{
    wcsncpy(out, L"/tmp/shir-test-install", n - 1);
    out[n - 1] = 0;
}

void app_install_file(wchar_t *out, size_t n, const wchar_t *name)
{
    swprintf(out, n, L"/tmp/shir-test-install/%ls", name);
}

/* Build-time values, as tools/gen_values.py would have written them. */
int values_lookup_string(const wchar_t *key, wchar_t *out, size_t n)
{
    const wchar_t *v = NULL;
    if (!wcscmp(key, L"PropagationChannelId")) v = L"TESTCHAN";
    else if (!wcscmp(key, L"SponsorId")) v = L"TESTSPONSOR";
    else if (!wcscmp(key, L"ClientVersion")) v = L"45";
    else if (!wcscmp(key, L"ConduitCompartmentId")) v = L"test-compartment";
    else if (!wcscmp(key, L"FeedbackEncryptionPublicKey")) v = L"";
    if (!v) return 0;
    wcsncpy(out, v, n - 1);
    out[n - 1] = 0;
    return 1;
}

int values_lookup_array(const wchar_t *key, char ***out, int *count)
{
    (void)key;
    *out = NULL;
    *count = 0;
    return 0;
}

#endif /* !_WIN32 */

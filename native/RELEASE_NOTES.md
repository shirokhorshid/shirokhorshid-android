# Forough (فروغ) 2.0.1 — Android

**فروغ؛ پنجره‌ای روشن به اینترنت آزاد** · Forough — a bright window to the open internet

## What's new in 2.0.1

**فارسی:**
- رفع کرش صفحه‌ها: باز کردن برخی صفحه‌ها (مثل لاگ‌ها و صفحات دیگر) برنامه را بسته می‌کرد — ساختار چیدمان اصلاح شد و همهٔ صفحه‌ها حالا درست باز می‌شوند.
- دفترچهٔ کرش: اگر برنامه دوباره بسته شود، گزارش کامل خطا ذخیره می‌شود و با دکمهٔ «کپی گزارش عیب‌یابی» در نوار بالا قابل کپی و ارسال است.
- محافظت از دکمه‌های نوار بالا (تست سرعت، گزارش عیب‌یابی) — دیگر هیچ دکمه‌ای برنامه را نمی‌بندد.

**English:**
- Fixed screen-open crashes: the shell layout handed every screen unbounded height constraints, which crashed any screen containing a lazy list (the tunnel logs view) the moment it composed. Each screen now owns its own scroll container.
- Crash journal: any uncaught exception is now written to a local journal and included in the diagnostics report — tap the (i) button in the header and share the report to report a crash with real data.
- Hardened header actions (speed test, diagnostics) so button taps can never close the app.

---

The **native Forough Android client** — an exact port of the Forough Linux desktop client:

- Same engine: the Forough psiphon-tunnel-core fork (quality-ranked CDN edges, beast mode)
- Same features: CDN edge scanner, speed test, live traffic graph, egress region + protocol selection, CDN fronting overrides (custom IP/SNI), OSL lists, in-proxy (conduit) with personal compartment, diagnostics report
- Same theme: the libadwaita-style dark look of the desktop client, full Persian RTL with English
- Whole-device VPN via Android VpnService (tun2socks)

## نصب / Install (فارسی)

1. فایل APK را دانلود و نصب کنید (اجازهٔ «نصب از منابع ناشناس» را بدهید). اگر نسخهٔ 2.0.0 را دارید، همین نسخه مستقیم روی آن نصب می‌شود (امضای یکسان).
2. اولین بار اتصال VPN را تأیید کنید.
3. دکمهٔ اتصال را بزنید — دقیقاً مثل نسخهٔ لینوکس.

## Notes

- Signed with the Forough community distribution key (stable across releases) — install directly over 2.0.0.
- Unofficial project, not affiliated with Psiphon Inc. Built on the open-source Psiphon network.
- Sources: https://github.com/nayrasolcr-arch/forough-android/tree/master/native

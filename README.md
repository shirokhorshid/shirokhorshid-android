<div align="center">

<img src="docs/banner.png" alt="Forough — فروغ" width="720" />

# فروغ | Forough

**فارسی** · [English](#english)

«فروغ؛ پنجره‌ای روشن به اینترنت آزاد»
*Forough — a bright window to the open internet*

</div>

## فارسی

### فروغ اندروید چیست؟

**فروغ اندروید، پورتِ دقیق کلاینت لینوکس فروغ است** — همان موتور، همان امکانات، همان تم. این نسخهٔ بومی (native) با Kotlin و Jetpack Compose نوشته شده و مستقیماً از همان موتور نسخهٔ دسکتاپ استفاده می‌کند: فورک اختصاصی `psiphon-tunnel-core` با لبه‌های CDN رتبه‌بندی‌شده بر اساس سرعت واقعی تونل. کل ترافیک دستگاه از طریق `VpnService` اندروید و tun2socks از تونل عبور می‌کند.

> نمایش صادقانه: موتور اتصال فروغ همان شبکه و زیرساخت [Psiphon](https://psiphon.ca) است؛ ارجاع‌های «Psiphon» به آن زیرساخت اشاره دارند. فروغ پروژه‌ای غیررسمی است و وابسته به Psiphon Inc. نیست.

### امکانات — دقیقاً مثل نسخهٔ لینوکس

- **رابط کاربری تیرهٔ libadwaita مانند دسکتاپ** — پشتیبانی کامل فارسی راست‌به‌چپ + انگلیسی
- **دکمهٔ اتصال دایره‌ای ۹۶dp** با زنجیرهٔ وضعیت صادقانه (اتصال → متصل → اتصال مجدد) و نمایش **پروتکل واقعی برقرارشده** (از اعلان ActiveTunnel)
- **اسکنر دستی لبه‌های CDN** — همان رنج‌ها و SNIهای نسخهٔ دسکتاپ (کلادفلر، فستلی، آکامای، کلادفرانت، بانی) با نتایج TCP+TLS میلی‌ثانیه و «استفاده از انتخاب‌شده‌ها»
- **تست سرعت داخلی** — همان اندازه‌گیری دسکتاپ: TTFB (اتصال + TLS + اولین بایت) و دانلود ۱۰ مگابایتی با سقف ۱۵ ثانیه
- **نمودار زندهٔ ترافیک ۶۰ ثانیه‌ای** — دو اسپارک‌لاین دانلود/آپلود با رنگ‌های همان نمودار دسکتاپ
- **انتخاب منطقهٔ خروجی** (۲۹ کشور با پرچم) و **حالت پروتکل** (خودکار / کانالیوت / مستقیم / فرونتینگ CDN)
- **حالت بیست (Beast)** — برقراری تهاجمی روی همهٔ سرورها و همهٔ پروتکل‌ها
- **فرونتینگ CDN سفارشی** — کاندیدهای IP (IPv4/CIDR) و SNI دلخواه با همان Overrideهای نسخهٔ دسکتاپ
- **لیست سرورهای OSL** — آدرس‌های ریشهٔ لیست سرورهای مبهم‌شده
- **کانالیوت (in-proxy)** با شناسهٔ بخش شخصی برای جفت‌سازی خصوصی
- **بازجوی خودکار ترافیک** — بررسی دوره‌ای عبور واقعی ترافیک از تونل و ری‌استارت خودکار تونل مرده (همان سیاست دسکتاپ)
- **گزارش عیب‌یابی** — کپی یک‌ضربه‌ای همه‌چیز برای گزارش مشکل
- بدون تبلیغ، بدون صفحهٔ حامی مالی؛ رایگان و متن‌باز

### نصب

1. به بخش [Releases](https://github.com/nayrasolcr-arch/forough-android/releases) بروید.
2. فایل `Forough-2.0.1-android.apk` را دانلود کنید.
3. نصب را تأیید کنید («نصب از منابع ناشناس» را برای فایل‌منیجر خود فعال کنید).
4. اولین اتصال، مجوز VPN اندروید را می‌خواهد — تأیید کنید و دکمهٔ اتصال را بزنید.

نسخه‌ها با کلید توزیع اجتماعی فروغ امضا می‌شوند؛ به‌روزرسانی‌ها بدون حذف برنامه نصب می‌شوند.

### ساخت از سورس

```bash
# 1) موتور (AAR) را بسازید — GitHub Actions یا دستی:
#    gomobile bind ... خروجی: ca.psiphon.aar
# 2) AAR را در مسیر زیر قرار دهید:
#    native/app/libs/ca.psiphon.aar
# 3) کلاینت بومی را بسازید (JDK 17 + Gradle 8.9):
gradle -p native assembleRelease
# خروجی: native/app/build/outputs/apk/release/app-release.apk
```

بیلد کامل و خودکار در GitHub Actions انجام می‌شود: تب **Actions → Build & Release**.

---

## English

### What is Forough for Android?

**Forough Android is an exact port of the Forough Linux desktop client** — same engine, same features, same theme. The native client is written in Kotlin + Jetpack Compose and runs the exact engine the desktop ships: the Forough `psiphon-tunnel-core` fork with quality-ranked CDN edges (candidates ranked by measured real-tunnel establishment time). Whole-device routing goes through Android's `VpnService` + tun2socks.

> Honest attribution: Forough's connectivity engine is the [Psiphon](https://psiphon.ca) network and infrastructure; "Psiphon" references point to it. Forough is an unofficial project, not affiliated with Psiphon Inc.

### Features — same as the Linux client

- **libadwaita-style dark theme** like the desktop, full Persian RTL + English
- **96dp circular connect button** with an honest status chain and the **real established protocol** (ActiveTunnel notice)
- **Manual CDN edge scanner** — identical ranges/SNIs as the desktop (Cloudflare, Fastly, Akamai, CloudFront, Bunny) with per-edge TCP+TLS timings and "Use selected"
- **Built-in speed test** — same measurements as the desktop: TTFB (connect + TLS + first byte) and a 10 MB download with the 15s cap
- **Live 60-second traffic graph** — download/upload sparklines in the desktop's colors
- **Egress region selection** (29 countries with flags) and **protocol modes** (Auto / Conduit / Direct / CDN Fronting)
- **Beast mode** — aggressive establishment across all servers and protocols
- **Custom CDN fronting** — IPv4/CIDR IP candidates + custom SNI with the same dial overrides as the desktop
- **OSL root URLs** — obfuscated server list buckets
- **In-proxy (conduit)** with a personal compartment ID for private pairing
- **Automatic traffic probe** — periodic end-to-end checks and auto-restart for dead tunnels (the desktop policy)
- **Diagnostics report** — one-tap copy for bug reports
- No ads, no sponsor pages; free and open source

### Install

1. Grab `Forough-2.0.1-android.apk` from [Releases](https://github.com/nayrasolcr-arch/forough-android/releases).
2. Install it (allow "install unknown apps" for your file manager).
3. Approve the Android VPN prompt on first connect and hit the connect button.

Releases are signed with the Forough community distribution key, so updates install without uninstalling.

### Build from source

```bash
# 1) Build the engine AAR (CI does this): gomobile bind ... -> ca.psiphon.aar
# 2) Drop it at native/app/libs/ca.psiphon.aar
# 3) Build the native client (JDK 17 + Gradle 8.9):
gradle -p native assembleRelease
```

Or just run **Actions → Build & Release** on GitHub.

### Repository layout

| Path | What it is |
|------|------------|
| `native/` | **The Forough native client** (Kotlin + Compose) — the exact port of the Linux client |
| `app/` | The legacy Psiphon-Android–based client (kept for reference / org parity) |
| `.github/workflows/build.yml` | Builds the tunnel-core AAR, the native APK, and cuts the release |

### Sibling clients

- Linux (Fedora KDE, GTK4/libadwaita): [nayrasolcr-arch/forough-linux](https://github.com/nayrasolcr-arch/forough-linux)
- Windows (MSYS2/GTK4 portable): [nayrasolcr-arch/forough-windows](https://github.com/nayrasolcr-arch/forough-windows)

### Disclaimer

This is an unofficial community client built on the open-source Psiphon network components (GPL-3.0). It is not the official Psiphon app and is not endorsed by Psiphon Inc. Use responsibly and in accordance with your local laws.

## License

GPL-3.0 — see [LICENSE](LICENSE).

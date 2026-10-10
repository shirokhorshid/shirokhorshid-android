# Shir o Khorshid for Windows

An unofficial, independently maintained Windows build of the
[Psiphon](https://psiphon.ca) open-source client, mirroring the look and
feature set of the Android client in this repository.

This application is **NOT** developed, endorsed, sponsored, or affiliated with
Psiphon Inc. in any way. For the official Psiphon application, visit
<https://psiphon.ca>.

## What is in here

| Path | What it is |
| --- | --- |
| `src/` | The Windows client (C, Win32, custom-drawn Material-style UI) |
| `res/` | Icons, manifest and the resource script |
| `tools/` | Build helpers (icon generation, values, engine fetch, installer packing) |
| `build.sh` | One-command build of the Windows installer |
| `tests/` | Native unit tests for the portable layers |

## Architecture

The client follows the same design as Psiphon's own Windows client:
`psiphon-tunnel-core.exe` runs as a child process and the UI is driven by the
JSON notices it writes to stdout.

```
ShirOKhorshid.exe
 ├── owns the window, tray icon, settings and Windows proxy configuration
 └── spawns psiphon-tunnel-core.exe
      ├── -config psiphon.config      (built from Settings + values.json)
      ├── -serverList server_entries.txt
      └── writes JSON notices to stdout ──> logs, status, byte counters
```

Files the client uses at runtime:

* `%APPDATA%\ShirOKhorshid\settings.json` — user preferences
* `%LOCALAPPDATA%\ShirOKhorshid\tunnel-core\` — the engine's datastore
* `<install dir>\values.json` — build-time Psiphon credentials (see below)

## Building

Everything is cross-compiled with the [Zig](https://ziglang.org) toolchain,
which provides both a Windows-targeting C compiler and a resource compiler, so
**no Windows machine or MSVC install is needed**. Zig is installed
automatically (`pip install ziglang`) if it is not already on `PATH`.

```bash
cd windows
export PSIPHON_PROPAGATION_CHANNEL_ID=...   # same values the APK build uses
export PSIPHON_SPONSOR_ID=...
./build.sh
```

The result is `windows/build/ShirOKhorshid-Setup.exe`, a self-contained
installer (no elevation required — it installs per-user under
`%LOCALAPPDATA%\Programs\ShirOKhorshid`, creates Start Menu and Desktop
shortcuts and registers an entry in *Apps & features*).

Optional environment variables mirror `app/build.gradle`:
`PSIPHON_CLIENT_VERSION`, `PSIPHON_EMBEDDED_SERVER_LIST[_FILE]`,
`PSIPHON_REMOTE_SERVER_LIST_URLS_JSON`,
`PSIPHON_REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY`,
`PSIPHON_OBFUSCATED_SERVER_LIST_ROOT_URLS_JSON`,
`PSIPHON_SERVER_ENTRY_SIGNATURE_PUBLIC_KEY`,
`PSIPHON_SERVER_ENTRY_EXCHANGE_OBFUSCATION_KEY`,
`PSIPHON_FEEDBACK_ENCRYPTION_PUBLIC_KEY`, `PSIPHON_FEEDBACK_UPLOAD_URLS_JSON`,
`PSIPHON_INFO_LINK_URL`, `PSIPHON_FAQ_URL`, `PSIPHON_GET_NEW_VERSION_URL`,
`PSIPHON_GET_NEW_VERSION_EMAIL`, `PSIPHON_DATA_COLLECTION_INFO_URL`,
`PSIPHON_CONDUIT_COMPARTMENT_ID`.

To build a UI-only preview without credentials: `SHIR_ALLOW_EMPTY=1 ./build.sh`
— the app installs and runs but warns that it cannot connect.

### The tunnel engine

`tools/fetch_core.py` obtains `psiphon-tunnel-core.exe` either by building
`./ConsoleClient` from a `psiphon-tunnel-core` checkout (needs Go, pass
`--source <dir>`) or by taking the prebuilt engine that Psiphon Inc. commits to
their open-source Windows client repository. That binary is a plain
`ConsoleClient` build of the same GPLv3 upstream project and takes all of its
configuration from the client, so it is reusable.

### GitHub Actions

`.github/workflows/build-windows.yml` builds the engine from the
`shirokhorshid/psiphon-tunnel-core` fork, cross-compiles the installer and
publishes a release, using the repository's existing `PSIPHON_*` secrets. It
runs on every push touching `windows/` and can also be dispatched by hand.

## Tests

The portable layers (JSON parsing/serialisation, UTF-8 conversion) have native
unit tests:

```bash
cc -std=c11 -I src -o /tmp/test_json tests/test_json.c src/json.c && /tmp/test_json
```

## Notes and limitations

* **Whole-device tunnelling.** Android tunnels every packet through
  `VpnService`. The tunnel engine does not implement a TUN device on Windows
  (`psiphon/common/tun` reports `IsSupported() == false` there), so this client
  exposes a local SOCKS/HTTP proxy and — with *Tunnel whole device* enabled —
  configures the Windows system proxy so ordinary desktop apps go through the
  tunnel. Applications that ignore the system proxy can be pointed at the
  addresses shown on the Home tab.
* **Interface language.** Persian (RTL) and English, chosen in Settings.

## License

GNU General Public License v3.0 — see [LICENSE](../LICENSE).

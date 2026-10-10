#!/usr/bin/env python3
"""Generate values.json - the Windows build's equivalent of the Android build's
generated EmbeddedValues.java.

Values are read from environment variables (for CI) with sensible fallbacks,
exactly like app/build.gradle does for the APK.  The output is bundled next to
the executable and read at runtime by the client.

Required:
  PSIPHON_PROPAGATION_CHANNEL_ID
  PSIPHON_SPONSOR_ID

Optional:
  PSIPHON_CLIENT_VERSION, PSIPHON_EMBEDDED_SERVER_LIST,
  PSIPHON_EMBEDDED_SERVER_LIST_FILE, PSIPHON_FEEDBACK_ENCRYPTION_PUBLIC_KEY,
  PSIPHON_FEEDBACK_UPLOAD_URLS_JSON, PSIPHON_REMOTE_SERVER_LIST_URLS_JSON,
  PSIPHON_OBFUSCATED_SERVER_LIST_ROOT_URLS_JSON,
  PSIPHON_REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY,
  PSIPHON_SERVER_ENTRY_SIGNATURE_PUBLIC_KEY,
  PSIPHON_SERVER_ENTRY_EXCHANGE_OBFUSCATION_KEY,
  PSIPHON_INFO_LINK_URL, PSIPHON_FAQ_URL, PSIPHON_GET_NEW_VERSION_URL,
  PSIPHON_GET_NEW_VERSION_EMAIL, PSIPHON_DATA_COLLECTION_INFO_URL,
  PSIPHON_CONDUIT_COMPARTMENT_ID
"""
import json
import os
import sys


def env(name, default=""):
    val = os.environ.get(name, default)
    if len(val) >= 2 and val[0] == val[-1] and val[0] in ('"', "'"):
        val = val[1:-1]
    return val


def as_list(raw):
    if not raw:
        return []
    raw = raw.strip()
    if not raw:
        return []
    try:
        parsed = json.loads(raw)
        if isinstance(parsed, list):
            return [str(x) for x in parsed]
    except (ValueError, TypeError):
        pass
    return [line.strip() for line in raw.split("\n") if line.strip()]


def main():
    out_path = sys.argv[1] if len(sys.argv) > 1 else "build/values.json"

    propagation = env("PSIPHON_PROPAGATION_CHANNEL_ID")
    sponsor = env("PSIPHON_SPONSOR_ID")

    allow_empty = "--allow-empty" in sys.argv[2:]
    if not propagation or not sponsor:
        if not allow_empty:
            sys.stderr.write(
                "error: PSIPHON_PROPAGATION_CHANNEL_ID and PSIPHON_SPONSOR_ID are required\n"
                "       (set them as environment variables, like the APK build does)\n"
                "       pass --allow-empty to build an unconfigured preview\n")
            return 1
        sys.stderr.write(
            "warning: building WITHOUT Psiphon credentials - the app will install\n"
            "         and run but cannot connect until values.json is replaced\n")

    entries = []
    list_file = env("PSIPHON_EMBEDDED_SERVER_LIST_FILE")
    if list_file and os.path.exists(list_file):
        with open(list_file, "r", encoding="utf-8") as f:
            entries = [ln.strip() for ln in f if ln.strip()]
        sys.stderr.write("read %d server entries from %s\n" % (len(entries), list_file))
    else:
        entries = as_list(env("PSIPHON_EMBEDDED_SERVER_LIST"))

    values = {
        "PropagationChannelId": propagation,
        "SponsorId": sponsor,
        "ClientVersion": env("PSIPHON_CLIENT_VERSION", "45"),
        "RemoteServerListSignaturePublicKey":
            env("PSIPHON_REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY"),
        "ServerEntrySignaturePublicKey":
            env("PSIPHON_SERVER_ENTRY_SIGNATURE_PUBLIC_KEY"),
        "ServerEntryExchangeObfuscationKey":
            env("PSIPHON_SERVER_ENTRY_EXCHANGE_OBFUSCATION_KEY"),
        "FeedbackEncryptionPublicKey":
            env("PSIPHON_FEEDBACK_ENCRYPTION_PUBLIC_KEY"),
        "FeedbackUploadURLs": as_list(env("PSIPHON_FEEDBACK_UPLOAD_URLS_JSON")),
        "RemoteServerListURLs": as_list(env("PSIPHON_REMOTE_SERVER_LIST_URLS_JSON")),
        "ObfuscatedServerListRootURLs":
            as_list(env("PSIPHON_OBFUSCATED_SERVER_LIST_ROOT_URLS_JSON")),
        "UpgradeURLs": [],
        "UpgradeSignaturePublicKey": "",
        "ConduitCompartmentId": env("PSIPHON_CONDUIT_COMPARTMENT_ID"),
        "AdditionalParameters": "",
        "EmbeddedServerEntries": entries,
        "InfoLinkUrl": env("PSIPHON_INFO_LINK_URL", "https://psiphon.ca"),
        "FaqUrl": env("PSIPHON_FAQ_URL", "https://psiphon.ca/faq.html"),
        "GetNewVersionUrl": env("PSIPHON_GET_NEW_VERSION_URL", "https://psiphon.ca"),
        "GetNewVersionEmail": env("PSIPHON_GET_NEW_VERSION_EMAIL", "get@psiphon3.com"),
        "DataCollectionInfoUrl":
            env("PSIPHON_DATA_COLLECTION_INFO_URL", "https://psiphon.ca/privacy.html"),
    }

    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(values, f, ensure_ascii=False, indent=2)

    sys.stderr.write("wrote %s (channel=%s sponsor=%s entries=%d)\n"
                     % (out_path, propagation, sponsor, len(entries)))
    return 0


if __name__ == "__main__":
    sys.exit(main())

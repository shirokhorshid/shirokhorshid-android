#!/usr/bin/env python3
"""Fetch the Psiphon tunnel engine (psiphon-tunnel-core.exe) for Windows.

Two sources, in order of preference:

  1. A locally built ConsoleClient, if psiphon-tunnel-core source is available
     and a Go toolchain is installed (GOOS=windows go build ./ConsoleClient).
  2. The prebuilt engine that Psiphon Inc. commits to their open-source
     Windows client repository (src/3rdParty/psiphon-tunnel-core.exe).  That
     binary is a plain ConsoleClient build of the same GPLv3 upstream project
     and takes all of its configuration from the client, so it is reusable.

Usage:
    python3 fetch_core.py <dest_dir> [--source DIR_OR_REPO] [--repo URL]
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile

DEFAULT_REPO = "https://github.com/Psiphon-Inc/psiphon-windows.git"
DEFAULT_REF = "master"
REL_PATH = os.path.join("src", "3rdParty", "psiphon-tunnel-core.exe")


def log(msg):
    sys.stderr.write(msg + "\n")


def try_build_from_source(source_dir, dest):
    """Build ConsoleClient for windows/amd64 from a tunnel-core checkout."""
    go = shutil.which("go")
    if not go or not source_dir:
        return False
    if not os.path.isdir(os.path.join(source_dir, "ConsoleClient")):
        return False
    log("building psiphon-tunnel-core from source in %s" % source_dir)
    env = dict(os.environ)
    env.update({"GOOS": "windows", "GOARCH": "amd64", "CGO_ENABLED": "0"})
    cmd = [go, "build", "-trimpath", "-o", dest, "./ConsoleClient"]
    try:
        subprocess.run(cmd, cwd=source_dir, env=env, check=True)
    except (subprocess.CalledProcessError, OSError) as exc:
        log("source build failed: %s" % exc)
        return False
    return os.path.exists(dest) and os.path.getsize(dest) > 1_000_000


def try_download(repo, ref, dest):
    git = shutil.which("git")
    if not git:
        return False
    tmp = tempfile.mkdtemp(prefix="psiphon-windows-")
    try:
        log("cloning %s (%s) - looking for the prebuilt tunnel engine" % (repo, ref))
        subprocess.run([git, "clone", "--depth", "1", "--branch", ref,
                        "--filter=blob:none", "--sparse", repo, tmp],
                       check=True, stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL)
        subprocess.run([git, "-C", tmp, "sparse-checkout", "set",
                        os.path.dirname(REL_PATH)],
                       check=True, stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL)
        src = os.path.join(tmp, REL_PATH)
        if os.path.exists(src):
            shutil.copyfile(src, dest)
            return True
    except (subprocess.CalledProcessError, OSError) as exc:
        log("clone failed: %s" % exc)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dest_dir")
    ap.add_argument("--source", default=os.environ.get("PSIPHON_TUNNEL_CORE_SRC", ""),
                    help="path to a psiphon-tunnel-core checkout (builds from source)")
    ap.add_argument("--repo", default=os.environ.get("PSIPHON_WINDOWS_REPO", DEFAULT_REPO))
    ap.add_argument("--ref", default=os.environ.get("PSIPHON_WINDOWS_REF", DEFAULT_REF))
    args = ap.parse_args()

    dest_dir = args.dest_dir
    os.makedirs(dest_dir, exist_ok=True)
    dest = os.path.join(dest_dir, "psiphon-tunnel-core.exe")

    if os.path.exists(dest) and os.path.getsize(dest) > 1_000_000:
        log("engine already present: %s (%d bytes)" % (dest, os.path.getsize(dest)))
        return 0

    if try_build_from_source(args.source, dest):
        log("built engine from source (%d bytes)" % os.path.getsize(dest))
        return 0

    if try_download(args.repo, args.ref, dest):
        log("downloaded prebuilt engine (%d bytes)" % os.path.getsize(dest))
        return 0

    log("")
    log("could not obtain psiphon-tunnel-core.exe.")
    log("Either install Go and pass --source <psiphon-tunnel-core checkout>,")
    log("or place a prebuilt psiphon-tunnel-core.exe in %s" % dest_dir)
    return 1


if __name__ == "__main__":
    sys.exit(main())

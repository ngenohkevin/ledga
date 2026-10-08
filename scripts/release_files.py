#!/usr/bin/env python3
"""Writes a release's files (spec §13.3): ledga-<version>.apk and ledga-release.json beside it.

The release workflow runs it on the signed APK. scripts/update-test-server.sh runs it on a Ledga dev build for the S26
update check, so the phone reads exactly the format CI publishes.

usage: release_files.py --apk APK --version 2.0.0-beta.1 --version-code 2000001 --min-sdk 26 --out DIR
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import sys


def version_code(version):
    """Spec §13.1: major*1,000,000 + minor*10,000 + patch*100 + stage (N for -beta.N, 99 for stable)."""
    m = re.fullmatch(r"(\d{1,4})\.(\d{1,2})\.(\d{1,2})(?:-beta\.(\d{1,2}))?", version)
    if not m:
        sys.exit(f"version {version!r} must be X.Y.Z or X.Y.Z-beta.N")
    major, minor, patch = (int(m.group(i)) for i in (1, 2, 3))
    beta = int(m.group(4)) if m.group(4) else None
    if beta is not None and not 1 <= beta <= 98:
        sys.exit(f"version {version!r}: a beta number must be 1 to 98")
    return major * 1_000_000 + minor * 10_000 + patch * 100 + (beta if beta is not None else 99), beta is not None


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--apk", required=True)
    p.add_argument("--version", required=True)
    p.add_argument("--version-code", required=True, type=int)
    p.add_argument("--min-sdk", required=True, type=int)
    p.add_argument("--out", required=True)
    a = p.parse_args()

    code, beta = version_code(a.version)
    if code != a.version_code:
        sys.exit(f"versionCode {a.version_code} doesn't match {a.version} (expected {code})")

    os.makedirs(a.out, exist_ok=True)
    name = f"ledga-{a.version}.apk"
    target = os.path.join(a.out, name)
    shutil.copyfile(a.apk, target)
    digest = hashlib.sha256()
    with open(target, "rb") as f:
        for block in iter(lambda: f.read(1 << 16), b""):
            digest.update(block)

    manifest = {
        "version": a.version,
        "versionCode": code,
        "apk": name,
        "sha256": digest.hexdigest(),
        "minSdk": a.min_sdk,
        "channel": "beta" if beta else "stable",
    }
    with open(os.path.join(a.out, "ledga-release.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
    print(json.dumps(manifest))


if __name__ == "__main__":
    main()

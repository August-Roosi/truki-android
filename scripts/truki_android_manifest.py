# Copyright 2026 Truki
# SPDX-License-Identifier: AGPL-3.0-only
"""Update manifest (upstream ApkUpdateJob's latest.json format) and the
signing-key backup guard for scripts/truki-android-release."""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


def build_manifest(version_code: int, version_name: str, apk_path: Path, repo: str, tag: str) -> dict:
    digest = hashlib.sha256(apk_path.read_bytes()).hexdigest()
    return {
        "versionCode": version_code,
        "versionName": version_name,
        "url": f"https://github.com/{repo}/releases/download/{tag}/{apk_path.name}",
        "sha256sum": digest,
    }


_BADGING = re.compile(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'")


def parse_badging(line: str) -> tuple[str, int, str]:
    """(package, versionCode, versionName) from aapt2's first badging line."""
    m = _BADGING.match(line)
    if not m:
        raise SystemExit(f"cannot parse aapt2 badging: {line[:120]!r}")
    return m[1], int(m[2]), m[3]


def check_key_backup(key: Path, copy: Path) -> None:
    """A release signed by a key with no identical backup is a release whose
    successors may never install. Refuse instead."""
    if not key.exists():
        raise SystemExit(f"signing key missing: {key}")
    if not copy.exists():
        raise SystemExit(f"signing key backup missing: {copy} (run the hub's kalender_backup.py)")
    if copy.read_bytes() != key.read_bytes():
        raise SystemExit(f"signing key backup differs from the key: {copy}")


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)
    check = sub.add_parser("check-key")
    check.add_argument("key", type=Path)
    check.add_argument("copy", type=Path)
    badging = sub.add_parser("badging")
    badging.add_argument("line")
    manifest = sub.add_parser("manifest")
    manifest.add_argument("--version-code", type=int, required=True)
    manifest.add_argument("--version-name", required=True)
    manifest.add_argument("--apk", type=Path, required=True)
    manifest.add_argument("--repo", required=True)
    manifest.add_argument("--tag", required=True)
    manifest.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    if args.cmd == "check-key":
        check_key_backup(args.key, args.copy)
    elif args.cmd == "badging":
        print(*parse_badging(args.line))
    else:
        data = build_manifest(args.version_code, args.version_name, args.apk, args.repo, args.tag)
        args.out.write_text(json.dumps(data, indent=2) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())

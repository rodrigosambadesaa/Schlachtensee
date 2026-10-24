#!/usr/bin/env python3
"""Check/sync the canonical Kotlin connectivity gist without embedding Java twice."""
import argparse
import hashlib
import json
from pathlib import Path
from urllib.request import Request, urlopen

GIST = "729cca29a031fef4e2f15751863b655f"
SOURCE_PACKAGE = "package net.i2p.android.router.util"
APP_PACKAGE = "package net.kibotu.schlachtensee.services.network"
DEST = Path("app/src/main/java/net/kibotu/schlachtensee/services/network/ConnectivityAndInternetAccess.kt")


def main():
    cli = argparse.ArgumentParser()
    cli.add_argument("--check", action="store_true")
    args = cli.parse_args()
    request = Request(
        f"https://api.github.com/gists/{GIST}",
        headers={"Accept": "application/vnd.github+json", "User-Agent": "Schlachtensee-gist-sync"},
    )
    with urlopen(request, timeout=30) as response:
        gist = json.load(response)
    source = gist["files"]["ConnectivityAndInternetAccess.kt"]
    if source.get("truncated"):
        with urlopen(Request(source["raw_url"], headers={"User-Agent": "Schlachtensee-gist-sync"}), timeout=30) as response:
            code = response.read().decode("utf-8")
    else:
        code = source["content"]
    if code.count(SOURCE_PACKAGE) != 1:
        raise ValueError("Gist package unexpected; refusing to replace the app source")
    if not 2_000 <= len(code.splitlines()) <= 4_000:
        raise ValueError("Gist Kotlin implementation length outside expected safety range")
    if "class ConnectivityAndInternetAccess" not in code or "SPDX-License-Identifier: MIT" not in code:
        raise ValueError("Unexpected gist structure")
    code = code.replace(SOURCE_PACKAGE, APP_PACKAGE, 1)
    code = code.rstrip("\n") + "\n"
    print("Canonical gist version:", gist.get("history", [{}])[0].get("version", "unknown"))
    print("Canonical Kotlin lines:", len(code.splitlines()), "SHA256:", hashlib.sha256(code.encode()).hexdigest())
    current = DEST.read_text(encoding="utf-8") if DEST.exists() else ""
    if current == code:
        print("Already synchronized")
    elif args.check:
        raise SystemExit("Source differs from latest gist; run the Sync connectivity gist workflow.")
    else:
        DEST.parent.mkdir(parents=True, exist_ok=True)
        DEST.write_text(code, encoding="utf-8")
        print("Updated", DEST)


if __name__ == "__main__":
    main()

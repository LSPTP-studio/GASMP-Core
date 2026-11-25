#!/usr/bin/env python3
"""Expand the compact UI texture bundle into the ItemsAdder font texture folder."""
from __future__ import annotations

import base64
import io
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BUNDLE = ROOT / "ui-textures-pack.zip.b64"
TARGET = ROOT / "contents" / "gasmp" / "textures" / "font"

def main() -> None:
    if not BUNDLE.is_file():
        raise SystemExit(f"Missing bundled UI texture source: {BUNDLE}")
    TARGET.mkdir(parents=True, exist_ok=True)
    payload = base64.b64decode(BUNDLE.read_text(encoding="ascii").strip(), validate=True)
    written = []
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        for name in archive.namelist():
            if "/" in name or "\\" in name or not name.lower().endswith(".png"):
                raise SystemExit(f"Unexpected file in UI bundle: {name}")
            data = archive.read(name)
            destination = TARGET / name
            destination.write_bytes(data)
            written.append(destination.name)
    if not written:
        raise SystemExit("The UI texture bundle contained no PNG files.")
    print("Prepared ItemsAdder UI textures:")
    for name in sorted(written):
        print(f" - {name}")

if __name__ == "__main__":
    main()

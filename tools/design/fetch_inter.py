#!/usr/bin/env python3
"""Vendor Inter 4.1 (SIL OFL 1.1) as five subset static TTFs for Ledga (spec 10.2, refinement R18).

Downloads the pinned release zip, checks its SHA-256, subsets each weight to the scripts Ledga shows,
keeps every OpenType layout feature (tabular figures "tnum" above all), and writes
app/src/main/res/font/inter_*.ttf plus the licence. Output is deterministic: re-running changes nothing.
Requires: pip install fonttools
"""
import hashlib
import io
import pathlib
import sys
import urllib.request
import zipfile

from fontTools import subset
from fontTools.ttLib import TTFont

URL = "https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip"
SHA256 = "9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e"
WEIGHTS = {
    "Regular": "inter_regular",
    "Medium": "inter_medium",
    "SemiBold": "inter_semibold",
    "Bold": "inter_bold",
    "ExtraBold": "inter_extrabold",
}
# Basic Latin, Latin-1, Latin Extended-A, dashes/quotes/bullet/ellipsis/primes/guillemets, euro, trade mark,
# arrows, minus, division slash, approx/not-equal/le/ge, up/down triangles (delta badges).
UNICODES = [
    *range(0x20, 0x7F), *range(0xA0, 0x180), *range(0x2010, 0x2016), *range(0x2018, 0x201F),
    0x2022, 0x2026, 0x2032, 0x2033, 0x2039, 0x203A, 0x20AC, 0x2122, *range(0x2190, 0x2194),
    0x2212, 0x2215, 0x2248, 0x2260, 0x2264, 0x2265, 0x25B2, 0x25BC,
]
REQUIRED = [0x2212, 0x00B7, 0x2026, 0x25B2, 0x25BC, ord("K"), ord("0"), ord("+")]

ROOT = pathlib.Path(__file__).resolve().parents[2]
FONT_DIR = ROOT / "app/src/main/res/font"
LICENSE = ROOT / "app/src/main/assets/licenses/inter-OFL.txt"


def main() -> None:
    data = urllib.request.urlopen(URL).read()
    digest = hashlib.sha256(data).hexdigest()
    if digest != SHA256:
        sys.exit(f"checksum mismatch: {digest}")
    archive = zipfile.ZipFile(io.BytesIO(data))
    FONT_DIR.mkdir(parents=True, exist_ok=True)
    LICENSE.parent.mkdir(parents=True, exist_ok=True)
    for weight, name in WEIGHTS.items():
        font = TTFont(io.BytesIO(archive.read(f"extras/ttf/Inter-{weight}.ttf")), recalcTimestamp=False)
        options = subset.Options()
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        options.notdef_outline = True
        subsetter = subset.Subsetter(options)
        subsetter.populate(unicodes=UNICODES)
        subsetter.subset(font)
        cmap = font.getBestCmap()
        missing = [hex(c) for c in REQUIRED if c not in cmap]
        if missing:
            sys.exit(f"{weight}: subset lost {missing}")
        features = {r.FeatureTag for r in font["GSUB"].table.FeatureList.FeatureRecord}
        if "tnum" not in features:
            sys.exit(f"{weight}: subset lost tnum")
        out = FONT_DIR / f"{name}.ttf"
        font.save(str(out))
        print(f"{out.relative_to(ROOT)}  {out.stat().st_size} bytes")
    LICENSE.write_bytes(archive.read("LICENSE.txt"))
    print(f"{LICENSE.relative_to(ROOT)}")


if __name__ == "__main__":
    main()

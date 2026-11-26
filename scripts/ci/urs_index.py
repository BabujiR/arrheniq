#!/usr/bin/env python3
"""Regenerates docs/validation/urs-index.csv from the (local, untracked) docs/product/URS.md.

The index holds only what CI needs for the traceability gate: requirement ID, GxP risk and planned release.
Requirement text stays in URS.md; the traceability report adds it when the file is present (local runs).
Run after changing URS.md:   python3 scripts/ci/urs_index.py
"""
import csv
import re
import sys

SRC = "docs/product/URS.md"
OUT = "docs/validation/urs-index.csv"


def main() -> int:
    src = sys.argv[1] if len(sys.argv) > 1 else SRC
    out = sys.argv[2] if len(sys.argv) > 2 else OUT
    header: list[str] = []
    rows = []
    for line in open(src, encoding="utf-8"):
        if not line.startswith("|"):
            header = []
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if cells and cells[0] == "ID":
            header = [c.lower() for c in cells]
            continue
        if not re.fullmatch(r"URS-[A-Z]+-\d{3}", cells[0] if cells else ""):
            continue
        if not header:
            print(f"no table header above {cells[0]}", file=sys.stderr)
            return 1
        rec = dict(zip(header, cells))
        withdrawn = "withdrawn" in line.lower()
        rows.append({"id": cells[0], "risk": rec.get("risk", ""), "release": rec.get("rel", rec.get("release", "")),
                     "status": "withdrawn" if withdrawn else "active"})
    ids = [r["id"] for r in rows]
    dupes = sorted({i for i in ids if ids.count(i) > 1})
    if dupes:
        print("duplicate IDs: " + ", ".join(dupes), file=sys.stderr)
        return 1
    with open(out, "w", newline="", encoding="utf-8") as f:
        f.write("# Generated from docs/product/URS.md by scripts/ci/urs_index.py - do not edit by hand.\n")
        w = csv.DictWriter(f, fieldnames=["id", "risk", "release", "status"], lineterminator="\n")
        w.writeheader()
        w.writerows(rows)
    print(f"{len(rows)} requirements -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Fails if a relative Markdown link in the given directories points to a missing file.

External links (http/https/mailto) and in-page anchors are ignored. Links into git-ignored
local-only folders (docs/discovery, docs/product) are reported as warnings, not errors,
because those folders are intentionally not tracked.
Usage: check_md_links.py <dir> [<dir> ...]
"""
import pathlib
import re
import sys

LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
LOCAL_ONLY = ("docs/discovery", "docs/product")


def main(dirs: list[str]) -> int:
    root = pathlib.Path.cwd()
    errors, warnings = [], []
    for d in dirs:
        for md in sorted(pathlib.Path(d).rglob("*.md")):
            for n, line in enumerate(md.read_text(encoding="utf-8").splitlines(), 1):
                for target in LINK.findall(line):
                    if target.startswith(("http://", "https://", "mailto:", "#")):
                        continue
                    path = target.split("#", 1)[0]
                    if not path:
                        continue
                    resolved = (md.parent / path).resolve()
                    rel = resolved.relative_to(root).as_posix() if resolved.is_relative_to(root) else str(resolved)
                    if resolved.exists():
                        continue
                    msg = f"{md}:{n}: broken link -> {target}"
                    (warnings if rel.startswith(LOCAL_ONLY) else errors).append(msg)
    for w in warnings:
        print(f"WARNING {w} (local-only folder)")
    for e in errors:
        print(f"ERROR {e}")
    print(f"{len(errors)} error(s), {len(warnings)} warning(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:] or ["docs"]))

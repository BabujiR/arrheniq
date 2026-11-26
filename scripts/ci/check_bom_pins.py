#!/usr/bin/env python3
"""Checks the Spring Boot BOM security overrides in backend/pom.xml (docs/ci/README.md, "Dependency security overrides").

Dependabot cannot update these pins: they are properties consumed by the Spring Boot BOM, not dependency versions in
our POM. So, weekly in CI, this script reports for every property marked with a "SECURITY OVERRIDE" comment:
  - REMOVE  the Spring Boot version we use now manages the pinned version or newer: delete the override;
  - UPDATE  a newer patch release of the pinned artifact exists on Maven Central: check its security notes;
  - OK      the pin is still needed and current.

Exit code 1 when any pin should be removed (stale pins hide future BOM fixes), 0 otherwise.
Usage: python3 scripts/ci/check_bom_pins.py [backend/pom.xml]      (needs network access to Maven Central)
"""
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

CENTRAL = "https://repo.maven.apache.org/maven2"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

# BOM property -> (groupId, artifactId, how many leading version parts define a compatible release line)
# Tomcat 11.0.x and pgjdbc 42.7.x ship fixes as patch releases; commons-codec ships them as minor releases.
ARTIFACTS = {
    "tomcat.version": ("org.apache.tomcat.embed", "tomcat-embed-core", 2),
    "postgresql.version": ("org.postgresql", "postgresql", 2),
    "commons-codec.version": ("commons-codec", "commons-codec", 1),
}


def fetch(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=30) as r:  # noqa: S310 (fixed https URL)
        return r.read()


def version_key(v: str):
    parts = re.split(r"[.\-]", v)
    return [(0, int(p)) if p.isdigit() else (1, p) for p in parts]


def same_line(a: str, b: str, depth: int) -> bool:
    """True when both versions share the first `depth` parts (e.g. 11.0.x for depth 2)."""
    return a.split(".")[:depth] == b.split(".")[:depth]


def pins(pom_text: str) -> dict:
    """Properties directly preceded by a <!-- SECURITY OVERRIDE ... --> comment."""
    found = {}
    for m in re.finditer(r"<!--\s*SECURITY OVERRIDE.*?-->\s*<([\w.\-]+)>([^<]+)</\1>", pom_text, re.S):
        found[m.group(1)] = m.group(2).strip()
    return found


def main() -> int:
    pom_path = sys.argv[1] if len(sys.argv) > 1 else "backend/pom.xml"
    text = open(pom_path, encoding="utf-8").read()
    root = ET.fromstring(text)
    parent = root.find("m:parent", NS)
    if parent is None or parent.findtext("m:artifactId", namespaces=NS) != "spring-boot-starter-parent":
        print("::error::backend/pom.xml has no spring-boot-starter-parent")
        return 2
    boot = parent.findtext("m:version", namespaces=NS)
    bom = ET.fromstring(fetch(f"{CENTRAL}/org/springframework/boot/spring-boot-dependencies/{boot}/"
                              f"spring-boot-dependencies-{boot}.pom"))
    managed = {el.tag.split("}")[1]: (el.text or "").strip() for el in bom.find("m:properties", NS)}

    overrides = pins(text)
    if not overrides:
        print("No SECURITY OVERRIDE pins in", pom_path)
        return 0

    rows, remove = [], False
    for prop, pinned in sorted(overrides.items()):
        if prop not in ARTIFACTS:
            print(f"::error::{prop} is pinned but unknown to {__file__}; add it to ARTIFACTS")
            return 2
        group, artifact, depth = ARTIFACTS[prop]
        bom_version = managed.get(prop, "?")
        meta = ET.fromstring(fetch(f"{CENTRAL}/{group.replace('.', '/')}/{artifact}/maven-metadata.xml"))
        releases = [v.text for v in meta.iter("version")
                    if v.text and not re.search(r"(?i)alpha|beta|rc|m\d|snapshot", v.text)]
        newer = sorted((v for v in releases if same_line(v, pinned, depth) and version_key(v) > version_key(pinned)),
                       key=version_key)
        if bom_version != "?" and version_key(bom_version) >= version_key(pinned):
            status, remove = "REMOVE (BOM now manages it)", True
            print(f"::warning::{prop}: Spring Boot {boot} manages {bom_version} >= pin {pinned}; delete the override")
        elif newer:
            status = f"UPDATE available: {newer[-1]}"
            print(f"::notice::{prop}: {newer[-1]} released (pinned {pinned}); check its security notes")
        else:
            status = "OK (still needed)"
        rows.append((prop, pinned, bom_version, status))

    print(f"\nSpring Boot {boot} BOM security overrides\n")
    print("| Property | Pinned | Boot BOM | Status |\n|---|---|---|---|")
    for r in rows:
        print("| " + " | ".join(r) + " |")
    return 1 if remove else 0


if __name__ == "__main__":
    sys.exit(main())

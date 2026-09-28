#!/usr/bin/env python3
"""Generate a CycloneDX 1.5 SBOM for the Android release, fully offline.

Inputs are the committed lock files, so the SBOM describes exactly what the locked build ships:
  * app/gradle.lockfile       - Maven dependencies on the release runtime classpath
  * core-lock.properties      - CMFA / Mihomo native core and Go toolchain
  * geodata-lock.properties   - bundled GeoIP / GeoSite datasets

Usage: tools/generate-sbom.py [--version VERSION] [--output FILE]
"""
import argparse
import datetime
import json
import pathlib
import re
import uuid

ROOT = pathlib.Path(__file__).resolve().parent.parent
RUNTIME_CONFIGURATION = "releaseRuntimeClasspath"


def read_properties(path: pathlib.Path) -> dict:
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def maven_components() -> list:
    components = []
    for line in (ROOT / "app/gradle.lockfile").read_text(encoding="utf-8").splitlines():
        if line.startswith("#") or "=" not in line or line.startswith("empty="):
            continue
        coordinate, configurations = line.split("=", 1)
        if RUNTIME_CONFIGURATION not in configurations.split(","):
            continue
        group, name, version = coordinate.split(":")
        components.append({
            "type": "library",
            "bom-ref": f"pkg:maven/{group}/{name}@{version}",
            "group": group,
            "name": name,
            "version": version,
            "purl": f"pkg:maven/{group}/{name}@{version}",
            "scope": "required",
        })
    return sorted(components, key=lambda c: c["purl"])


def native_components() -> list:
    core = read_properties(ROOT / "core-lock.properties")
    geo = read_properties(ROOT / "geodata-lock.properties")
    components = [
        {
            "type": "library",
            "bom-ref": "pkg:github/MetaCubeX/mihomo@" + core["mihomo.commit"],
            "name": "mihomo",
            "version": core["mihomo.commit"],
            "purl": "pkg:github/MetaCubeX/mihomo@" + core["mihomo.commit"],
            "licenses": [{"license": {"id": "GPL-3.0-only"}}],
            "hashes": [{"alg": "SHA-256", "content": core["mihomo.commit.archive.sha256"]}],
            "externalReferences": [{"type": "vcs", "url": core["mihomo.repository"]}],
        },
        {
            "type": "library",
            "bom-ref": "pkg:github/MetaCubeX/ClashMetaForAndroid@" + core["cmfa.commit"],
            "name": "ClashMetaForAndroid-core",
            "version": core["cmfa.version"],
            "purl": "pkg:github/MetaCubeX/ClashMetaForAndroid@" + core["cmfa.commit"],
            "licenses": [{"license": {"id": "GPL-3.0-only"}}],
            "externalReferences": [{"type": "vcs", "url": core["cmfa.repository"]}],
        },
    ]
    for key, value in sorted(core.items()):
        match = re.fullmatch(r"libclash\.(.+)\.sha256", key)
        if match:
            components.append({
                "type": "file",
                "bom-ref": f"file:libclash.so/{match.group(1)}",
                "name": f"lib/{match.group(1)}/libclash.so",
                "version": core["mihomo.commit"],
                "hashes": [{"alg": "SHA-256", "content": value}],
            })
    for dataset in ("geoip", "geosite"):
        components.append({
            "type": "data",
            "bom-ref": f"data:{dataset}@{geo['release.commit']}",
            "name": geo[f"{dataset}.asset.name"],
            "version": geo["release.commit"],
            "hashes": [{"alg": "SHA-256", "content": geo[f"{dataset}.sha256"]}],
            "externalReferences": [{"type": "distribution", "url": geo["repository"]}],
        })
    return components


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", default="unreleased")
    parser.add_argument("--output", default="-")
    args = parser.parse_args()
    bom = {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "serialNumber": f"urn:uuid:{uuid.uuid4()}",
        "version": 1,
        "metadata": {
            "timestamp": datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat(),
            "component": {
                "type": "application",
                "bom-ref": "io.weave.client",
                "name": "Weave for Android",
                "version": args.version,
                "licenses": [{"license": {"id": "GPL-3.0-or-later"}}],
            },
        },
        "components": maven_components() + native_components(),
    }
    text = json.dumps(bom, indent=2, ensure_ascii=False) + "\n"
    if args.output == "-":
        print(text, end="")
    else:
        pathlib.Path(args.output).write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()

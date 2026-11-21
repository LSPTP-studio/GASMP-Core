#!/usr/bin/env python3
"""Static release checks for GASMP Core. Does not replace a Paper integration test."""
from __future__ import annotations

import json
import re
import struct
import sys
import zlib
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]


class UniqueLoader(yaml.SafeLoader):
    pass


def construct_unique_mapping(loader: UniqueLoader, node: yaml.MappingNode, deep: bool = False):
    mapping = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in mapping:
            raise ValueError(f"duplicate YAML key: {key!r}")
        mapping[key] = loader.construct_object(value_node, deep=deep)
    return mapping


UniqueLoader.add_constructor(
    yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, construct_unique_mapping
)


def fail(message: str) -> None:
    raise RuntimeError(message)


def parse_yaml_files() -> int:
    paths = sorted([*ROOT.rglob("*.yml"), *ROOT.rglob("*.yaml")])
    for path in paths:
        try:
            yaml.load(path.read_text(encoding="utf-8"), Loader=UniqueLoader)
        except Exception as exc:  # includes duplicate-key errors
            fail(f"YAML error in {path.relative_to(ROOT)}: {exc}")
    print(f"PASS: {len(paths)} YAML files parse without duplicate keys")
    return len(paths)


def check_command_bindings() -> None:
    descriptor = yaml.load(
        (ROOT / "src/main/resources/plugin.yml").read_text(encoding="utf-8"),
        Loader=UniqueLoader,
    )
    declared = set(descriptor.get("commands", {}))
    core = (ROOT / "src/main/java/net/gasmp/core/GASMPCore.java").read_text(
        encoding="utf-8"
    )
    bound = set(re.findall(r'bind\("([a-z0-9]+)"', core))
    if declared != bound:
        fail(
            "Command descriptor/binding mismatch: "
            f"declared_without_binding={sorted(declared-bound)}, "
            f"bound_without_descriptor={sorted(bound-declared)}"
        )
    print(f"PASS: all {len(declared)} commands have exactly a descriptor and binding")


def check_version_consistency() -> None:
    gradle = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
    plugin = yaml.load(
        (ROOT / "src/main/resources/plugin.yml").read_text(encoding="utf-8"),
        Loader=UniqueLoader,
    )
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    version = re.search(r'version\s*=\s*"([^"]+)"', gradle)
    if not version:
        fail("Gradle version declaration is missing")
    if plugin.get("version") != version.group(1):
        fail("plugin.yml version does not match Gradle version")
    if f"GASMP Core {version.group(1)}" not in readme:
        fail("README heading does not match Gradle version")
    print(f"PASS: release version {version.group(1)} is consistent")


def check_default_resources() -> None:
    core = (ROOT / "src/main/java/net/gasmp/core/GASMPCore.java").read_text(
        encoding="utf-8"
    )
    resources = re.findall(r'"([a-z0-9-]+\.yml)"', core.split("saveIfMissing", 1)[0])
    # Resource names in the startup array must exist. config.yml is saved with saveDefaultConfig().
    for name in resources:
        if not (ROOT / "src/main/resources" / name).exists():
            fail(f"Missing default resource: {name}")
    if not (ROOT / "src/main/resources/config.yml").exists():
        fail("Missing default config.yml")
    print(f"PASS: startup default config resources exist ({len(set(resources)) + 1})")


def walk_outliner(nodes, refs):
    for node in nodes:
        # Blockbench stores group nodes as objects and their element children as UUID strings.
        if isinstance(node, str):
            refs.append(node)
        elif isinstance(node, dict):
            refs.append(node.get("uuid"))
            walk_outliner(node.get("children", []), refs)
        else:
            fail("Blockbench outliner entry is neither a group object nor an element UUID")


def check_blockbench_models() -> None:
    paths = sorted(
        (ROOT / "integrations/rp-engine/content/gasmp_crates/assets/models").glob(
            "*.bbmodel"
        )
    )
    if not paths:
        fail("No crate Blockbench models found")
    for path in paths:
        model = json.loads(path.read_text(encoding="utf-8"))
        groups = {group.get("uuid") for group in model.get("groups", [])}
        elements = {element.get("uuid") for element in model.get("elements", [])}
        refs = []
        walk_outliner(model.get("outliner", []), refs)
        valid_refs = groups | elements
        if not refs or any(ref not in valid_refs for ref in refs):
            fail(f"Invalid outliner/group/element references in {path.name}")
        if not groups.issubset(set(refs)) or not elements.issubset(set(refs)):
            fail(f"Not all groups/elements are present in the outliner for {path.name}")
        if not model.get("animations"):
            fail(f"Missing animation in {path.name}")
        if not any(animation.get("name") == "open" for animation in model["animations"]):
            fail(f"Missing open animation in {path.name}")
        if len(model.get("elements", [])) < 2:
            fail(f"Model geometry unexpectedly small in {path.name}")
    print(f"PASS: {len(paths)} Blockbench 5 models have valid groups and open animations")


def check_png_files() -> None:
    paths = sorted(
        (ROOT / "integrations/rp-engine/content/gasmp_crates/assets/textures").rglob(
            "*.png"
        )
    )
    if not paths:
        fail("No crate PNG textures found")
    for path in paths:
        raw = path.read_bytes()
        if len(raw) < 33 or raw[:8] != b"\x89PNG\r\n\x1a\n":
            fail(f"Invalid PNG signature: {path.name}")
        width, height = struct.unpack(">II", raw[16:24])
        if width < 1 or height < 1:
            fail(f"Invalid PNG dimensions: {path.name}")
        # Parse chunks and verify CRCs; validates the complete PNG stream structure.
        offset = 8
        seen_iend = False
        while offset < len(raw):
            if offset + 12 > len(raw):
                fail(f"Truncated PNG chunk in {path.name}")
            length = struct.unpack(">I", raw[offset : offset + 4])[0]
            kind = raw[offset + 4 : offset + 8]
            end = offset + 8 + length
            if end + 4 > len(raw):
                fail(f"Truncated PNG data in {path.name}")
            data = raw[offset + 4 : end]
            expected = struct.unpack(">I", raw[end : end + 4])[0]
            if zlib.crc32(data) & 0xFFFFFFFF != expected:
                fail(f"PNG CRC mismatch in {path.name}")
            offset = end + 4
            if kind == b"IEND":
                seen_iend = True
                break
        if not seen_iend:
            fail(f"PNG IEND missing: {path.name}")
    print(f"PASS: {len(paths)} custom crate PNG textures pass structural/CRC checks")


def check_safe_release_defaults() -> None:
    config = yaml.load(
        (ROOT / "src/main/resources/config.yml").read_text(encoding="utf-8"),
        Loader=UniqueLoader,
    )
    casino = yaml.load(
        (ROOT / "src/main/resources/casino.yml").read_text(encoding="utf-8"),
        Loader=UniqueLoader,
    )
    if config.get("modules", {}).get("casino") is not False:
        fail("Casino module must remain disabled in launch candidate defaults")
    casino_settings = casino.get("settings", casino)
    if casino_settings.get("enabled") is not False:
        fail("casino.yml must keep wagering disabled by default")
    print("PASS: casino remains disabled by default")


def main() -> int:
    checks = [
        parse_yaml_files,
        check_command_bindings,
        check_version_consistency,
        check_default_resources,
        check_blockbench_models,
        check_png_files,
        check_safe_release_defaults,
    ]
    try:
        for check in checks:
            check()
    except Exception as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    print("All GASMP static release checks passed. Live Paper/Bedrock tests are still required.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

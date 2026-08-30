#!/usr/bin/env python3
"""Assert that every shipped asset has a declared licence category.

`LICENSE.md` reserves rights over an enumerated list rather than over whole directories,
because a directory-wide claim swept in 2,285 Apache-2.0 community translations and every
functional string resource. An enumerated list only stays correct if something notices when
a new asset is added and not classified. That is this script.

Run from the stardroid-v2 module root:

    python3 tools/check_asset_licenses.py

Exit status 0 when every asset matches a rule in ASSET-LICENSES.txt, 1 otherwise.
"""

from __future__ import annotations

import fnmatch
import sys
from pathlib import Path

MODULE_ROOT = Path(__file__).resolve().parent.parent
MANIFEST = MODULE_ROOT / "ASSET-LICENSES.txt"
ASSET_ROOT = MODULE_ROOT / "app" / "src" / "main"

# Directories holding licensable artwork. Selected by directory rather than by file
# extension because the launcher icon and every UI glyph are `.xml` vector drawables — an
# extension filter would silently skip the single most important reserved asset.
#
# res/values*/, res/layout/ and res/xml/ are deliberately absent: strings, themes and
# configs are source, covered by the GPLv3 grant over the module. Sweeping them in is the
# defect this manifest replaces.
SCANNED_PREFIXES = (
    "res/drawable",
    "res/mipmap",
    "res/raw",
    "assets/",
)

# Never licensable content in their own right.
IGNORED_NAMES = {".DS_Store", "Thumbs.db"}

VALID_SECTIONS = {"arr", "gpl", "apache-v1", "third-party"}


def load_rules(path: Path) -> list[tuple[str, str]]:
    """Parse the manifest into ordered (section, glob) pairs. First match wins."""
    if not path.exists():
        sys.exit(f"error: manifest not found: {path}")

    rules: list[tuple[str, str]] = []
    section: str | None = None

    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1].strip().lower()
            if section not in VALID_SECTIONS:
                sys.exit(
                    f"{path.name}:{lineno}: unknown section [{section}]; "
                    f"expected one of {sorted(VALID_SECTIONS)}"
                )
            continue
        if section is None:
            sys.exit(f"{path.name}:{lineno}: pattern before any [section] heading")
        rules.append((section, line))

    if not rules:
        sys.exit(f"error: {path.name} declares no patterns")
    return rules


def discover_assets(root: Path) -> list[str]:
    """Every licensable asset under app/src/main, as posix paths relative to it."""
    found: list[str] = []
    for p in root.rglob("*"):
        if not p.is_file() or p.name in IGNORED_NAMES:
            continue
        rel = p.relative_to(root).as_posix()
        if rel.startswith(SCANNED_PREFIXES):
            found.append(rel)
    return sorted(found)


def classify(asset: str, rules: list[tuple[str, str]]) -> tuple[str, int] | None:
    """First matching rule wins. Returns (section, rule index), or None if unclassified."""
    for i, (section, pattern) in enumerate(rules):
        # fnmatch does not treat '/' specially, so '**' and '*' both span separators.
        # That is what we want for 'assets/celestial_images/**'; for the narrower
        # 'res/drawable/ic_*.xml' the literal prefix keeps it anchored to that directory.
        if fnmatch.fnmatch(asset, pattern):
            return section, i
    return None


def find_dead_rules(
    rules: list[tuple[str, str]],
    assets: list[str],
    winners: set[int],
) -> tuple[list[tuple[str, str]], list[tuple[str, str]]]:
    """Rules that classify nothing, split into two kinds.

    A rule stops doing work either because the files it named are gone, or because an
    earlier rule already claims everything it would match. Both leave a claim in the
    manifest that no longer corresponds to anything shipped, which is how the previous
    directory-wide notice drifted out of true in the first place.
    """
    gone: list[tuple[str, str]] = []
    shadowed: list[tuple[str, str]] = []
    for i, (section, pattern) in enumerate(rules):
        if i in winners:
            continue
        if any(fnmatch.fnmatch(a, pattern) for a in assets):
            shadowed.append((section, pattern))
        else:
            gone.append((section, pattern))
    return gone, shadowed


def main() -> int:
    rules = load_rules(MANIFEST)
    assets = discover_assets(ASSET_ROOT)

    if not assets:
        print(f"error: no assets discovered under {ASSET_ROOT}", file=sys.stderr)
        return 1

    counts: dict[str, int] = {s: 0 for s in VALID_SECTIONS}
    unclassified: list[str] = []
    winners: set[int] = set()

    for asset in assets:
        hit = classify(asset, rules)
        if hit is None:
            unclassified.append(asset)
        else:
            section, rule_index = hit
            counts[section] += 1
            winners.add(rule_index)

    if unclassified:
        print(
            f"\nFAIL: {len(unclassified)} asset(s) are not declared in "
            f"{MANIFEST.name}:\n",
            file=sys.stderr,
        )
        for asset in unclassified:
            print(f"    {asset}", file=sys.stderr)
        print(
            "\nEvery shipped asset must declare its licence. Add each path to a section\n"
            f"of {MANIFEST.name}:\n"
            "\n"
            "    [arr]           you drew it AND it is brand identity\n"
            "    [gpl]           you drew it, functional artwork, ships under GPLv3\n"
            "    [apache-v1]     inherited from Sky Map v1, under Apache-2.0\n"
            "    [third-party]   NASA/ESA/ESO/etc — under its own terms, not ours\n"
            "\n"
            "If it is not unambiguously your own brand artwork, it does not go in [arr].\n"
            "Claiming rights over other people's work weakens every other claim beside it.\n",
            file=sys.stderr,
        )
        return 1

    gone, shadowed = find_dead_rules(rules, assets, winners)
    if gone or shadowed:
        print(f"\nFAIL: {MANIFEST.name} declares rules that classify nothing.\n", file=sys.stderr)
        if gone:
            print("  Matches no shipped file — the asset was removed or renamed:", file=sys.stderr)
            for section, pattern in gone:
                print(f"    [{section}]  {pattern}", file=sys.stderr)
        if shadowed:
            print(f"{chr(10) if gone else ''}  Shadowed — an earlier rule already claims "
                  "everything it would match:", file=sys.stderr)
            for section, pattern in shadowed:
                print(f"    [{section}]  {pattern}", file=sys.stderr)
        print(
            "\nDelete these lines. A licence claim over files that are not shipped is the\n"
            "drift this manifest exists to prevent: it is how the previous notice ended up\n"
            "asserting rights over 2,285 translations nobody had checked in years.\n"
            "\n"
            "If you are removing an asset, remove its rule in the same commit — then the\n"
            "file and its licence can never disagree.\n",
            file=sys.stderr,
        )
        return 1

    total = len(assets)
    print(f"OK: {total} assets classified")
    print(
        f"    arr {counts['arr']}  |  gpl {counts['gpl']}  |  "
        f"apache-v1 {counts['apache-v1']}  |  third-party {counts['third-party']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

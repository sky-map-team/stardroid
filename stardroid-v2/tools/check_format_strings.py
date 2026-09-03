#!/usr/bin/env python3
"""Reject malformed or mismatched printf-style format specifiers in translated strings.

Android Lint's format-string checks and `tm validate` both recognise a specifier with a
regex whose conversion character is `[a-zA-Z]` — ASCII letters only. A translation that
mangles the conversion character into anything else (a Greek letter, an accented letter, a
stray symbol) doesn't get flagged as *invalid*; the regex just fails to match at that
position, so the tool sees plain text and reports nothing. `java.util.Formatter` at
runtime is not permissive the same way: it parses character-by-character and throws
`UnknownFormatConversionException` the instant it hits a conversion character it doesn't
recognise, wherever that happens to be. A string can pass every existing machine review and
still crash the app on first render.

This script parses each format specifier with the same grammar `java.util.Formatter` uses,
so a malformed specifier fails here the way it would fail at runtime. It also checks that a
translated string uses the same *multiset* of argument categories (int/float/string/...) as
its English source — order may differ (translations reorder `%1$s`/`%2$s` freely), but a
missing, extra, or wrong-typed argument still crashes `String.format`.

Run from the stardroid-v2 module root:

    python3 tools/check_format_strings.py

Exit status 0 when every translated format string is well-formed and type-consistent with
its English source, 1 otherwise.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path
from xml.etree import ElementTree

MODULE_ROOT = Path(__file__).resolve().parent.parent
RES_DIR = MODULE_ROOT / "app" / "src" / "main" / "res"
BASE_DIR = RES_DIR / "values"

# Mirrors the `[[sources]] type = "android"` entry's `files` list in .tmconfig.toml — the
# set of resource files `tm` treats as translatable. Keep the two in sync.
TRANSLATABLE_FILES = ["strings.xml", "credits.xml", "help.xml", "whatsnew.xml", "eula.xml"]

# Locale resource directories: two/three-letter language codes with an optional -rXX
# region (values-pt, values-zh-rTW) or the BCP-47 script form (values-b+zh+Hant). This
# excludes non-locale qualifiers (values-night, values-w820dp, ...) should any appear.
LOCALE_DIR_RE = re.compile(r"^values-((?:[a-z]{2,3})(?:-r[A-Z]{2})?|b\+[A-Za-z0-9+]+)$")

FLAGS = set("-#+ 0,(")
# java.util.Formatter's %t/%T date-time conversion suffixes.
TIME_SUFFIXES = set("HIklMSLNpzZsQBbhAaCYyjJmdeRTrDFc")

# Conversion character -> argument category. 'n' and '%' consume no argument.
CONVERSION_CATEGORY = {
    "d": "int", "o": "int", "x": "int", "X": "int",
    "e": "float", "E": "float", "f": "float", "g": "float", "G": "float", "a": "float", "A": "float",
    "s": "string", "S": "string",
    "c": "char", "C": "char",
    "b": "bool", "B": "bool",
    "h": "hash", "H": "hash",
    "n": None,
    "%": None,
}

# A conservative "is this string actually meant to be formatted" detector, used to decide
# which strings get validated at all. Deliberately narrower than the full grammar above
# (e.g. it excludes the space flag): prose can and does contain a bare '%' immediately
# followed by an ordinary word ("83% illuminated" — never passed through String.format, so
# a stray '%' there is harmless), and that must not be mistaken for a translator having
# mangled a real specifier. Real specifiers in this codebase are always written with an
# explicit conversion character from CONVERSION_CATEGORY, so requiring one here (rather
# than any letter) is what keeps prose out.
_REAL_CONVERSIONS = "".join(c for c in CONVERSION_CATEGORY if c not in ("n", "%")) + "n%"
IS_FORMATTED_RE = re.compile(
    r"%(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?[" + re.escape(_REAL_CONVERSIONS) + r"]"
)


def is_formatted(text: str) -> bool:
    """Whether `text` is meant to be used with String.format-style arguments at all.

    Only strings that pass this are validated (base and every locale) — see the note
    above IS_FORMATTED_RE for why the rest are deliberately left alone.
    """
    return bool(IS_FORMATTED_RE.search(text))


def scan_format_string(text: str) -> tuple[list[str], list[str]]:
    """Parse every `%` specifier in `text` with java.util.Formatter's own grammar.

    Returns (categories, errors). `categories` is the multiset of argument categories
    consumed, in the order encountered (order is not meaningful for comparison — callers
    should sort before comparing, since translations may legally reorder arguments).
    """
    categories: list[str] = []
    errors: list[str] = []
    i, n = 0, len(text)
    while i < n:
        if text[i] != "%":
            i += 1
            continue
        start = i
        j = i + 1

        m = re.match(r"\d+\$", text[j:])
        if m:
            j += m.end()

        while j < n and text[j] in FLAGS:
            j += 1

        m = re.match(r"\d+", text[j:])
        if m:
            j += m.end()

        if j < n and text[j] == ".":
            m = re.match(r"\.\d+", text[j:])
            if m:
                j += m.end()
            else:
                errors.append(f"malformed precision at offset {start}: {text[start:start + 12]!r}...")
                i = j + 1
                continue

        if j >= n:
            errors.append(f"truncated format specifier at end of string: {text[start:]!r}")
            break

        conv = text[j]
        if conv in ("t", "T"):
            j += 1
            if j >= n or text[j] not in TIME_SUFFIXES:
                near = text[start:j + 1]
                errors.append(f"invalid or truncated date-time conversion at offset {start}: {near!r}")
                i = j + 1
                continue
            j += 1
            categories.append("datetime")
        elif conv in CONVERSION_CATEGORY:
            j += 1
            category = CONVERSION_CATEGORY[conv]
            if category is not None:
                categories.append(category)
        else:
            near = text[max(0, start - 5):j + 1]
            errors.append(f"invalid conversion character {conv!r} at offset {start} (near {near!r})")
            j += 1
        i = j
    return categories, errors


def parse_strings(xml_path: Path) -> dict[str, str]:
    """name -> text for every translatable <string> in an Android resource file."""
    if not xml_path.exists():
        return {}
    tree = ElementTree.parse(xml_path)
    result: dict[str, str] = {}
    for elem in tree.getroot().findall("string"):
        name = elem.get("name")
        if name is None or elem.get("translatable") == "false":
            continue
        result[name] = elem.text or ""
    return result


def load_locale(res_dir: Path) -> dict[str, str]:
    strings: dict[str, str] = {}
    for filename in TRANSLATABLE_FILES:
        strings.update(parse_strings(res_dir / filename))
    return strings


def main() -> int:
    if not BASE_DIR.exists():
        print(f"error: base resource directory not found: {BASE_DIR}", file=sys.stderr)
        return 1

    base_strings = load_locale(BASE_DIR)
    if not base_strings:
        print(f"error: no translatable strings found under {BASE_DIR}", file=sys.stderr)
        return 1

    # Only validate strings the English source actually uses as format strings — see
    # is_formatted's docstring. Everything else (including any stray '%' it contains) is
    # left alone, exactly as tm/lint leave it alone today.
    formatted_names = {name for name, text in base_strings.items() if is_formatted(text)}
    base_scans = {name: scan_format_string(base_strings[name]) for name in formatted_names}
    for name, (_, errors) in base_scans.items():
        for error in errors:
            print(f"FAIL values/ [{name}]: {error}", file=sys.stderr)

    locale_dirs = sorted(
        p for p in RES_DIR.iterdir() if p.is_dir() and LOCALE_DIR_RE.match(p.name)
    )

    failures = sum(len(errors) for _, errors in base_scans.values())
    checked_strings = 0

    for locale_dir in locale_dirs:
        locale_strings = load_locale(locale_dir)
        for name, text in locale_strings.items():
            if name not in formatted_names:
                continue
            checked_strings += 1
            categories, errors = scan_format_string(text)
            for error in errors:
                print(f"FAIL {locale_dir.name} [{name}]: {error}", file=sys.stderr)
                failures += 1

            if errors:
                continue
            base_categories, base_errors = base_scans[name]
            if base_errors:
                continue
            if sorted(categories) != sorted(base_categories):
                print(
                    f"FAIL {locale_dir.name} [{name}]: argument mismatch — "
                    f"source has {sorted(base_categories)}, translation has {sorted(categories)}",
                    file=sys.stderr,
                )
                failures += 1

    if failures:
        print(
            f"\n{failures} format-string issue(s) found across {len(locale_dirs)} locales.\n"
            "Each would throw at String.format/stringResource time on a device using that "
            "locale — see java.util.Formatter's grammar for valid conversion characters.",
            file=sys.stderr,
        )
        return 1

    print(f"OK: {checked_strings} translated strings across {len(locale_dirs)} locales, 0 issues")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

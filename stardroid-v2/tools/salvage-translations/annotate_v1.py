#!/usr/bin/env python3
"""Annotate v1 string resources the salvage tool refused to carry into v2.

The salvage run (salvage.py) rejects a v1 translation when it cannot be trusted: the
inline markup does not match the source, a run of untranslated English survives inside
it, or it carries the "translated prose glued to the original" merge artifact. Those are
pre-existing defects in v1's own resources — v2 simply surfaced them.

This script writes an XML comment above each offending <string> in stardroid-v1 so the
damage is visible where it lives, for a human to fix or re-run through `tm`. It is
idempotent: re-running replaces the existing marker rather than stacking duplicates.

The defect list comes from salvage.py's own verdicts, not a hardcoded table, so the two
cannot drift apart.

v1 lives in its own repository (sky-map-team/stardroid); the copy beside stardroid-v2 in this
monorepo is a reference snapshot. Point --v1-res at the checkout you actually commit to, and
note the two can differ — the defects are re-derived against whichever tree is given rather
than copied from a previous run.

Run from the repository root:
  python3 stardroid-v2/tools/salvage-translations/annotate_v1.py \
      --v1-res ../stardroid/stardroid-v1/app/src/main/res
  python3 stardroid-v2/tools/salvage-translations/annotate_v1.py \
      --v1-res ../stardroid/stardroid-v1/app/src/main/res --apply
"""

import argparse
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import salvage as S  # noqa: E402

MARKER = "SALVAGE-FLAGGED"

REASON_TEXT = {
    "markup": (
        "inline markup does not match the English source - a dropped or partial <b> means "
        "the string was translated in halves"
    ),
    "leftover": (
        "a run of untranslated English survives inside the translation - the translated "
        "prose was appended to the original instead of replacing it"
    ),
    "splice": (
        "merge artifact: translated text glued to the English original with a stray '=' "
        "separator"
    ),
    "tail": (
        "the translation is complete but is followed by a repeated fragment of the English "
        "original (e.g. a trailing \"<b>Manual Mode</b>!\") - delete the English tail"
    ),
}


def find_defects(res_v1: str, res_v2: str):
    """Re-derive salvage.py's rejections. Returns {locale_dir: {key: reason}}."""
    v1_en = S.load_dir(os.path.join(res_v1, "values"), S.V1_SOURCE_FILES)
    v2_en = S.load_dir(os.path.join(res_v2, "values"), S.V2_SOURCE_FILES)
    text_index = S.build_index(v1_en)
    matches, _fuzzy, _case_only, _unmatched = S.classify(v2_en, v1_en, text_index, 0.70)

    defects: dict[str, dict[str, str]] = {}
    for entry in sorted(os.listdir(res_v1)):
        if not S.is_locale_dir(entry):
            continue
        tag = S.LOCALE_DIR_RE.match(entry).group(1)
        locale_strings = S.load_dir(os.path.join(res_v1, entry), S.V1_SOURCE_FILES)
        for v2_key, (v1_key, _kind) in matches.items():
            translated = locale_strings.get(v1_key)
            if translated is None:
                continue
            reason = None
            if not S.markup_matches(translated, v2_en[v2_key]):
                reason = "markup"
            elif not S.is_english_variant(tag) and S.leftover_english(
                translated, v2_en[v2_key], v1_en[v1_key]
            ):
                reason = "leftover"
            elif S.spliced_translation(translated):
                reason = "splice"
            elif not S.is_english_variant(tag) and S.duplicated_tail(
                translated, v1_en[v1_key]
            ):
                reason = "tail"
            if reason:
                defects.setdefault(entry, {})[v1_key] = reason
    return defects


def annotate_file(path: str, keys: dict[str, str], apply: bool) -> int:
    """Insert (or refresh) a marker comment above each flagged string. Returns count."""
    with open(path, encoding="utf-8") as handle:
        text = handle.read()

    # Drop any previous markers so re-runs stay idempotent.
    text = re.sub(rf"[ \t]*<!--[ \t]*{MARKER}.*?-->\n", "", text, flags=re.S)

    changed = 0
    for key, reason in sorted(keys.items()):
        pattern = re.compile(
            rf'(^([ \t]*)<string\s+name="{re.escape(key)}"[\s>])',
            re.M,
        )
        match = pattern.search(text)
        if not match:
            print(f"    ! {key}: not found in {os.path.basename(path)}", file=sys.stderr)
            continue
        indent = match.group(2)
        comment = (
            f"{indent}<!-- {MARKER}: {REASON_TEXT[reason]}.\n"
            f"{indent}     Not carried into v2 by tools/salvage-translations/salvage.py;"
            f" needs a fresh translation. -->\n"
        )
        text = text[: match.start(1)] + comment + text[match.start(1):]
        changed += 1

    if apply and changed:
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text)
    return changed


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--apply", action="store_true",
                        help="Write the comments. Default is a dry run.")
    parser.add_argument("--v1-res", default=S.V1_RES,
                        help="Path to the v1 res/ directory to annotate. Defaults to the "
                             "snapshot in this monorepo; pass the standalone v1 checkout "
                             "(e.g. ../stardroid/stardroid-v1/app/src/main/res) to write "
                             "where v1 is actually maintained.")
    args = parser.parse_args()

    v1_res = args.v1_res
    if not os.path.isdir(v1_res):
        print(f"error: no such directory: {v1_res}", file=sys.stderr)
        return 2
    if not os.path.isdir(S.V2_RES):
        print("error: run from the repository root (the dir holding stardroid-v2/).",
              file=sys.stderr)
        return 2
    print(f"v1 res : {os.path.abspath(v1_res)}")
    print(f"v2 res : {os.path.abspath(S.V2_RES)}\n")

    defects = find_defects(v1_res, S.V2_RES)
    total = sum(len(v) for v in defects.values())
    print(f"flagged strings: {total} across {len(defects)} locales\n")

    written = 0
    for locale_dir in sorted(defects):
        keys = defects[locale_dir]
        path = os.path.join(v1_res, locale_dir, "strings.xml")
        count = annotate_file(path, keys, args.apply)
        written += count
        for key, reason in sorted(keys.items()):
            print(f"  {locale_dir:14s} {key:38s} {reason}")

    print()
    if args.apply:
        print(f"annotated {written} strings in {len(defects)} v1 locale files")
    else:
        print(f"dry run - would annotate {written} strings. Re-run with --apply.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

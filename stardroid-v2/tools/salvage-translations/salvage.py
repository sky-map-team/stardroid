#!/usr/bin/env python3
"""One-off salvage of v1's translated UI strings into v2's string resources.

v2 rewrote its resource keys, but ~60% of its English UI strings are byte-identical
to a string v1 already had translated into 32 locales. This script carries those
translations over so `tm translate` only has to handle what is genuinely new.

Matching happens against the *English* text, in three tiers:

  exact-key   v2 key == v1 key, English text identical      -> auto-applied
  exact-text  keys differ, English text identical           -> auto-applied
  fuzzy       English text similar above --fuzzy-threshold  -> REPORTED ONLY

Fuzzy matches are never written. A high similarity score does not mean the meaning
survived: v1 PR #895 deliberately reworded compass copy from "calibration" to
"attention", and v2 rewrote no_sensor_warning from "may lack sensors" to a definite
"has no compass". Carrying those over would silently resurrect superseded wording in
30 locales. They go to the review manifest for a human or `tm` to decide.

Long-form documents (help/credits/eula/whatsnew) are excluded by default: v2 rewrote
them, so they are being retranslated wholesale rather than salvaged (see
docs/design/localization.md section 1). Pass --include-long-form to override.

Reads (from stardroid-v1/app/src/main/res/):
  values/*.xml, values-*/*.xml        English sources and their translations
Writes (to stardroid-v2/app/src/main/res/):
  values-{locale}/strings.xml         carried-over translations
  (and a review manifest to stdout or --manifest)

Run from the repository root:
  python3 stardroid-v2/tools/salvage-translations/salvage.py --dry-run
  python3 stardroid-v2/tools/salvage-translations/salvage.py --apply
"""

import argparse
import difflib
import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import OrderedDict
from xml.sax.saxutils import escape

V1_RES = "stardroid-v1/app/src/main/res"
V2_RES = "stardroid-v2/app/src/main/res"

# v1 files worth mining for short UI strings. celestial_objects.xml is included because v2
# keeps *catalog* names in the generated DB (D34) but still has string resources for the
# handful the UI names directly — the planets, and the Crab Nebula card the warm welcome
# shows. Those are identical English and already translated in 29 locales.
V1_SOURCE_FILES = ["strings.xml", "arrays.xml", "celestial_objects.xml"]
V2_SOURCE_FILES = ["strings.xml"]

# Rewritten in v2 and being retranslated wholesale, not salvaged.
LONG_FORM_FILES = ["help.xml", "credits.xml", "eula.xml", "whatsnew.xml"]

# v1 ships these as BCP-47 `b+` directories; v2/Android also accept the modern form.
LOCALE_DIR_RE = re.compile(r"^values-(.+)$")

# Qualifiers that are not locales at all.
NON_LOCALE_QUALIFIERS = {"w820dp", "large", "land", "port", "night", "v21", "v26"}

FORMAT_SPEC_RE = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")

# Terms .tmconfig.toml requires every locale to keep in English. A translation that shares
# only these with its source is correctly translated, not half-translated.
PRESERVED_TERMS = (
    "Sky Map",
    "Android",
    "Google Analytics",
    "Google Play",
    "Hubble",
    "NASA",
    "GPS",
)


def is_locale_dir(name: str) -> bool:
    m = LOCALE_DIR_RE.match(name)
    if not m:
        return False
    qualifier = m.group(1)
    if qualifier in NON_LOCALE_QUALIFIERS:
        return False
    # Screen-size / density / version qualifiers appended to a locale are out of scope.
    return not any(q in NON_LOCALE_QUALIFIERS for q in qualifier.split("-"))


def _escape_text(text: str) -> str:
    """Escape a bare text run for XML.

    ElementTree hands back decoded text, so a v1 string that contained `&lt;` (Arabic
    time-travel speeds use a literal `<`) arrives as a bare `<` and would produce a
    malformed resource file. Ampersands must go first or the escaping compounds.
    """
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def inner_xml(elem: ET.Element) -> str:
    """Serialize an element's content, preserving inline markup (<b>, <i>, <a>) as raw tags.

    Both delivery forms converge here on *raw* markup: v2's CDATA text already contains
    real tags, and v1's child elements are serialized back into them. Escaping instead
    would make a CDATA string indistinguishable from plain text with literal angle
    brackets, which is what `tm validate` keys off (see [needs_cdata]).

    Text runs still need `&` escaped, or an ampersand in the prose would corrupt the
    output file — but `<` is left alone so genuine markup survives.
    """
    parts = [(elem.text or "").replace("&", "&amp;")]
    for child in elem:
        parts.append(ET.tostring(child, encoding="unicode"))
    return "".join(parts)


def load_strings(path: str) -> "OrderedDict[str, str]":
    """Parse an Android strings file -> {name: raw text}, skipping translatable=false."""
    out: "OrderedDict[str, str]" = OrderedDict()
    if not os.path.exists(path):
        return out
    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        print(f"  ! parse error in {path}: {exc}", file=sys.stderr)
        return out
    for elem in tree.getroot():
        if elem.tag != "string":
            continue
        if elem.get("translatable") == "false":
            continue
        name = elem.get("name")
        if not name:
            continue
        out[name] = inner_xml(elem)
    return out


def load_dir(res_dir: str, files: list[str]) -> "OrderedDict[str, str]":
    merged: "OrderedDict[str, str]" = OrderedDict()
    for filename in files:
        merged.update(load_strings(os.path.join(res_dir, filename)))
    return merged


def unescape_markup(text: str) -> str:
    """Turn escaped markup back into real markup, so CDATA and raw forms compare equal."""
    return text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")


def normalize(text: str) -> str:
    """Fold whitespace, escaping, and CDATA-vs-raw markup, preserving case.

    Case is deliberately significant. v2 has both `east` ("EAST" — the all-caps horizon
    label on the map) and `diagnostics_east` ("East" — a sentence-case table row). Folding
    case matches the second against v1's first and every locale inherits the shouty form.

    Markup delivery is *not* significant: v2 wraps inline HTML in CDATA (so ElementTree
    hands back a literal "<b>") where v1 wrote it as child elements (which serialize to
    "&lt;b&gt;" once re-escaped). Both render identically, so unescaping here lets an
    otherwise-identical string match instead of landing in the fuzzy pile.
    """
    text = text.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
    return re.sub(r"\s+", " ", unescape_markup(text)).strip()


def normalize_ci(text: str) -> str:
    """Case-folded form, used only to *report* near-misses — never to auto-apply."""
    return normalize(text).lower()


TAG_RE = re.compile(r"</?([a-zA-Z][a-zA-Z0-9]*)[^>]*?/?>")


def tag_multiset(text: str) -> list[str]:
    """Sorted list of HTML tag names, counting open and close separately."""
    out = []
    for match in TAG_RE.finditer(unescape_markup(text)):
        raw = match.group(0)
        if raw.endswith("/>"):
            continue  # self-closing (<br/>) needs no partner
        out.append(("/" if raw.startswith("</") else "") + match.group(1).lower())
    return sorted(out)


def markup_matches(translated: str, v2_english: str) -> bool:
    """True when the translation carries the same inline markup as its v2 source.

    Exact parity, not "same or none": `tm validate` reports a dropped `<b>` as
    `broken_html`, so a translation that omits the emphasis (zh-Hans and fr do, and their
    prose is otherwise complete) cannot be carried over even though it reads fine. Those
    land in the manifest as v1 damage and `tm` retranslates them with the markup intact.
    """
    return tag_multiset(translated) == tag_multiset(v2_english)


def needs_cdata(v2_english: str) -> bool:
    """True when the v2 source key delivers its markup as CDATA-wrapped raw HTML.

    `tm` classifies a v2 string holding real `<b>` tags as CDATA_HTML and validates the
    translation with a regex that looks for *raw* tags. A carried-over translation that
    escapes them to `&lt;b&gt;` therefore reads as "all tags missing". Detected on the v2
    English (which ElementTree hands back with the CDATA already unwrapped) rather than
    hardcoded per key, so new CDATA strings are covered automatically.
    """
    return bool(TAG_RE.search(v2_english))


def match_escaping(translated: str, v2_english: str) -> str:
    """Render the translation's markup the way the v2 source key delivers it.

    Escaped and raw markup render identically in Android but are *not* interchangeable to
    `tm validate`: see [needs_cdata]. v1 wrote inline HTML as child elements, so it arrives
    raw and must be re-escaped for a non-CDATA v2 key, or left raw (and CDATA-wrapped by
    the writer) for a CDATA one.
    """
    raw = unescape_markup(translated)
    if needs_cdata(v2_english):
        return raw
    # No markup expected: any stray angle bracket is literal text (v1's Arabic
    # time-travel speeds use a bare "<"), so escape it.
    return _escape_text(raw)


def is_english_variant(locale_tag: str) -> bool:
    """True for en-GB and friends, where sharing text with the source is the whole point."""
    return locale_tag.lower().replace("b+", "").replace("+", "-").startswith("en")


def leftover_english(translated: str, v2_english: str, v1_english: str) -> bool:
    """True when a 'translation' still contains a long run of its English source.

    Several v1 locales half-translated the warm-welcome strings: the translated prose was
    prepended to an intact English fragment ("... in einer Acht-Bewegung.\\n <b>figure-8</b>
    wave every once in a while."). Tag counts still match, so the markup check passes —
    but importing it ships visible English inside a translated string.
    """
    source = normalize(v1_english)
    body = normalize(translated)
    if len(source) < 40 or body == source:
        # Short strings share too much by chance, and a translation identical to its
        # source is normal (proper nouns, "OK") rather than a half-translation.
        return False
    match = difflib.SequenceMatcher(None, source, body).find_longest_match(
        0, len(source), 0, len(body)
    )
    run = source[match.a:match.a + match.size].strip()

    # Brand names are *required* to survive untranslated (.tmconfig.toml translation_notes),
    # so a shared run made only of them is correct, not corruption.
    for brand in PRESERVED_TERMS:
        run = run.replace(brand, " ")
    # Markup and punctuation alone prove nothing either.
    words = re.findall(r"[^\W\d_]{3,}", TAG_RE.sub(" ", run))
    if len(words) < 2:
        return False
    # Require the leftover to be a real chunk of the string, not an incidental collocation.
    return len(" ".join(words)) >= max(12, 0.15 * len(source))


# A merge artifact from v1's own history: translated prose glued to the English original,
# leaving "...erkunden!=\n    <b>Manual Mode</b>!". The stray "=" between a sentence end and
# the resumed English is the reliable tell; no correct translation contains it.
SPLICE_RE = re.compile(r"[.!?]\s*=\s")


def spliced_translation(translated: str) -> bool:
    """True when a translation shows v1's translated-plus-English merge artifact."""
    return bool(SPLICE_RE.search(normalize(translated)))


def duplicated_tail(translated: str, v1_english: str) -> bool:
    """True when the translation ends by repeating the tail of its English source.

    The `warm_welcome_slide3_*` strings were merged badly across 24 of v1's locales: the
    finished translation is followed by the last clause of the English original, e.g.
    "...宇宙全体を探索できます！<b>Manual Mode</b>!". [leftover_english] misses this because the
    repeated run ("Manual Mode") is short — but position makes it unambiguous. A *trailing*
    fragment that also appears near the end of the English source is never correct, however
    few words it has.
    """
    body = normalize(translated).rstrip()
    source = normalize(v1_english).rstrip()
    if not body or body == source:
        return False

    # Find the longest suffix of the source that the translation also ends with.
    shared = ""
    for size in range(1, min(len(body), len(source)) + 1):
        if body[-size:] == source[-size:]:
            shared = body[-size:]
        else:
            break
    shared = shared.strip()
    if len(shared) < 6:
        return False

    # The shared run must be a *separable* trailing clause, not the natural tail of a
    # sentence that happens to converge. A half-translation leaves the English behind after
    # a sentence end or markup boundary ("...探索できます！<b>Manual Mode</b>!"); a merely
    # cognate ending runs straight on from translated words (gl "Lonxitude:%1$.2f,
    # Latitude:%2$.2f" shares all but one letter with English and is entirely correct).
    boundary = body[: len(body) - len(shared)].rstrip()
    if boundary and not re.search(r"[.!?！。>]$", boundary):
        return False

    # A correct translation often ends the same way as its source: a preserved brand
    # ("Google Analytics"), a units/abbreviation parenthetical ("(lat, long)"), or a
    # cognate. None of those make it a half-translation. Require the shared suffix to
    # contain real English *prose* words beyond those.
    probe = shared
    for brand in PRESERVED_TERMS:
        probe = probe.replace(brand, " ")
    probe = TAG_RE.sub(" ", probe)
    # Drop parentheticals and any short/abbreviated tokens.
    probe = re.sub(r"\([^)]*\)", " ", probe)
    words = [w for w in re.findall(r"[A-Za-z]{4,}", probe)]
    if len(words) < 2:
        return False

    # Finally: the shared suffix must be a *tail*, not the whole translation being English.
    return len(shared) < len(body)


def format_specs(text: str) -> list[str]:
    """Positional-agnostic multiset of format specifiers, for validation."""
    specs = []
    for raw in FORMAT_SPEC_RE.findall(text):
        if raw == "%%":
            continue
        specs.append(re.sub(r"^%\d+\$", "%", raw))
    return sorted(specs)


def retarget_specifiers(v1_text: str, v2_english: str) -> tuple[str, bool]:
    """Rewrite %s -> %1$s (etc.) when v2 modernized the source string's specifiers.

    Returns (text, ok). ok is False when the specifier multisets disagree, which means
    the translation cannot be safely carried over — Android would crash at format time.
    """
    v1_specs = format_specs(v1_text)
    v2_specs = format_specs(v2_english)
    if v1_specs != v2_specs:
        return v1_text, False

    v1_positional = bool(re.search(r"%\d+\$", v1_text))
    v2_positional = bool(re.search(r"%\d+\$", v2_english))
    if v2_positional and not v1_positional:
        counter = {"n": 0}

        def bump(match: re.Match) -> str:
            if match.group(0) == "%%":
                return match.group(0)
            counter["n"] += 1
            return "%" + str(counter["n"]) + "$" + match.group(0)[1:]

        return FORMAT_SPEC_RE.sub(bump, v1_text), True
    if v1_positional and not v2_positional:
        return re.sub(r"%(\d+)\$", "%", v1_text), True
    return v1_text, True


def build_index(v1_en: "OrderedDict[str, str]") -> dict[str, list[str]]:
    index: dict[str, list[str]] = {}
    for key, text in v1_en.items():
        index.setdefault(normalize(text), []).append(key)
    return index


def classify(v2_en, v1_en, text_index, fuzzy_threshold):
    """Resolve each v2 key to a v1 source key.

    Returns (matches, fuzzy, case_only, unmatched). Only exact-key and exact-text
    (case-sensitive, unambiguous) land in `matches` and are auto-applied; the rest is
    reported for review.
    """
    matches: "OrderedDict[str, tuple[str, str]]" = OrderedDict()
    fuzzy: list[tuple[str, str, float]] = []
    case_only: list[tuple[str, str, str]] = []
    unmatched: list[str] = []

    v1_normalized = {k: normalize(v) for k, v in v1_en.items()}
    ci_index: dict[str, list[str]] = {}
    for key, text in v1_en.items():
        ci_index.setdefault(normalize_ci(text), []).append(key)

    for v2_key, v2_text in v2_en.items():
        norm = normalize(v2_text)
        if v2_key in v1_en and normalize(v1_en[v2_key]) == norm:
            matches[v2_key] = (v2_key, "exact-key")
            continue

        candidates = text_index.get(norm)
        if candidates:
            # Several v1 keys can share English text ("Diagnostics" was both a menu item
            # and an activity title). They were translated independently and may differ,
            # so an arbitrary pick is a coin flip — but only when the translations really
            # diverge, which the caller cannot see here. Prefer the v2 key if present,
            # else the first, and flag genuine ambiguity to the manifest.
            chosen = v2_key if v2_key in candidates else candidates[0]
            kind = "exact-text" if len(candidates) == 1 else "exact-text-ambiguous"
            matches[v2_key] = (chosen, kind)
            continue

        # Case-only differences are NOT auto-applied: see normalize(). They split into two
        # very different situations, so they are reported as separate tiers.
        ci_candidates = ci_index.get(normalize_ci(v2_text))
        if ci_candidates:
            src = ci_candidates[0]
            v1_text = normalize(v1_en[src])
            v2_norm = normalize(v2_text)
            # ALL-CAPS vs sentence case is a different string with a different job
            # (map horizon label vs table row). Never reuse.
            if v1_text.isupper() != v2_norm.isupper():
                case_only.append((v2_key, src, "shouty"))
                continue
            # Title-case vs sentence-case is an English-only distinction: v1's
            # translations already follow each language's own convention ("Système
            # solaire", and Japanese has no case at all), so v2's capitalization change
            # carries no information into them. Reuse rather than pay to regenerate.
            case_only.append((v2_key, src, "title-case"))
            matches[v2_key] = (src, "title-case")
            continue

        best_key, best_ratio = None, 0.0
        for v1_key, v1_norm in v1_normalized.items():
            if abs(len(v1_norm) - len(norm)) > max(60, len(norm) * 1.5):
                continue
            ratio = difflib.SequenceMatcher(None, norm, v1_norm).ratio()
            if ratio > best_ratio:
                best_key, best_ratio = v1_key, ratio
        if best_key and best_ratio >= fuzzy_threshold:
            fuzzy.append((v2_key, best_key, best_ratio))
        else:
            unmatched.append(v2_key)

    return matches, fuzzy, case_only, unmatched


def write_locale_file(path: str, entries: "OrderedDict[str, str]") -> None:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!--",
        "  Carried over from v1 by tools/salvage-translations/salvage.py.",
        "  Only strings whose English source is identical between v1 and v2 appear here;",
        "  everything else is translated by `tm`. Safe to hand-edit thereafter.",
        "-->",
        "<resources>",
    ]
    for name, text in entries.items():
        # Raw inline markup has to be CDATA-wrapped to be valid XML at all; escaped
        # markup and plain text go in directly. match_escaping picked which form this
        # string uses, following its v2 English source.
        if TAG_RE.search(text):
            body = f"<![CDATA[{text}]]>"
        else:
            body = text
        lines.append(f'    <string name="{escape(name)}">{body}</string>')
    lines.append("</resources>")
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--apply", action="store_true",
                        help="Write values-*/strings.xml. Default is a dry run.")
    parser.add_argument("--fuzzy-threshold", type=float, default=0.70,
                        help="Report fuzzy matches at or above this ratio (default 0.70).")
    parser.add_argument("--include-long-form", action="store_true",
                        help="Also mine help/credits/eula/whatsnew (default: skip; see docstring).")
    parser.add_argument("--manifest", default=None,
                        help="Write the review manifest here instead of stdout.")
    parser.add_argument("--locale", action="append", default=None,
                        help="Restrict to one locale (repeatable). Default: all discovered.")
    args = parser.parse_args()

    if not os.path.isdir(V1_RES) or not os.path.isdir(V2_RES):
        print("error: run from the repository root (the dir holding stardroid-v1/ and -v2/).",
              file=sys.stderr)
        return 2

    v1_files = list(V1_SOURCE_FILES)
    v2_files = list(V2_SOURCE_FILES)
    if args.include_long_form:
        v1_files += LONG_FORM_FILES
        v2_files += LONG_FORM_FILES

    v1_en = load_dir(os.path.join(V1_RES, "values"), v1_files)
    v2_en = load_dir(os.path.join(V2_RES, "values"), v2_files)
    text_index = build_index(v1_en)

    matches, fuzzy, case_only, unmatched = classify(
        v2_en, v1_en, text_index, args.fuzzy_threshold
    )

    exact_key = sum(1 for _, kind in matches.values() if kind == "exact-key")
    exact_text = sum(1 for _, kind in matches.values() if kind == "exact-text")
    ambiguous = [(k, src) for k, (src, kind) in matches.items()
                 if kind == "exact-text-ambiguous"]
    total = len(v2_en)
    print(f"v2 translatable strings : {total}")
    print(f"v1 candidate strings    : {len(v1_en)}")
    print(f"  exact-key matches     : {exact_key}")
    print(f"  exact-text matches    : {exact_text}  (renamed keys)")
    print(f"  ambiguous exact-text  : {len(ambiguous)}  (applied; listed in manifest)")
    print(f"  auto-applied total    : {len(matches)} ({100 * len(matches) / max(total, 1):.0f}%)")
    title_case = [c for c in case_only if c[2] == "title-case"]
    shouty = [c for c in case_only if c[2] == "shouty"]
    print(f"  fuzzy (review only)   : {len(fuzzy)}")
    print(f"  title-case only       : {len(title_case)}  (reusable in most locales)")
    print(f"  caps mismatch         : {len(shouty)}  (do NOT reuse)")
    print(f"  no match -> tm        : {len(unmatched)}")
    print()

    locales = []
    for entry in sorted(os.listdir(V1_RES)):
        if not is_locale_dir(entry):
            continue
        tag = LOCALE_DIR_RE.match(entry).group(1)
        if args.locale and tag not in args.locale:
            continue
        locales.append((tag, entry))

    spec_failures: list[tuple[str, str]] = []
    markup_failures: list[tuple[str, str]] = []
    written = 0
    print(f"{'locale':12s} {'carried':>8s} {'missing':>8s} {'spec-skip':>10s}")
    for tag, dirname in locales:
        v1_loc = load_dir(os.path.join(V1_RES, dirname), v1_files)
        carried: "OrderedDict[str, str]" = OrderedDict()
        missing = 0
        skipped = 0
        for v2_key, (v1_key, _kind) in matches.items():
            translated = v1_loc.get(v1_key)
            if translated is None:
                missing += 1
                continue
            if not markup_matches(translated, v2_en[v2_key]):
                markup_failures.append((tag, v2_key))
                skipped += 1
                continue
            if not is_english_variant(tag) and leftover_english(
                translated, v2_en[v2_key], v1_en[v1_key]
            ):
                markup_failures.append((tag, f"{v2_key} (untranslated English retained)"))
                skipped += 1
                continue
            if spliced_translation(translated):
                markup_failures.append((tag, f"{v2_key} (v1 merge artifact)"))
                skipped += 1
                continue
            if not is_english_variant(tag) and duplicated_tail(translated, v1_en[v1_key]):
                markup_failures.append((tag, f"{v2_key} (English tail repeated)"))
                skipped += 1
                continue
            translated = match_escaping(translated, v2_en[v2_key])
            retargeted, ok = retarget_specifiers(translated, v2_en[v2_key])
            if not ok:
                spec_failures.append((tag, v2_key))
                skipped += 1
                continue
            carried[v2_key] = retargeted
        print(f"{tag:12s} {len(carried):8d} {missing:8d} {skipped:10d}")

        if args.apply and carried:
            out_dir = os.path.join(V2_RES, f"values-{tag}")
            os.makedirs(out_dir, exist_ok=True)
            write_locale_file(os.path.join(out_dir, "strings.xml"), carried)
            written += 1

    print()
    if args.apply:
        print(f"wrote {written} locale files under {V2_RES}/values-*/strings.xml")
    else:
        print("dry run - nothing written. Re-run with --apply to write locale files.")

    manifest = []
    manifest.append("# Salvage review manifest\n")
    manifest.append("Fuzzy matches are NOT auto-applied. Each needs a human or `tm` decision:\n")
    manifest.append("carrying a translation whose English source changed meaning silently\n")
    manifest.append("resurrects superseded wording in every locale.\n\n")
    manifest.append(f"## Fuzzy matches ({len(fuzzy)}) - review each\n\n")
    for v2_key, v1_key, ratio in sorted(fuzzy, key=lambda x: -x[2]):
        manifest.append(f"### {v2_key}  <- v1:{v1_key}  (similarity {ratio:.2f})\n")
        manifest.append(f"  v1 EN: {normalize(v1_en[v1_key])[:300]}\n")
        manifest.append(f"  v2 EN: {normalize(v2_en[v2_key])[:300]}\n\n")
    if title_case:
        manifest.append(f"\n## Title-case only ({len(title_case)}) - APPLIED\n\n")
        manifest.append("English capitalization changed (\"Solar System\" -> \"Solar system\").\n")
        manifest.append("Reviewed and approved for reuse: v1's translations already follow\n")
        manifest.append("each target language's own convention (fr \"Système solaire\", and\n")
        manifest.append("Japanese has no case at all), so the English change carries no\n")
        manifest.append("information into them. Listed here for the record, not for action.\n\n")
        for v2_key, src, _ in title_case:
            manifest.append(f"- {v2_key} <- v1:{src}\n")
            manifest.append(f"    v1 EN: {normalize(v1_en[src])}\n")
            manifest.append(f"    v2 EN: {normalize(v2_en[v2_key])}\n")
    if shouty:
        manifest.append(f"\n## Capitalization mismatch ({len(shouty)}) - do NOT reuse\n\n")
        manifest.append("One is ALL CAPS and the other is not: these are different strings\n")
        manifest.append("with different jobs (map horizon label vs. table row). Let `tm`\n")
        manifest.append("translate them fresh.\n\n")
        for v2_key, src, _ in shouty:
            manifest.append(f"- {v2_key} (v2 EN: {normalize(v2_en[v2_key])}) "
                            f"vs v1:{src} ({normalize(v1_en[src])})\n")
    if ambiguous:
        manifest.append(f"\n## Ambiguous exact-text matches ({len(ambiguous)}) - applied, verify\n\n")
        manifest.append("Several v1 keys shared this English text and were translated\n")
        manifest.append("independently, so the carried-over one may not be the best fit.\n\n")
        for v2_key, src in ambiguous:
            manifest.append(f"- {v2_key} <- v1:{src}  (EN: {normalize(v2_en[v2_key])[:80]})\n")
    manifest.append(f"\n## No match - `tm` must translate ({len(unmatched)})\n\n")
    for key in sorted(unmatched, key=lambda k: -len(v2_en[k])):
        manifest.append(f"- {key} ({len(normalize(v2_en[key]))} chars)\n")
    if markup_failures:
        manifest.append(f"\n## Broken markup in v1, skipped ({len(markup_failures)})\n\n")
        manifest.append("v1's own translation has different inline markup than the v2 source -\n")
        manifest.append("several locales half-translated these, leaving an English fragment\n")
        manifest.append("beside the translated text. Left for `tm` to translate fresh.\n\n")
        for tag, key in markup_failures:
            manifest.append(f"- {tag}: {key}\n")
    if spec_failures:
        manifest.append(f"\n## Format-specifier mismatches, skipped ({len(spec_failures)})\n\n")
        manifest.append("These would crash at String.format time; left for `tm`.\n\n")
        for tag, key in spec_failures:
            manifest.append(f"- {tag}: {key}\n")
    manifest_text = "".join(manifest)

    if args.manifest:
        with open(args.manifest, "w", encoding="utf-8") as handle:
            handle.write(manifest_text)
        print(f"review manifest -> {args.manifest}")
    else:
        print()
        print(manifest_text)

    if spec_failures:
        print(f"note: {len(spec_failures)} format-specifier mismatches were skipped.",
              file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())

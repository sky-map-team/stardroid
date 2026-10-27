#!/usr/bin/env python3
# Copyright (c) 2026 Penterakt LLC.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
"""Move string resources from one tm source to another, in every locale, keeping tm's state.

Phase 5 of the iOS port moves screens from :app to :shared:ui one at a time, and each brings its
strings from app/src/main/res to shared/ui/src/commonMain/res (D136). A move has three parts,
and missing any of them makes tm see every moved translation as new or stale:

- the <string>, <string-array> and <plurals> elements, in every values* folder;
- tm's `<!-- tm:omitted name="..." -->` markers for those names (strings identical to English);
- the snapshots in .tmstate.toml (`source_snapshot.<source>."<file>".<locale>` and any other
  per-source table), keyed by name or, for arrays and plurals, `"name[index]"`.

Run from stardroid-v2/. Check `tm languages` before and after: strings, same, stale and coverage
must not change.

    tools/move_strings.py hud_alt_label hud_az_label hud_cardinal_directions
"""

import argparse
import os
import re
import sys

# Attribute values may hold a raw '>' (a translator's note), so quoted values are skipped whole.
ELEMENT = r'^[ \t]*<(string|string-array|plurals) name="(%s)"(?:[^>"]|"[^"]*")*>.*?</\1>[ \t]*\n'
MARKER = r'^[ \t]*<!-- tm:omitted name="(%s)"[^\n]*-->[ \t]*\n'


def move_elements(args, keys):
    names = "|".join(re.escape(k) for k in keys)
    element = re.compile(ELEMENT % names, re.S | re.M)
    marker = re.compile(MARKER % names, re.M)
    english = os.path.join(args.from_dir, "values", args.from_file)
    found = {m.group(2) for m in element.finditer(open(english, encoding="utf-8").read())}
    missing = set(keys) - found
    if missing:
        sys.exit(f"not in {english}: {', '.join(sorted(missing))}")

    moved = 0
    for folder in sorted(os.listdir(args.from_dir)):
        source = os.path.join(args.from_dir, folder, args.from_file)
        if not folder.startswith("values") or not os.path.exists(source):
            continue
        text = open(source, encoding="utf-8").read()
        taken = [m.group(0) for m in sorted(
            list(element.finditer(text)) + list(marker.finditer(text)), key=lambda m: m.start())]
        if not taken:
            continue
        text = marker.sub("", element.sub("", text))
        open(source, "w", encoding="utf-8").write(text)

        target = os.path.join(args.to_dir, folder, args.to_file)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        if os.path.exists(target):
            existing = open(target, encoding="utf-8").read()
        else:
            declaration = text.split("\n", 1)[0]
            existing = f"{declaration}\n<resources>\n</resources>\n"
        close = existing.rindex("</resources>")
        existing = existing[:close] + "".join(taken) + existing[close:]
        open(target, "w", encoding="utf-8").write(existing)
        moved += 1
    print(f"moved {len(keys)} name(s) in {moved} folder(s)")


def move_state(args, keys):
    path = ".tmstate.toml"
    lines = open(path, encoding="utf-8").read().split("\n")
    header = re.compile(r"^\[(\w+)\.%s\.\"%s\"\.(.+)\]$" % (
        re.escape(args.from_source), re.escape(args.from_file)))
    key = re.compile(r'^"?(%s)(\[[^\]]*\])?"? = ' % "|".join(re.escape(k) for k in keys))

    sections = []  # [header line or None, [lines]]
    for line in lines:
        if line.startswith("["):
            sections.append([line, []])
        elif sections:
            sections[-1][1].append(line)
        else:
            sections.append([None, [line]])

    moved = {}
    for section in sections:
        match = header.match(section[0] or "")
        if not match:
            continue
        target = f'[{match.group(1)}.{args.to_source}."{args.to_file}".{match.group(2)}]'
        keep = []
        for line in section[1]:
            (moved.setdefault(target, []) if key.match(line) else keep).append(line)
        section[1] = keep

    for target, entries in moved.items():
        existing = next((s for s in sections if s[0] == target), None)
        if existing is None:
            # A new table, at the end, blank-line separated like the rest.
            while sections[-1][1] and sections[-1][1][-1] == "":
                sections[-1][1].pop()
            sections[-1][1].append("")
            sections.append([target, sorted(entries) + [""]])
        else:
            body = existing[1]
            while body and body[-1] == "":
                body.pop()
            existing[1] = sorted(body + entries) + [""]

    out = []
    for head, body in sections:
        if head is not None:
            out.append(head)
        out.extend(body)
    open(path, "w", encoding="utf-8").write("\n".join(out).rstrip("\n") + "\n")
    print(f"moved {sum(len(v) for v in moved.values())} snapshot entries in {len(moved)} table(s)")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("keys", nargs="+")
    parser.add_argument("--from-source", default="app")
    parser.add_argument("--from-dir", default="app/src/main/res")
    parser.add_argument("--from-file", default="strings.xml")
    parser.add_argument("--to-source", default="shared-ui")
    parser.add_argument("--to-dir", default="shared/ui/src/commonMain/res")
    parser.add_argument("--to-file", default="strings.xml")
    args = parser.parse_args()
    move_elements(args, args.keys)
    move_state(args, args.keys)


if __name__ == "__main__":
    main()

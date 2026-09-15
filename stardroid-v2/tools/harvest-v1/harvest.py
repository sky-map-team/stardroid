#!/usr/bin/env python3
"""One-off harvest of v1 data files into v2's checked-in source-data/ tree.

Reads (from stardroid-v1/):
  tools/data/stardata_names.txt              raw star catalog (true magnitudes)
  tools/data/deep_sky_objects.csv            DSO catalog incl. detailed types
  tools/data/constellations.ascii            text-proto constellation figures
  app/src/main/assets/object_info.json       info-card registry (v2 format)
  app/src/main/res/values*/celestial_objects.xml     localized object names
  app/src/main/res/values*/celestial_info_cards.xml  localized card text

Writes (to stardroid-v2/source-data/):
  stars.csv, dso.csv, constellations/iau.json, objects.json,
  names/{tag}.csv (+ universal.csv), info_cards/{tag}.json

This script is retained for provenance; the source-data/ output is the
checked-in source of truth for :data:generateCatalogDb (catalog-and-schema.md).
Run from the repository root:  python3 stardroid-v2/tools/harvest-v1/harvest.py
"""

import csv
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import OrderedDict

V1 = "stardroid-v1"
OUT = "stardroid-v2/source-data"

# ---------------------------------------------------------------- id helpers

def rkeys(names: str) -> list[str]:
    """Port of v1 AbstractAsciiProtoWriter.rKeysFromName: name -> R.string key."""
    out = []
    for name in re.split(r"[|]+", names):
        key = re.sub(r"[^a-zA-Z0-9 _]", "", name).replace(" ", "_").lower()
        if not key:
            continue
        if key[0].isdigit():
            key = "n" + key
        out.append(key)
    return out


def positional_star_id(ra_deg: float, dec_deg: float) -> str:
    """IAU-style J-designation id for unnamed stars, e.g. star/j064459-164236."""
    h = ra_deg / 15.0
    hh = int(h)
    m = (h - hh) * 60
    mm = int(m)
    ss = int(round((m - mm) * 60))
    if ss == 60:
        ss = 0
        mm += 1
    if mm == 60:
        mm = 0
        hh += 1
    d = abs(dec_deg)
    dd = int(d)
    dm = (d - dd) * 60
    dmm = int(dm)
    dss = int(round((dm - dmm) * 60))
    if dss == 60:
        dss = 0
        dmm += 1
    if dmm == 60:
        dmm = 0
        dd += 1
    sign = "-" if dec_deg < 0 else "+"
    return f"star/j{hh:02d}{mm:02d}{ss:02d}{sign}{dd:02d}{dmm:02d}{dss:02d}"


# ------------------------------------------------------------ strings.xml IO

LOCALE_DIR_RE = re.compile(r"^values(-(.+))?$")


def android_locale_tag(dirname: str):
    """values-de -> de; values-b+zh+Hans -> zh-Hans; values -> en; else None."""
    m = LOCALE_DIR_RE.match(dirname)
    if not m:
        return None
    suffix = m.group(2)
    if suffix is None:
        return "en"
    if suffix.startswith("b+"):
        # Lowercase to match LocaleSpec's normalized fallback chain ("zh-hans").
        return suffix[2:].replace("+", "-").lower()
    # Skip resource qualifiers that aren't locales (values-w820dp etc).
    if not re.fullmatch(r"[a-z]{2,3}(-r[A-Z]{2})?", suffix):
        return None
    return suffix.replace("-r", "-").lower()


def unescape_android(text: str) -> str:
    if text is None:
        return ""
    text = text.strip()
    if len(text) >= 2 and text[0] == '"' and text[-1] == '"':
        text = text[1:-1]
    text = (
        text.replace("\\'", "'")
        .replace('\\"', '"')
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\\", "\\")
    )
    return re.sub(r"\s+", " ", text).strip()


def read_strings(path: str) -> dict:
    if not os.path.exists(path):
        return {}
    out = {}
    for el in ET.parse(path).getroot():
        if el.tag != "string":
            continue
        text = "".join(el.itertext())
        out[el.get("name")] = unescape_android(text)
    return out


def load_locale_strings(filename: str) -> dict:
    """locale tag -> {key -> text} for one resource filename across values-* dirs."""
    res = os.path.join(V1, "app/src/main/res")
    out = {}
    for d in sorted(os.listdir(res)):
        tag = android_locale_tag(d)
        if tag is None:
            continue
        strings = read_strings(os.path.join(res, d, filename))
        if strings:
            out[tag] = strings
    return out


# ------------------------------------------------------------------- parsing

def parse_stars():
    """Rows of (id, ra, dec, mag, name_tokens). Raw columns: names,mag,dec,ra,x,y,z."""
    rows = []
    seen = {}
    for line in open(os.path.join(V1, "tools/data/stardata_names.txt"), encoding="utf-8"):
        line = line.strip()
        if not line:
            continue
        tokens = line.split(",")
        if len(tokens) != 7:
            raise ValueError(f"expected 7 columns: {line}")
        names, mag, dec, ra = tokens[0], float(tokens[1]), float(tokens[2]), float(tokens[3])
        name_tokens = [n for n in re.split(r"[|]+", names) if n.strip()]
        keys = rkeys(names)
        base = f"star/{keys[0]}" if keys else positional_star_id(ra, dec)
        n = seen.get(base, 0)
        seen[base] = n + 1
        oid = base if n == 0 else f"{base}-{n + 1}"
        rows.append((oid, ra, dec, mag, name_tokens))
    return rows


DETAILED_TYPE_TO_CODE = {
    "Supernova Remnant": "nebula.supernova_remnant",
    "Globular Cluster": "cluster.globular",
    "Open Cluster": "cluster.open",
    "Diffuse Nebula": "nebula.diffuse",
    "Emission Nebula": "nebula.emission",
    "Planetary Nebula": "nebula.planetary",
    "Star Cloud": "star_cloud",
    "Spiral Galaxy": "galaxy.spiral",
    "Elliptical Galaxy": "galaxy.elliptical",
    "Irregular Galaxy": "galaxy.irregular",
    "Lenticular Galaxy": "galaxy.lenticular",
    "Ultra-faint Dwarf Galaxy": "galaxy.dwarf",
    "Radio Galaxy": "galaxy.radio",
    "Double Star": "star.double",
    "Recurrent Nova": "star.nova",
    "Asterism": "asterism",
    "Black Hole": "black_hole",
    "Other": "other",
}

DESIGNATION_RE = re.compile(
    r"^(M ?\d+|NGC ?\d+|IC ?\d+|Mel ?\d+|Melotte ?\d+|Cr ?\d+|Collinder ?\d+"
    r"|Caldwell ?\d+|PMR ?\d+|HD ?\d+|47 Tuc|V838( Mon)?|T CrB|HDF|LMC|SMC)$",
    re.IGNORECASE,
)


def parse_dsos(info):
    """Rows of dict; joins object_info by shared string key."""
    path = os.path.join(V1, "tools/data/deep_sky_objects.csv")
    raw = [r for r in csv.reader(open(path, encoding="utf-8")) if r and any(x.strip() for x in r)]
    rows = []
    for r in raw[1:]:
        name_field = r[0]
        tokens = [t.strip() for t in re.split(r"[|]+", name_field) if t.strip()]
        ra = 15.0 * float(r[2])
        dec = float(r[3])
        mag = float(r[4]) if r[4].strip() else None
        size = float(r[5]) if r[5].strip() else None
        ngc = r[6].strip()
        detailed = r[8].strip() if len(r) > 8 else ""
        broad = r[1].strip()
        common = ",".join(r[9:]).strip() if len(r) > 9 else ""
        type_code = DETAILED_TYPE_TO_CODE.get(detailed) or DETAILED_TYPE_TO_CODE.get(broad)
        if type_code is None:
            raise ValueError(f"unmapped DSO type {detailed!r}/{broad!r} for {name_field}")

        # Common-name column adds a display name when it looks like a proper name
        # (v1 only labeled from the first column) and isn't already present.
        if common and "," not in common and len(common.split()) <= 4 \
                and not common.lower().startswith("a "):
            if rkeys(common) and rkeys(common)[0] not in [k for t in tokens for k in rkeys(t)]:
                tokens.append(common)

        keys = [k for t in tokens for k in rkeys(t)]
        info_key = next((k for k in keys if k in info), None)
        oid = f"dso/{info_key or keys[0]}"

        designations = []
        names = []
        for t in tokens:
            if DESIGNATION_RE.match(t):
                designations.append(t)
            else:
                names.append(t)
        if ngc:
            d = re.sub(r"^(NGC)\s*", r"NGC ", ngc)
            if d.upper() not in [x.upper().replace("  ", " ") for x in designations] \
                    and re.match(r"^NGC \d+$", d):
                designations.append(d)
        rows.append(
            {
                "id": oid,
                "ra": ra,
                "dec": dec,
                "magnitude": mag,
                "type": type_code,
                "size_arcmin": size,
                "designations": designations,
                "names": names,
                "info_key": info_key,
            }
        )
    return rows


def parse_constellations():
    """Parses the v1 text-proto into [{key, ra, dec, label_ra, label_dec, strokes}]."""
    raw = open(os.path.join(V1, "tools/data/constellations.ascii"), encoding="utf-8").read()
    text = "\n".join(
        line for line in raw.splitlines() if not line.lstrip().startswith("#")
    )
    tokens = re.findall(r"[{}]|[\w.]+:\s*(?:\"[^\"]*\"|[-\d.]+)|[\w.]+(?=\s*\{)", text)

    pos = 0

    def parse_block():
        nonlocal pos
        block = {}
        while pos < len(tokens):
            tok = tokens[pos]
            if tok == "}":
                pos += 1
                return block
            if tokens[pos + 1] == "{" if pos + 1 < len(tokens) else False:
                name = tok
                pos += 2
                block.setdefault(name, []).append(parse_block())
            else:
                m = re.match(r"([\w.]+):\s*(.*)", tok)
                if not m:
                    raise ValueError(f"unexpected token {tok!r}")
                key, val = m.group(1), m.group(2)
                if val.startswith('"'):
                    val = val[1:-1]
                else:
                    val = float(val) if ("." in val or "e" in val) else int(val)
                block.setdefault(key, []).append(val)
                pos += 1
        return block

    top = parse_block()
    out = []
    orphans = []
    for source in top["source"]:
        strokes = []
        for line in source.get("line", []):
            strokes.append(
                [[v["right_ascension"][0], v["declination"][0]] for v in line["vertex"]]
            )
        if "label" not in source:
            # Six sources are line-only (no label/search_location). v1 rendered all
            # sources as one all-or-none layer, so ownership never mattered there.
            orphans.append(strokes)
            continue
        label = source["label"][0]
        key = label["strings_str_id"][0]
        search = source["search_location"][0]
        loc = label["location"][0]
        out.append(
            {
                "key": key,
                "ra": search["right_ascension"][0],
                "dec": search["declination"][0],
                "label_ra": loc["right_ascension"][0],
                "label_dec": loc["declination"][0],
                "strokes": strokes,
            }
        )

    # Attach each orphan to the constellation whose search location is nearest the
    # orphan's stroke centroid (RA-wrap aware) — deterministic and visually identical.
    import math

    def angsep(ra1, dec1, ra2, dec2):
        dra = math.radians(min(abs(ra1 - ra2), 360 - abs(ra1 - ra2)))
        return math.hypot(dra * math.cos(math.radians((dec1 + dec2) / 2)),
                          math.radians(dec1 - dec2))

    for strokes in orphans:
        pts = [p for s in strokes for p in s]
        # Circular mean for RA so figures crossing RA 0 land on the right side.
        cra = math.degrees(
            math.atan2(
                sum(math.sin(math.radians(p[0])) for p in pts),
                sum(math.cos(math.radians(p[0])) for p in pts),
            )
        ) % 360
        cdec = sum(p[1] for p in pts) / len(pts)
        owner = min(out, key=lambda c: angsep(cra, cdec, c["ra"], c["dec"]))
        print(f"orphan figure ({cra:.1f}, {cdec:.1f}) -> {owner['key']}")
        owner["strokes"].extend(strokes)
    return out


# ------------------------------------------------------------------ assembly

INFO_TYPE_TO_CODE = {
    "star": "star",
    "moon": "moon",
    "planet": "planet",
    "dwarf_planet": "dwarf_planet",
    "nebula": "nebula",
    "cluster": "cluster",
    "galaxy": "galaxy",
    "constellation": "constellation",
    "black_hole": "black_hole",
    "meteor_shower": "meteor_shower",
    "other": "other",
}

SOLAR_SYSTEM_KEYS = {
    "sun", "moon", "mercury", "venus", "mars", "jupiter",
    "saturn", "uranus", "neptune", "pluto",
}


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(os.path.join(OUT, "constellations"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "names"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "info_cards"), exist_ok=True)

    info = json.load(open(os.path.join(V1, "app/src/main/assets/object_info.json"), encoding="utf-8"))["objects"]
    name_strings = load_locale_strings("celestial_objects.xml")
    card_strings = load_locale_strings("celestial_info_cards.xml")

    stars = parse_stars()
    dsos = parse_dsos(info)
    consts = parse_constellations()

    # ---- object_info key -> v2 id
    star_key_to_id = {}
    for oid, _, _, _, name_tokens in stars:
        for k in rkeys("|".join(name_tokens)):
            star_key_to_id.setdefault(k, oid)
    dso_key_to_id = {}
    for d in dsos:
        for t in d["designations"] + d["names"]:
            for k in rkeys(t):
                dso_key_to_id.setdefault(k, d["id"])
        if d["info_key"]:
            dso_key_to_id[d["info_key"]] = d["id"]
    const_keys = {c["key"] for c in consts}

    def v2_id(key: str, entry: dict) -> str:
        if key in SOLAR_SYSTEM_KEYS:
            return f"planet/{key}"
        t = entry["type"]
        if t == "moon":
            return f"moon/{key}"
        if t == "meteor_shower":
            return f"shower/{key}"
        if t == "constellation":
            assert key in const_keys, key
            return f"constellation/{key}"
        if t == "star" and key in star_key_to_id:
            return star_key_to_id[key]
        assert key in dso_key_to_id, f"unmatched object_info entry {key}"
        return dso_key_to_id[key]

    info_id = {k: v2_id(k, v) for k, v in info.items()}
    positional_ids = (
        {oid for oid, *_ in stars}
        | {d["id"] for d in dsos}
        | {f"constellation/{c['key']}" for c in consts}
    )

    # ---- stars.csv
    with open(os.path.join(OUT, "stars.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["id", "ra_deg", "dec_deg", "magnitude", "names"])
        for oid, ra, dec, mag, name_tokens in stars:
            w.writerow([oid, repr(ra), repr(dec), repr(mag), "|".join(name_tokens)])

    # ---- dso.csv
    with open(os.path.join(OUT, "dso.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(
            ["id", "ra_deg", "dec_deg", "magnitude", "type", "size_arcmin",
             "designations", "names"]
        )
        for d in dsos:
            w.writerow(
                [
                    d["id"], repr(d["ra"]), repr(d["dec"]),
                    "" if d["magnitude"] is None else repr(d["magnitude"]),
                    d["type"],
                    "" if d["size_arcmin"] is None else repr(d["size_arcmin"]),
                    "|".join(d["designations"]), "|".join(d["names"]),
                ]
            )

    # ---- constellations/iau.json
    iau = {
        "culture": "iau",
        "constellations": [
            OrderedDict(
                [
                    ("id", f"constellation/{c['key']}"),
                    ("ra", c["ra"]),
                    ("dec", c["dec"]),
                    ("label_ra", c["label_ra"]),
                    ("label_dec", c["label_dec"]),
                    ("strokes", c["strokes"]),
                ]
            )
            for c in sorted(consts, key=lambda c: c["key"])
        ],
    }
    with open(os.path.join(OUT, "constellations/iau.json"), "w", encoding="utf-8") as f:
        json.dump(iau, f, indent=1)
        f.write("\n")

    # ---- objects.json: registry of non-positional objects + overlay metadata
    objects = OrderedDict()
    for key, entry in sorted(info.items(), key=lambda kv: info_id[kv[0]]):
        oid = info_id[key]
        rec = OrderedDict()
        if oid not in positional_ids:
            rec["type"] = INFO_TYPE_TO_CODE[entry["type"]]
        if entry.get("parentObjectId"):
            rec["parent"] = info_id[entry["parentObjectId"]]
        if entry.get("imageKey"):
            rec["image_ref"] = entry["imageKey"]
        if entry.get("magnitude") is not None and oid not in positional_ids:
            rec["magnitude"] = float(entry["magnitude"])
        if entry.get("seeAlso"):
            rec["see_also"] = [info_id[k] for k in entry["seeAlso"]]
        if rec:
            objects[oid] = rec
    with open(os.path.join(OUT, "objects.json"), "w", encoding="utf-8") as f:
        json.dump({"objects": objects}, f, indent=1)
        f.write("\n")

    # ---- names/*.csv
    # name rows: (object_id, name, is_primary) per locale; universal '' rows for
    # designations. Locale rows identical to a universal designation are dropped;
    # non-en rows identical to the en row are dropped (the fallback chain covers them).
    universal = {}  # object_id -> [name]

    def add_universal(oid, name):
        universal.setdefault(oid, [])
        if name not in universal[oid]:
            universal[oid].append(name)

    # Star names: tokens with a strings key are localizable names; tokens without
    # one are catalog designations ("268 G. Cet", "p Eridani") -> universal.
    en = name_strings["en"]
    obj_name_keys = {}  # object_id -> [string keys] (primary first)
    for oid, ra, dec, mag, name_tokens in stars:
        keys = []
        for t in name_tokens:
            k = rkeys(t)[0] if rkeys(t) else None
            if k and k in en:
                keys.append(k)
            else:
                add_universal(oid, t)
        if keys:
            obj_name_keys[oid] = keys

    for d in dsos:
        oid = d["id"]
        for t in d["designations"]:
            add_universal(oid, t)
        keys = []
        for t in d["names"]:
            k = rkeys(t)[0] if rkeys(t) else None
            if k and k in en:
                keys.append(k)
            else:
                add_universal(oid, t)
        if keys:
            obj_name_keys[oid] = keys

    for c in consts:
        oid = f"constellation/{c['key']}"
        obj_name_keys[oid] = [c["key"]]

    # object_info nameKey is the primary name; alternateNameKeys are extras.
    for key, entry in info.items():
        oid = info_id[key]
        keys = obj_name_keys.setdefault(oid, [])
        nk = entry["nameKey"]
        if nk in keys:
            keys.remove(nk)
        keys.insert(0, nk)
        for alt in entry.get("alternateNameKeys", []):
            if alt not in keys:
                keys.append(alt)

    # Per locale, an object's candidate rows are its keys' resolved texts minus any
    # that duplicate a universal designation (those match every locale already).
    # The first survivor is that locale's primary. A non-en locale keeps rows only
    # for objects where some translation differs from en, and then keeps the FULL
    # row set with per-key en fallback — a partial set would shadow-shift which
    # name the locale fallback chain displays (de's "Hundsstern" must not displace
    # "Sirius" just because de never translated the primary name).
    def locale_rows(strings, oid, fallback=None):
        rows = []
        for k in obj_name_keys.get(oid, []):
            text = strings.get(k) or (fallback.get(k) if fallback else None)
            if text and text not in universal.get(oid, []):
                rows.append((k, text))
        return rows

    objects_with_en_rows = set()
    for tag in sorted(name_strings):
        strings = name_strings[tag]
        rows = []
        for oid in sorted(obj_name_keys):
            if tag == "en":
                cand = locale_rows(strings, oid)
            else:
                cand = locale_rows(strings, oid, fallback=name_strings["en"])
                if cand == locale_rows(name_strings["en"], oid):
                    continue
            if not cand:
                continue
            if tag == "en":
                objects_with_en_rows.add(oid)
            for i, (k, text) in enumerate(cand):
                rows.append((oid, text, i == 0))
        if not rows:
            continue
        with open(os.path.join(OUT, "names", f"{tag}.csv"), "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f, lineterminator="\n")
            w.writerow(["object_id", "name", "is_primary"])
            for oid, text, prim in rows:
                w.writerow([oid, text, 1 if prim else 0])

    # Universal designations; the first is promoted to primary when the object has
    # no surviving en rows (e.g. M2, whose only "name" is its designation).
    with open(os.path.join(OUT, "names", "universal.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["object_id", "name", "is_primary"])
        for oid in sorted(universal):
            for i, name in enumerate(universal[oid]):
                prim = i == 0 and oid not in objects_with_en_rows
                w.writerow([oid, name, 1 if prim else 0])

    # ---- info_cards/*.json
    # A locale gets a card only if its own resources localize the description or
    # fun fact (whole-row fallback, D33); non-localized fields are copied to every
    # locale's card so fallback rows stay self-contained.
    for tag in sorted(card_strings):
        strings = card_strings[tag]
        cards = OrderedDict()
        for key, entry in sorted(info.items(), key=lambda kv: info_id[kv[0]]):
            desc = strings.get(entry["descriptionKey"])
            fun = strings.get(entry["funFactKey"])
            if not desc and not fun:
                continue
            card = OrderedDict()
            if desc:
                card["description"] = desc
            if fun:
                card["fun_fact"] = fun
            for field, k in (
                ("distance", "distanceKey"), ("size", "sizeKey"), ("mass", "massKey"),
            ):
                if entry.get(k) and strings.get(entry[k]):
                    card[field] = strings[entry[k]]
            if entry.get("spectralClass"):
                card["spectral_class"] = entry["spectralClass"]
            if entry.get("imageCredit"):
                card["image_credit"] = entry["imageCredit"]
            if entry.get("searchSubtext"):
                card["search_subtext"] = entry["searchSubtext"]
            cards[info_id[key]] = card
        if not cards:
            continue
        with open(os.path.join(OUT, "info_cards", f"{tag}.json"), "w", encoding="utf-8") as f:
            json.dump({"cards": cards}, f, indent=1, ensure_ascii=False)
            f.write("\n")

    print(f"stars: {len(stars)}  dsos: {len(dsos)}  constellations: {len(consts)}")
    print(f"objects.json entries: {len(objects)}")
    print(f"name locales: {len(name_strings)}  card locales: {len(card_strings)}")


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Augment source-data star names with IAU proper names and Bayer/Flamsteed designations.

One-off data-preparation script (harvest.py precedent): reads the checked-in raw catalogs in
data/ (IAU WGSN Catalog of Star Names, CC BY 4.0; Yale Bright Star Catalogue BSC5 column
extract, public-domain catalog data), matches them against source-data/stars.csv, and rewrites
source-data/names/en.csv and source-data/names/universal.csv in place. The diff is reviewed
and committed; the script is not part of the build.

Name placement (see source-data/README.md):
  - IAU proper name        -> en.csv, is_primary=1 (existing primary names win; conflicts logged)
  - Bayer spelled out      -> en.csv, is_primary=0   e.g. "Alpha Canis Majoris"
  - Bayer compact          -> universal.csv, 0       e.g. "α CMa"
  - Flamsteed              -> universal.csv, 0       e.g. "9 CMa"

stars.csv has no HIP/HD ids, so BSC5 records are matched positionally: nearest neighbour
within MATCH_RADIUS_DEG and |Δmag| <= MATCH_MAX_DMAG, rejected as ambiguous when a second
candidate lies within AMBIGUITY_FACTOR of the best distance. The radius is empirical:
stars.csv coordinates are rounded (many rows to 0.1 degree), and the distance from a BSC5
record to its true counterpart is always under ~6 arcmin while the nearest wrong
magnitude-compatible star is beyond 18 arcmin — a clean gap. WGSN rows join BSC5 by HR number (exact) and fall back to a positional match that
prefers the *brightest* in-tolerance candidate — a proper name designates the primary of a
close pair, which nearest-neighbour ambiguity would wrongly reject. A match report is printed
to stdout — review it whenever the script is re-run.

Usage: python3 augment_names.py [--repo-root ../..]
"""

from __future__ import annotations

import argparse
import csv
import math
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

MATCH_RADIUS_DEG = 0.105  # ~6.3 arcmin; see the module docstring for the empirical basis
MATCH_MAX_DMAG = 0.7
AMBIGUITY_FACTOR = 2.0

GREEK = {
    "Alp": ("Alpha", "α"), "Bet": ("Beta", "β"), "Gam": ("Gamma", "γ"),
    "Del": ("Delta", "δ"), "Eps": ("Epsilon", "ε"), "Zet": ("Zeta", "ζ"),
    "Eta": ("Eta", "η"), "The": ("Theta", "θ"), "Iot": ("Iota", "ι"),
    "Kap": ("Kappa", "κ"), "Lam": ("Lambda", "λ"), "Mu": ("Mu", "μ"),
    "Nu": ("Nu", "ν"), "Xi": ("Xi", "ξ"), "Omi": ("Omicron", "ο"),
    "Pi": ("Pi", "π"), "Rho": ("Rho", "ρ"), "Sig": ("Sigma", "σ"),
    "Tau": ("Tau", "τ"), "Ups": ("Upsilon", "υ"), "Phi": ("Phi", "φ"),
    "Chi": ("Chi", "χ"), "Psi": ("Psi", "ψ"), "Ome": ("Omega", "ω"),
}

CONSTELLATION_GENITIVE = {
    "And": "Andromedae", "Ant": "Antliae", "Aps": "Apodis", "Aqr": "Aquarii",
    "Aql": "Aquilae", "Ara": "Arae", "Ari": "Arietis", "Aur": "Aurigae",
    "Boo": "Boötis", "Cae": "Caeli", "Cam": "Camelopardalis", "Cnc": "Cancri",
    "CVn": "Canum Venaticorum", "CMa": "Canis Majoris", "CMi": "Canis Minoris",
    "Cap": "Capricorni", "Car": "Carinae", "Cas": "Cassiopeiae", "Cen": "Centauri",
    "Cep": "Cephei", "Cet": "Ceti", "Cha": "Chamaeleontis", "Cir": "Circini",
    "Col": "Columbae", "Com": "Comae Berenices", "CrA": "Coronae Australis",
    "CrB": "Coronae Borealis", "Crv": "Corvi", "Crt": "Crateris", "Cru": "Crucis",
    "Cyg": "Cygni", "Del": "Delphini", "Dor": "Doradus", "Dra": "Draconis",
    "Equ": "Equulei", "Eri": "Eridani", "For": "Fornacis", "Gem": "Geminorum",
    "Gru": "Gruis", "Her": "Herculis", "Hor": "Horologii", "Hya": "Hydrae",
    "Hyi": "Hydri", "Ind": "Indi", "Lac": "Lacertae", "Leo": "Leonis",
    "LMi": "Leonis Minoris", "Lep": "Leporis", "Lib": "Librae", "Lup": "Lupi",
    "Lyn": "Lyncis", "Lyr": "Lyrae", "Men": "Mensae", "Mic": "Microscopii",
    "Mon": "Monocerotis", "Mus": "Muscae", "Nor": "Normae", "Oct": "Octantis",
    "Oph": "Ophiuchi", "Ori": "Orionis", "Pav": "Pavonis", "Peg": "Pegasi",
    "Per": "Persei", "Phe": "Phoenicis", "Pic": "Pictoris", "Psc": "Piscium",
    "PsA": "Piscis Austrini", "Pup": "Puppis", "Pyx": "Pyxidis", "Ret": "Reticuli",
    "Sge": "Sagittae", "Sgr": "Sagittarii", "Sco": "Scorpii", "Scl": "Sculptoris",
    "Sct": "Scuti", "Ser": "Serpentis", "Sex": "Sextantis", "Tau": "Tauri",
    "Tel": "Telescopii", "Tri": "Trianguli", "TrA": "Trianguli Australis",
    "Tuc": "Tucanae", "UMa": "Ursae Majoris", "UMi": "Ursae Minoris",
    "Vel": "Velorum", "Vir": "Virginis", "Vol": "Volantis", "Vul": "Vulpeculae",
}


@dataclass
class Star:
    object_id: str
    ra: float
    dec: float
    mag: float


@dataclass
class BscRecord:
    hr: int
    flamsteed: str  # "9" or ""
    bayer: str  # BSC5 abbreviation, "" when none
    superscript: str  # "1".."9" or ""
    constellation: str  # "CMa" etc, "" when none
    ra: float
    dec: float
    mag: float


@dataclass
class WgsnRecord:
    name: str
    hr: int | None
    ra: float
    dec: float
    mag: float | None


@dataclass
class Report:
    matched: int = 0
    unmatched: list = field(default_factory=list)
    ambiguous: list = field(default_factory=list)
    collisions: list = field(default_factory=list)
    conflicts: list = field(default_factory=list)
    duplicate_names: list = field(default_factory=list)
    collapsed: list = field(default_factory=list)
    manual_fixes: list = field(default_factory=list)
    unchecked_matches: list = field(default_factory=list)


def read_stars(path: Path) -> list[Star]:
    with path.open(encoding="utf-8") as f:
        return [
            Star(r["id"], float(r["ra_deg"]), float(r["dec_deg"]), float(r["magnitude"]))
            for r in csv.DictReader(f)
        ]


def read_bsc5(path: Path) -> list[BscRecord]:
    """Parse the checked-in column extract (see README.md for the cut(1) command).

    1-indexed columns: 1-4 HR, 5-7 Flamsteed, 8-10 Bayer, 11 superscript, 12-14
    constellation, 15-20 HD, 21-28 RA J2000 (HHMMSS.S), 29-35 Dec J2000 (+DDMMSS),
    36-40 Vmag.
    """
    records = []
    for line in path.read_text(encoding="utf-8").splitlines():
        ra_field = line[20:28].strip()
        mag_field = line[35:40].strip()
        if not ra_field or not mag_field:
            continue  # deleted/nova entries carry no position or magnitude
        ra = (
            int(line[20:22]) + int(line[22:24]) / 60 + float(line[24:28]) / 3600
        ) * 15.0
        dec = int(line[29:31]) + int(line[31:33]) / 60 + int(line[33:35]) / 3600
        if line[28] == "-":
            dec = -dec
        records.append(
            BscRecord(
                hr=int(line[0:4]),
                flamsteed=line[4:7].strip(),
                bayer=line[7:10].strip(),
                superscript=line[10:11].strip(),
                constellation=line[11:14].strip(),
                ra=ra,
                dec=dec,
                mag=float(mag_field),
            )
        )
    return records


def read_wgsn(path: Path) -> list[WgsnRecord]:
    """Parse IAU-CSN.txt. The name is a fixed-width slice (names may contain spaces); the
    columns after the designation are single space-separated tokens ending
    [mag, band, HIP, HD, RA, Dec, date, notes?]."""
    records = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith(("#", "$")):
            continue
        name = line[0:18].strip()
        designation = line[36:49].strip()
        hr = None
        if designation.startswith("HR "):
            hr = int(designation[3:])
        tokens = line[49:].split()
        # From the end: [date, Dec, RA, HD, HIP, band, mag, ...] with an optional trailing
        # notes token after the date.
        if tokens and not tokens[-1][0].isdigit():
            tokens = tokens[:-1]
        try:
            dec = float(tokens[-2])
            ra = float(tokens[-3])
            # Magnitude is "_" for objects without one (Geminga, Lich); the matcher
            # accepts mag=None, so keep the row rather than skipping it.
            mag = None if tokens[-7] == "_" else float(tokens[-7])
        except (IndexError, ValueError):
            print(f"WARNING: cannot parse WGSN row, skipped: {line!r}", file=sys.stderr)
            continue
        records.append(WgsnRecord(name=name, hr=hr, ra=ra, dec=dec, mag=mag))
    return records


def angular_distance_deg(ra1: float, dec1: float, ra2: float, dec2: float) -> float:
    """Small-angle approximation with RA foreshortening — fine at 72-arcsec scales."""
    dra = abs(ra1 - ra2)
    if dra > 180:
        dra = 360 - dra
    dra *= math.cos(math.radians((dec1 + dec2) / 2))
    return math.hypot(dra, dec1 - dec2)


class PositionalMatcher:
    """Nearest-neighbour star lookup bucketed by integer RA degree."""

    def __init__(self, stars: list[Star]):
        self.buckets: dict[int, list[Star]] = defaultdict(list)
        for s in stars:
            self.buckets[int(s.ra) % 360].append(s)

    def candidates(self, ra: float, dec: float) -> list[Star]:
        center = int(ra)
        return [
            s
            for b in (center - 1, center, center + 1)
            for s in self.buckets[b % 360]
        ]

    def match(
        self,
        ra: float,
        dec: float,
        mag: float | None,
        label: str,
        report: Report,
        prefer_brightest: bool = False,
    ) -> tuple[float, Star] | None:
        """Returns (angular distance in degrees, matched star), or None."""
        scored = sorted(
            (
                (angular_distance_deg(ra, dec, s.ra, s.dec), s)
                for s in self.candidates(ra, dec)
            ),
            key=lambda t: t[0],
        )
        # A star of the wrong brightness is not a candidate at all (so a faint neighbour
        # cannot make a bright star's match "ambiguous").
        close = [
            t
            for t in scored
            if t[0] <= MATCH_RADIUS_DEG
            and (mag is None or abs(t[1].mag - mag) <= MATCH_MAX_DMAG)
        ]
        if not close:
            report.unmatched.append(label)
            return None
        if prefer_brightest:
            return min(close, key=lambda t: t[1].mag)
        best_dist, best = close[0]
        if len(close) > 1 and close[1][0] <= best_dist * AMBIGUITY_FACTOR:
            report.ambiguous.append(
                f"{label}: {best.object_id} at {best_dist * 3600:.1f}\" vs "
                f"{close[1][1].object_id} at {close[1][0] * 3600:.1f}\""
            )
            return None
        return close[0]


def bayer_names(rec: BscRecord) -> tuple[str | None, str | None]:
    """(spelled-out English, compact universal) Bayer forms, or Nones."""
    # The BSC5 name field occasionally carries non-Bayer catalog codes in the Bayer
    # columns ("GC", "VA"); those yield no designation.
    if rec.bayer not in GREEK or rec.constellation not in CONSTELLATION_GENITIVE:
        return None, None
    genitive = CONSTELLATION_GENITIVE[rec.constellation]
    full, letter = GREEK[rec.bayer]
    sup = rec.superscript
    return f"{full}{sup} {genitive}", f"{letter}{sup} {rec.constellation}"


def flamsteed_name(rec: BscRecord) -> str | None:
    if not rec.flamsteed or not rec.constellation:
        return None
    return f"{rec.flamsteed} {rec.constellation}"


def read_rejections(path: Path) -> set[str]:
    """Human-reviewed matches judged wrong (one 'Name -> object_id' per line)."""
    if not path.exists():
        return set()
    return {
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith("#")
    }


def read_names(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as f:
        return list(csv.DictReader(f))


def write_names(path: Path, rows: list[dict]) -> None:
    # Sorted by object_id then primary-first then name: keeps the whole file in the existing
    # sorted-by-object convention and makes re-runs idempotent.
    rows.sort(key=lambda r: (r["object_id"], r["is_primary"] != "1", r["name"]))
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(
            f, fieldnames=["object_id", "name", "is_primary"], lineterminator="\n"
        )
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parent.parent.parent,
        help="stardroid-v2 module root (contains source-data/)",
    )
    args = parser.parse_args()
    source_data = args.repo_root / "source-data"
    data = Path(__file__).resolve().parent / "data"

    stars = read_stars(source_data / "stars.csv")
    bsc = read_bsc5(data / "bsc5_extract.txt")
    wgsn = read_wgsn(data / "IAU-CSN.txt")
    en_rows = read_names(source_data / "names" / "en.csv")
    universal_rows = read_names(source_data / "names" / "universal.csv")
    rejections = read_rejections(data / "rejected_matches.txt")

    report = Report()
    matcher = PositionalMatcher(stars)

    # BSC5 -> star. Resolve reverse collisions (two BSC5 records on one star) by keeping the
    # closer record; doubles merged in the source catalog must not stack designations.
    star_by_id = {s.object_id: s for s in stars}
    bsc_match: dict[int, Star] = {}
    claimed: dict[str, tuple[float, BscRecord]] = {}
    for rec in bsc:
        match = matcher.match(rec.ra, rec.dec, rec.mag, f"HR {rec.hr}", report)
        if match is None:
            continue
        dist, star = match
        prev = claimed.get(star.object_id)
        if prev is not None:
            keep_new = dist < prev[0]
            loser = prev[1] if keep_new else rec
            report.collisions.append(
                f"HR {prev[1].hr} and HR {rec.hr} both match {star.object_id}; "
                f"dropped HR {loser.hr}"
            )
            if not keep_new:
                continue
            del bsc_match[prev[1].hr]
        claimed[star.object_id] = (dist, rec)
        bsc_match[rec.hr] = star
    report.matched = len(bsc_match)

    bsc_by_hr = {r.hr: r for r in bsc}

    # Existing names index: (object_id, normalized name) and per-object primary flags.
    def norm(name: str) -> str:
        return " ".join(name.split()).lower()

    existing = {(r["object_id"], norm(r["name"])) for r in en_rows + universal_rows}
    has_primary_en = {r["object_id"] for r in en_rows if r["is_primary"] == "1"}
    primary_en_name = {
        r["object_id"]: r["name"] for r in en_rows if r["is_primary"] == "1"
    }

    new_en: list[dict] = []
    new_universal: list[dict] = []

    def add(rows: list[dict], object_id: str, name: str, primary: bool) -> None:
        key = (object_id, norm(name))
        if key in existing:
            return
        existing.add(key)
        rows.append(
            {"object_id": object_id, "name": name, "is_primary": "1" if primary else "0"}
        )

    # IAU proper names: HR join first, positional fallback.
    for rec in wgsn:
        star = None
        if rec.hr is not None:
            star = bsc_match.get(rec.hr)
        if star is None:
            fallback = matcher.match(
                rec.ra, rec.dec, rec.mag, f"WGSN {rec.name}", report, prefer_brightest=True
            )
            star = fallback[1] if fallback is not None else None
            if star is not None and rec.mag is None:
                # No |Δmag| gate applied and prefer_brightest skips ambiguity rejection, so
                # a magnitude-less object could grab any star within the radius. Such a
                # match is never auto-written: it is reported, the run fails, and a human
                # vets it either by adding the row to the CSV by hand (re-runs then stay
                # quiet via `existing`) or, if the match is wrong, by listing it in
                # data/rejected_matches.txt.
                if f"{rec.name} -> {star.object_id}" in rejections:
                    continue
                if (star.object_id, norm(rec.name)) not in existing:
                    report.unchecked_matches.append(
                        f"WGSN {rec.name} -> {star.object_id} (no magnitude check; add "
                        f"the row by hand if right, or add '{rec.name} -> "
                        f"{star.object_id}' to data/rejected_matches.txt if wrong)"
                    )
                    continue
        if star is None:
            continue
        if star.object_id in has_primary_en:
            if norm(primary_en_name[star.object_id]) != norm(rec.name):
                report.conflicts.append(
                    f"{star.object_id}: keeps '{primary_en_name[star.object_id]}', "
                    f"WGSN '{rec.name}' added as secondary"
                )
                add(new_en, star.object_id, rec.name, primary=False)
        else:
            has_primary_en.add(star.object_id)
            primary_en_name[star.object_id] = rec.name
            add(new_en, star.object_id, rec.name, primary=True)

    # Bayer / Flamsteed designations for every matched star.
    for hr, star in sorted(bsc_match.items()):
        rec = bsc_by_hr[hr]
        spelled, compact = bayer_names(rec)
        if spelled:
            add(new_en, star.object_id, spelled, primary=False)
            add(new_universal, star.object_id, compact, primary=False)
        flamsteed = flamsteed_name(rec)
        if flamsteed:
            add(new_universal, star.object_id, flamsteed, primary=False)

    # A name string must map to exactly one star per locale file. Components of a double
    # share a designation when BSC5 gives them no superscripts (Castor A/B are both
    # "Alp Gem"); the designation conventionally means the system's primary, so keep the
    # brightest colliding star and drop the rest. Near-equal brightness drops all copies.
    # The pass covers pre-existing rows too, so re-running the script repairs committed
    # duplicates; only star rows participate (other namespaces have no magnitude to rank
    # by). Every drop is listed in the report for review.
    merged_en = en_rows + new_en
    merged_universal = universal_rows + new_universal
    for rows in (merged_en, merged_universal):
        by_name = defaultdict(list)
        for r in rows:
            if r["object_id"] in star_by_id:
                by_name[norm(r["name"])].append(r)
        dropped: set[int] = set()
        for copies in by_name.values():
            # Collapse duplicates of the same object first (identical committed rows must
            # repair to one copy, not zero); a primary row wins over a secondary one. Rows
            # that only match under norm() (casing/spacing differences) are flagged for a
            # human — the script cannot know which text was the intended correction.
            per_object: dict[str, list[dict]] = defaultdict(list)
            for r in copies:
                per_object[r["object_id"]].append(r)
            survivors = []
            for object_id, group in per_object.items():
                group.sort(key=lambda r: r["is_primary"] != "1")
                keep_row, rest = group[0], group[1:]
                if rest:
                    dropped.update(id(r) for r in rest)
                    report.collapsed.append(
                        f"'{keep_row['name']}' listed {len(group)} times on {object_id}; "
                        "collapsed to one row"
                    )
                    texts = sorted({r["name"] for r in group})
                    if len(texts) > 1:
                        report.manual_fixes.append(
                            f"{object_id}: rows {texts} normalize identically; kept "
                            f"'{keep_row['name']}' — verify the intended casing/spacing"
                        )
                survivors.append(keep_row)
            copies = survivors
            if len(copies) <= 1:
                continue
            # A curated primary display name is never auto-dropped: it wins the collision.
            # When two primaries collide, secondary copies on further stars are still
            # dropped, and the primary-vs-primary conflict fails the run for a human.
            copies.sort(
                key=lambda r: (r["is_primary"] != "1", star_by_id[r["object_id"]].mag)
            )
            primaries = [r for r in copies if r["is_primary"] == "1"]
            if len(primaries) >= 2:
                # copies is sorted primary-first, so the tail is exactly the secondaries.
                secondaries = copies[len(primaries):]
                if secondaries:
                    dropped.update(id(r) for r in secondaries)
                    report.duplicate_names.append(
                        f"'{copies[0]['name']}' -> dropped secondary copies on "
                        f"{[r['object_id'] for r in secondaries]}"
                    )
                report.manual_fixes.append(
                    f"'{copies[0]['name']}' is a primary name on multiple stars "
                    f"{[r['object_id'] for r in primaries]}; resolve by hand"
                )
                continue
            mags = [star_by_id[r["object_id"]].mag for r in copies]
            if copies[0]["is_primary"] == "1":
                keep = copies[0]
            else:
                keep = copies[0] if mags[1] - mags[0] > 0.01 else None
            report.duplicate_names.append(
                f"'{copies[0]['name']}' -> {[r['object_id'] for r in copies]}; "
                + (
                    f"kept {keep['object_id']} "
                    + ("(primary)" if keep["is_primary"] == "1" else "(brightest)")
                    if keep
                    else "all dropped"
                )
            )
            dropped.update(id(r) for r in copies if r is not keep)
        rows[:] = [r for r in rows if id(r) not in dropped]

    kept = {id(r) for r in merged_en} | {id(r) for r in merged_universal}
    new_en = [r for r in new_en if id(r) in kept]
    new_universal = [r for r in new_universal if id(r) in kept]

    print(f"BSC5 records matched: {report.matched}/{len(bsc)}")
    print(f"New en.csv rows: {len(new_en)} (primary: "
          f"{sum(1 for r in new_en if r['is_primary'] == '1')})")
    print(f"New universal.csv rows: {len(new_universal)}")
    for title, items in (
        ("Ambiguous (no designation assigned)", report.ambiguous),
        ("Reverse collisions", report.collisions),
        ("Primary-name conflicts (existing kept)", report.conflicts),
        ("Duplicate names (dropped)", report.duplicate_names),
        ("Same-object duplicates (collapsed)", report.collapsed),
        ("Name conflicts needing manual fix", report.manual_fixes),
        ("Magnitude-unchecked positional matches (review)", report.unchecked_matches),
    ):
        print(f"\n{title}: {len(items)}")
        for item in items:
            print(f"  {item}")
    print(f"\nUnmatched BSC5/WGSN entries: {len(report.unmatched)} (expected: stars fainter "
          "than the source catalog, novae, far-south gaps)")

    # All-or-nothing: while anything needs a human decision, leave the CSVs untouched so
    # the conflicting rows (the evidence) stay in place and every re-run fails the same way.
    blocking = len(report.manual_fixes) + len(report.unchecked_matches)
    if blocking:
        sys.exit(
            f"{blocking} item(s) need manual resolution (see the report above); "
            "no files were written."
        )
    write_names(source_data / "names" / "en.csv", merged_en)
    write_names(source_data / "names" / "universal.csv", merged_universal)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Reconstruct missing `tm` source_snapshot baselines from git history.

`tm` decides whether a translation is stale by comparing the current English source with
the English it recorded in `.tmstate.toml` (`source_snapshot`) when it last translated that
key. Translations older than that feature (salvaged from v1, bulk runs before 2.0.6) have no
snapshot, and `tm` now reports every one of them as possibly stale: ~38.8k keys across the
28 core locales. Almost all of them are fine. This script works out which are, without
calling an LLM.

For every translated key with no snapshot it walks the history of the translation files
(the whole commit DAG, not just first-parent, so a translation made on a branch is judged
against the English on that branch) and finds the commit(s) where the locale's *current*
value first appeared. The English at those commits is what the value was translated from:

  current   English then == English now      -> snapshot = English now (not stale)
  drifted   English then != English now      -> snapshot = English then (stale, with a
                                                real old -> new diff for `tm translate`)
  unknown   the key had no English then      -> left without a snapshot (still flagged)

Keys `tm` keeps as identical to English (tm:omitted / same_as_parent) are tracked as that
flag rather than their text, so an omission made against older English reads as drifted
instead of silently following every English change.

With --verify, keys that already have a snapshot are reconstructed too and compared with
what `tm` recorded — a check on the method itself, and a way to spot snapshots that no
longer match the history.

History is given as one or more --segment REPO SUBDIR REVS, oldest first, where SUBDIR is
the tm project root inside REPO. Each segment's first commits are grafted onto the previous
segment's tip. v2 was developed in a private repo before it was published here in #965
(28955ab7), whose translation trees are byte-identical to the private repo's 5901a345, so
the full history is the two joined:

    python tools/tm-baseline-backfill/backfill.py \\
        --segment $PRIVATE_REPO stardroid-v2 5901a345 \\
        --segment .. stardroid-v2 28955ab7^..HEAD \\
        --write

The public segment alone is not enough: 55 translations were already stale when v2 was
published, and without the earlier history they look as if they were made at #965.

It imports `translationmanager`, so run it with the interpreter `tm` is installed into.
Without --write it only reports. Run from the stardroid-v2 module root.
"""

from __future__ import annotations

import argparse
import io
import shutil
import subprocess
import sys
import tarfile
import tempfile
import tomllib
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

import tomli_w
from translationmanager.config import Settings
from translationmanager.pipeline import (
    build_sources,
    collect_missing,
    documents_for,
    resolved_parent_values,
)
from translationmanager.sources.state import TmState

# Stands in for "identical to English" so the flag, not the text it resolves to, is tracked.
SAME = "\x00same_as_parent"
STATE_FILE = ".tmstate.toml"
WORKTREE = "WORKTREE"


@dataclass
class Commit:
    oid: str
    repo: Path
    subdir: str
    parents: list[str] = field(default_factory=list)


def git(repo: Path, *args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(repo), *args], check=True, capture_output=True, text=True
    ).stdout


def git_bytes(repo: Path, *args: str) -> bytes:
    return subprocess.run(
        ["git", "-C", str(repo), *args], check=True, capture_output=True
    ).stdout


def load_segments(segments: list[list[str]], source_paths: list[str]) -> dict[str, Commit]:
    """All commits touching the tm sources, parents rewritten and segments grafted."""
    commits: dict[str, Commit] = {}
    previous_tip: str | None = None
    for repo_arg, subdir, revs in segments:
        repo = Path(repo_arg).resolve()
        paths = [f"{subdir}/{p}" for p in [*source_paths, STATE_FILE]]
        out = git(repo, "rev-list", "--parents", "--topo-order", "--reverse", revs, "--", *paths)
        segment: dict[str, Commit] = {}
        for line in out.splitlines():
            oid, *parents = line.split()
            segment[oid] = Commit(oid, repo, subdir, parents)
        for c in segment.values():
            inside = [p for p in c.parents if p in segment]
            if not inside and previous_tip:
                inside = [previous_tip]
            c.parents = inside
        if not segment:
            raise SystemExit(f"segment {repo_arg} {revs} does not touch the tm sources")
        commits.update(segment)
        # --topo-order --reverse lists the newest commit last.
        previous_tip = list(segment)[-1]
    return commits


class Reader:
    """Per-(source, doc, locale) values and resolved English, at any commit or the worktree.

    Each source is materialised on its own, keyed by its tree hash (plus .tmstate.toml's,
    for formats that keep flags there), so a commit that only touched strings.xml doesn't
    re-read 6 MB of info cards.
    """

    def __init__(self, project: Path, locales: list[str]):
        self.project = project
        self.locales = locales
        with (project / ".tmconfig.toml").open("rb") as f:
            self.config = tomllib.load(f)
        self.sources = {s["name"]: s for s in self.config["sources"]}
        self.tmp = Path(tempfile.mkdtemp(prefix="tm-backfill-"))
        self.cache: dict[tuple, dict] = {}

    def at_commit(self, c: Commit) -> dict[str, dict]:
        paths = [f"{c.subdir}/{s['path']}" for s in self.sources.values()]
        paths.append(f"{c.subdir}/{STATE_FILE}")
        oids = {}
        for line in git(c.repo, "ls-tree", c.oid, "--", *paths).splitlines():
            meta, path = line.split("\t", 1)
            oids[path] = meta.split()[2]
        state_oid = oids.get(f"{c.subdir}/{STATE_FILE}")
        result = {}
        for name, source in self.sources.items():
            oid = oids.get(f"{c.subdir}/{source['path']}")
            if oid is None:
                continue
            key = (name, oid, state_oid)
            if key not in self.cache:
                root = self.tmp / f"{name}-{oid[:12]}-{(state_oid or 'none')[:12]}"
                self._materialise(c.repo, root, source, oid, state_oid)
                self.cache[key] = self._read(root, name)
            result[name] = self.cache[key]
        return result

    def at_worktree(self) -> dict[str, dict]:
        return {name: self._read(self.project, name, translated=True) for name in self.sources}

    def _materialise(self, repo, root, source, oid, state_oid) -> None:
        dest = root / source["path"]
        if git(repo, "cat-file", "-t", oid).strip() == "tree":
            dest.mkdir(parents=True)
            with tarfile.open(fileobj=io.BytesIO(git_bytes(repo, "archive", oid))) as tar:
                tar.extractall(dest, filter="data")
        else:
            dest.parent.mkdir(parents=True)
            dest.write_bytes(git_bytes(repo, "cat-file", "blob", oid))
        if state_oid:
            (root / STATE_FILE).write_bytes(git_bytes(repo, "cat-file", "blob", state_oid))
        config = {k: v for k, v in self.config.items() if k != "sources"}
        config["sources"] = [source]
        # The glossary is only read when translating; a missing path is harmless.
        with (root / ".tmconfig.toml").open("wb") as f:
            tomli_w.dump(config, f)

    def _read(self, root: Path, name: str, translated: bool = False) -> dict:
        """{(doc, locale): (values, english, translated_keys)} for one source.

        translated_keys is what `tm` counts as translated (the keys stale_keys() checks),
        only needed for the worktree.
        """
        (ctx,) = build_sources(Settings(project_root=root), source_filter=name)
        out = {}
        for doc in documents_for(ctx):
            for loc in self.locales:
                values = {
                    u.key: SAME if u.same_as_parent else u.value
                    for u in ctx.read_locale(doc, loc)
                }
                english = resolved_parent_values(ctx, doc, loc)
                keys = set()
                if translated:
                    keys = set(english) - {u.key for u in collect_missing(ctx, doc, loc).units}
                out[(doc, loc)] = (values, english, keys)
        return out


def introducing_commits(commits, data, source, doc, loc, key, value) -> list[str]:
    """Commits where `value` entered the history that reaches the worktree unchanged."""

    def value_at(oid):
        entry = data[oid].get(source, {}).get((doc, loc))
        return entry[0].get(key) if entry else None

    found, seen, stack = [], set(), [WORKTREE]
    while stack:
        oid = stack.pop()
        if oid in seen:
            continue
        seen.add(oid)
        carriers = [p for p in commits[oid].parents if value_at(p) == value]
        if carriers:
            stack.extend(carriers)
        else:
            found.append(oid)
    return found


def clip(value: str | None, width: int = 70) -> str:
    text = repr(value)
    return text if len(text) <= width else text[: width - 3] + "..."


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument(
        "--segment", nargs=3, action="append", required=True,
        metavar=("REPO", "SUBDIR", "REVS"), help="history segment, oldest first",
    )
    parser.add_argument("--project", default=".", help="tm project root (default: .)")
    parser.add_argument("--write", action="store_true", help="write snapshots to .tmstate.toml")
    parser.add_argument("--verify", action="store_true", help="also check existing snapshots")
    parser.add_argument("--examples", type=int, default=0, help="print N drifted examples")
    args = parser.parse_args()

    project = Path(args.project).resolve()
    settings = Settings(project_root=project)
    locales = settings.project_config.primary_languages
    reader = Reader(project, locales)

    commits = load_segments(args.segment, [s["path"] for s in reader.sources.values()])
    tip = list(commits)[-1]
    commits[WORKTREE] = Commit(WORKTREE, project, "", [tip])
    print(f"{len(commits) - 1} commits in history", file=sys.stderr)

    data = {oid: reader.at_commit(c) for oid, c in commits.items() if oid != WORKTREE}
    data[WORKTREE] = reader.at_worktree()
    shutil.rmtree(reader.tmp)

    # tm's own store, so what gets written is exactly what `tm translate` would write.
    state = TmState.load(project)

    tally: dict[tuple, Counter] = defaultdict(Counter)
    verify: Counter = Counter()
    disagreements, drifted_examples = [], []
    by_key: dict[tuple, list[str]] = defaultdict(list)
    for source, per_doc in data[WORKTREE].items():
        for (doc, loc), (values, english, translated) in per_doc.items():
            recorded = state.snapshot(source, doc, loc)
            for key in sorted(translated):
                if key in recorded and not args.verify:
                    continue
                now = english[key]
                intro = introducing_commits(
                    commits, data, source, doc, loc, key, values.get(key)
                )
                then = {data[o].get(source, {}).get((doc, loc), ({}, {}))[1].get(key)
                        for o in intro}
                if None in then:
                    verdict, baseline = "unknown", None
                elif then == {now}:
                    verdict, baseline = "current", now
                else:
                    verdict, baseline = "drifted", next(iter(then - {now}))
                if key in recorded:
                    agree = (recorded[key] == now) == (verdict == "current")
                    verify["agree" if agree else "disagree"] += 1
                    if not agree:
                        disagreements.append((source, doc, loc, key, recorded[key], baseline, now))
                    continue
                tally[(source, doc)][verdict] += 1
                if verdict != "current":
                    by_key[(verdict, f"{source}/{doc}", key)].append(loc)
                if verdict == "drifted":
                    drifted_examples.append((source, doc, loc, key, baseline, now))
                if args.write and baseline is not None:
                    state.set_snapshot(source, doc, loc, key, baseline)

    print(f"\n{'source/doc':34} {'current':>9} {'drifted':>9} {'unknown':>9}")
    totals: Counter = Counter()
    for (source, doc), counts in sorted(tally.items()):
        totals.update(counts)
        print(f"{source + '/' + doc:34} {counts['current']:>9} {counts['drifted']:>9} "
              f"{counts['unknown']:>9}")
    print(f"{'TOTAL':34} {totals['current']:>9} {totals['drifted']:>9} {totals['unknown']:>9}")
    print()
    for (verdict, doc, key), locs in sorted(by_key.items()):
        shown = ", ".join(locs) if len(locs) < 28 else "all 28"
        print(f"  {verdict:8} {doc} {key}: {shown}")

    if args.verify:
        print(f"\nexisting snapshots: {verify['agree']} agree, {verify['disagree']} disagree")
        for d in disagreements[:20]:
            print("  {}/{} {} {}: recorded={} history={} now={}".format(*d[:4], *map(clip, d[4:])))
    for e in drifted_examples[: args.examples]:
        print("  {}/{} {} {}: {} -> {}".format(*e[:4], *map(clip, e[4:])))

    if args.write:
        state.save()
        print(f"\nwrote {state.path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

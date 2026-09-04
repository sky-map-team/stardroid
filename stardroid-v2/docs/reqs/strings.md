# Rethinking Strings

Status: **requirements + agreed direction**, not yet a design doc. The measurements below are
real; the decisions are agreed in principle and need a design doc (and a D-number — D114 is
the highest currently allocated) before implementation.

The current design for handling strings seems cumbersome and complex.

Relevant docs: [data-layer.md](../design/data-layer.md), [data-packs.md](../design/data-packs.md),
[localization.md](../design/localization.md).

Design decisions are to be made in parallel on Sky Map and on
[translationmanager](https://github.com/jaydeetay/translationmanager).

---

## The pivot

**Info-card prose moves to `res/values-*/` XML. Object names stay in `source-data/` → Room.**

This is the load-bearing decision and everything below follows from it. Info cards are 76% of
the available size win and carry none of the structural difficulty; names are the remaining 24%
and carry all of it. Room keeps the language-free catalog (position, magnitude, type, figures)
and gains a second job as the search index over names.

---

## Measurements

Everything in this section is measured against the current build, not estimated. `skymap.db` is
9.42 MB installed, 3.39 MB compressed inside a 23.5 MB APK. Assets are not split by language,
so every user downloads all 31 locales.

### Where the size is

| Component | Installed | Share |
|---|---:|---:|
| `info_card` + its index | 6.09 MB | 65% |
| `object_name` + 2 indexes + FTS | 2.56 MB | 27% |
| — of which universal designations | ~0.60 MB | (needed by everyone) |
| — of which localized names | ~1.96 MB | |
| `celestial_object` + indexes | 0.52 MB | 6% |
| figures, links, showers, types, packs | ~0.25 MB | 3% |

### What each option is worth

| Scope | DB installed | In APK |
|---|---:|---:|
| Today | 9.42 MB | 3.39 MB |
| **Cards → XML, names stay in DB** | **3.32 MB** | ~1.2 MB |
| ...plus dropping same-as-parent rows at generation (Decision 9) | **~2.50 MB** | ~0.9 MB |
| Cards *and* names → XML | 1.42 MB | ~0.5 MB |

Moving names as well buys a further 1.90 MB installed / ~0.7 MB download. It was rejected: see
[Why names stay in the database](#why-names-stay-in-the-database).

This answers the original bullet asking whether bundling all locales is a significant size
cost. **For info cards, yes** — 3.11 MB of the 3.23 MB of bundled card prose serves non-English
users, and Play cannot split an asset by language. **For names, no** — the splittable portion is
~1.96 MB and shrinks further once non-core locales are pruned.

**The saving is `gms`-only.** It comes entirely from Play delivering language splits; F-Droid
distributes a universal APK with no splits, so an `fdroid` user carries every locale either way.
The pivot is roughly size-neutral for that flavour — the same text moves from an asset to
resources. See [Split delivery](#split-delivery).

---

## Requirements

The original requirement list, each with its resolution inline. Everything still undecided is
collected under [Open questions](#open-questions).

- **The files should be easy to understand and maintain by both humans and machine
  translation.**
- **As much as possible should be in Android's native `.xml` format.** Resolved as a
  *tiebreaker, applied where it pays*: UI strings and info-card prose in XML; names in the DB,
  because XML actively fights the naming model (below).
- **Ease of transformation to platforms other than Android should be a factor.** Note this cuts
  against XML. It is a factor, not a veto — and `tm` already reads and writes both formats, so
  the source of truth being XML does not lock the content to Android.
- **Compatible with a future data-pack solution** ([data-packs.md](../design/data-packs.md)).
  Resolved as a deliberate two-tier model: bundled card prose is XML, pack-delivered card prose
  arrives as DB rows, and the resolver checks resources first, then pack rows. A downloaded pack
  cannot add `res/values-*/`, so this split is forced rather than chosen.
- **Objects need a primary name used as the map label; some are locale-specific.** Already
  implemented — see [What the DB already gets right](#what-the-db-already-gets-right).
- **Objects often have secondary names, used in the search index and shown on the info card as
  alternative names. Counts may differ per locale.** Already implemented, and measured: 59 of
  563 objects have locales that disagree on name count. The count difference is *correct data*,
  not a defect — see [Why counts differ](#why-counts-differ).
- **Objects may have universal names (M31) that are never translated, and may or may not be
  primary.** Resolved, and tightened: a universal name is a single **atomic** catalog
  designation, so compound forms are banned outright. The only case where one is ever
  locale-specific is a script that re-renders its digits. See
  [Universal names are atomic](#universal-names-are-atomic). Whether it may be primary is
  settled by Decision 7 and the measured cases in
  [What the DB already gets right](#what-the-db-already-gets-right).
- **Not every string needs its own translation; follow the usual fallback rules.** Already
  implemented: `LocaleSpec.fallbackChain`.
- **`en` is the root; fall back to it from any locale so text is never blank — and the tool
  should flag it.** Already implemented and tested. The doc's original wording said `en-US`;
  the code uses `en`, Android's default `values/` is the same content, and no `en-US` file
  exists anywhere. Keeping `en` — the requirement is satisfied, the name differs.
- **Tag translations as "identical to parent" so they are not carried but are not coverage
  losses.** Already exists in `tm` as `same_as_parent` (`tm:omitted` comments in Android XML,
  `.tmstate.toml` otherwise), plus `tm compact`.
- **Tag translations as "human generated" so `tm` will not overwrite them without `--force`.**
  Already exists as `human_translated` (`tm_human` attributes in Android XML).
- **Detect stale translations, easily for humans, with no cost to app size.** Already exists via
  `source_snapshot`. It costs nothing in the app — `.tmstate.toml` never ships. It does cost
  2.7 MB in the repo; **out of scope** for this work.

The three tagging mechanisms the original requirements asked for all already exist. The
complexity being felt is not their absence.

---

## Decisions

### 1. Info-card prose moves to `res/values-*/` XML

Translatable prose only: `description`, `fun_fact`, `search_subtext`.

They need no new `tm` machinery: once in `res/values-*/` they are ordinary Android string
resources, so the card file simply joins the existing `android` source's `files` list. The
two-tier model is not a `tm` concern at all — `tm` translates sources, and the runtime resolver
is what merges bundled resources with pack rows. `json-cards` stays configured only for the
Willman 1 canary and, later, pack content (Decision 8).

The other five fields stop being per-locale strings:

| Field | Today | Becomes |
|---|---|---|
| `spectral_class` | byte-identical in all 31 locales (78 entries each) | language-free DB column |
| `image_credit` | byte-identical in all 31 locales (251 entries each) | language-free DB column |
| `size`, `distance`, `mass` | translated prose — "722 square degrees" | numeric column + runtime unit formatting |

`spectral_class` and `image_credit` are pure duplication: the same bytes stored 31 times and
sent through a translation model that correctly declines to change them. `size`/`distance`/
`mass` are worse — they are data wearing a prose costume, and translating "722 square degrees"
into 30 languages is 318 model calls per locale to render a number and a unit. Both go to the
language-free tier; the unit formatting becomes a presentation concern.

This needs a unit-formatting layer that does not exist yet — see Decision 12, which specifies it
and records the locale-formatting bugs that motivate it.

### 2. Object names stay in `source-data/` → Room

See [Why names stay in the database](#why-names-stay-in-the-database).

### 3. Search shows localized and universal names only

A French user sees French names and universal designations. Seeing an English name is a
**localization coverage gap**, not a feature — and since display already falls back to English
so nothing is ever blank, search should match exactly what is displayed.

This falls out for free: index the resolved name plus the universal designations. No separate
English search tier. An English name appearing in a French UI is visible in `tm languages` as
the coverage gap it is.

**Two different faults look identical on screen.** A *coverage* gap — nobody has written the
French string — shows up in `tm languages`. A *delivery* gap — the `values-fr` split is not on
the device — does not: `tm` reports 100% because the string exists in the repo. Both render as
English text in a French UI. Only card prose can have a delivery gap, because only card prose
becomes a split resource; names stay in the DB. Whatever diagnostic reports coverage in the app
has to tell these apart, or a delivery failure will be misread as missing translation and
"fixed" by retranslating something that is already correct.

### 4. Alias lists are not machine-translated

`tm` owns primary names — they are the map label, user-visible, and worth generating
automatically. Alternate names are seeded once from the existing corpus, deduplicated, and
thereafter changed only by hand or by the `skymap.add-object` skill.

The reason is in the data. Asking a model to translate four synonymous English aliases produces
the same word four times: `es.csv` has `Alfa Centauri` for `rigil_kentaurus_a` four times over,
`el.csv` has `Άλφα Κενταύρου` three times. 97 redundant rows across 85 (object, locale) groups,
worst in zh-hans (12), zh-hant (10), fa (7), es (7). Aliases are search keys, so a bad one is
invisible until someone's search silently fails.

### 5. Coverage counts primary names only

If Spanish genuinely has one distinct name where English has five aliases, that object is
**100% covered** in Spanish.

This needs no mechanism — it is what the pipeline already does. `tm`'s translatable unit for
`csv-overrides` is an object's primary row, keyed by object id: `_units_from_rows` skips every
row with `is_primary=0`. Coverage is computed over units, so there is exactly one per object per
locale and alias cardinality is structurally invisible to it.

An earlier draft of this document proposed a per-locale "list is complete" flag. That was a
leftover from assuming names would become XML `<string-array>`s, where the whole array is one
variable-length unit and "is this list finished?" cannot be answered from the data. Decisions 2
and 4 dissolved the problem: names stay in CSV with per-row granularity, and aliases are not
translation units at all. No flag is needed.

What the alternative would have cost is worth recording, because it is what created the 97
duplicate rows: counting a locale's aliases against English structurally rewards writing
"Alfa Centauri" four times to satisfy a counter.

### 6. The generated DB is pruned to the 28 core languages

`ca` and `hu` carry 16 names and 145 partial card sets each while being deliberately excluded
from `primary_languages`; `ru` has 172 names and no info cards at all. The shipped catalog
should match what `tm` actually maintains. This is not a change to
[the intentional exclusion of ca/hu/ru](../../AGENTS.md) — it is making the build agree with it.

---

### 7. Exactly one primary name for any object that has any name at all

Not *at most* one — **exactly** one. Every object with a name has a primary, and no
`(object, locale)` has two.

The "at most one" half already holds: zero objects have two primaries in the same locale. The
"at least one" half **does not** — 1,871 objects have names and no primary anywhere. All of them
are stars, and all the gaps are in the universal tier; the localized data is clean. The star
catalog simply never set the flag.

**This is a live labelling bug, not a hygiene issue.** 1,026 of those stars have more than one
name, so `bestNameInChain` falls through to its alphabetical tiebreak — and because ASCII digits
sort before letters, that picks the Flamsteed number over the Bayer name:

| Star | Mag | Candidates | Currently labelled |
|---|---:|---|---|
| Gamma Cassiopeiae | 2.15 | `27 Cas`, `Gamma Cassiopeiae`, `γ Cas` | **27 Cas** |
| Zeta Ophiuchi | 2.54 | `13 Oph`, `Zeta Ophiuchi`, `ζ Oph` | **13 Oph** |

33 of the ambiguous stars are brighter than magnitude 3, so these are prominent naked-eye stars
carrying a catalog number where a Bayer designation exists. This is traced through
`bestNameInChain` → `pickByChain` rather than observed on screen; worth an eyeball, though the
sort order is not in doubt.

**Backfill rule: Greek abbreviation > spelled-out Bayer > Flamsteed.** `γ Cas` beats
`Gamma Cassiopeiae` beats `27 Cas` — compact, which matters on a crowded star field, and the
form astronomers actually use.

Applied to the data this resolves **all 1,871 with no residue**: 1,026 take a Greek form and
845 take a Flamsteed number. The spelled-out tier never fires, because every star carrying a
spelled-out Bayer name also carries the Greek one. The 845 are single-name stars, so marking
their sole name primary changes nothing on screen.

**It does change 1,026 labels**, including the 33 brighter than magnitude 3 — from
`Gamma Centauri` and `27 Cas` to `γ Cen` and `γ Cas`. That is a deliberate product change, not a
silent side effect of a data fix, and should be called out in release notes.

**Enforcement: strict at build, tolerant at runtime.** The generator fails on an object with
names and no primary, or with two primaries in one locale, alongside the existing asset-licence
and format-string checks. The runtime keeps its deterministic tiebreak anyway, because a
downloaded pack is exactly the case the build gate cannot cover and a bad pack must not crash
the app. Runtime tolerance without the build gate would be the worst of both — which is
precisely how 1,871 missing flags went unnoticed.

### 8. One info card stays in JSON as a canary

`dso/willman_1` keeps its card in `source-data/info_cards/` in all 29 locales it has, and does
**not** get XML resources. Everything else moves.

This keeps the `json-cards` `tm` source, the DB card path, and the two-tier resolver from
bit-rotting in the window between this work and packs shipping. Two things make it a real test
rather than a decoration: the card must exist *only* in JSON, so the resolver genuinely falls
through instead of being shadowed by an XML entry, and there must be a test asserting it
resolves.

Willman 1 is the right choice because it is invisible. An ultra-faint dwarf satellite galaxy at
magnitude 15.4, it is beyond naked-eye and most amateur telescopes, so a regression in the card
path costs nothing user-visible while still failing the test. Its `image_credit` already reads
"Sky Map team (it's very faint)".

### 9. Same-as-parent row sets are dropped at generation, not in source

6,283 name rows across 6,147 `(object, locale)` groups duplicate English entirely — mostly IAU
proper names that genuinely are identical in other languages (`Baten Kaitos`, `Cor Caroli`,
`Kaus Borealis`, `Tania Australis`, `Nubecula Major`). They are correct data, not a defect, and
the fallback chain already resolves them without help.

**Source keeps them, tagged `same_as_parent`. The generator omits them.** Keeping them in
`names/*.csv` is what makes coverage honest and stops `tm` retranslating them every run;
omitting them from `skymap.db` cuts `object_name` by 32% and takes the post-pivot DB from
3.32 MB to **~2.5 MB**.

`tm compact` is not the mechanism. It marks rather than deletes — its write path states that
"same_as_parent units are included too: their `.value` already equals what the fallback chain
resolves to" — which is the behaviour we want in source, and the reason the drop has to happen
at build time instead.

**The generator must test the whole row set, never individual rows.** Fallback is whole-object:
`pickByChain` takes the earliest chain locale with *any* row for an object and uses only that
locale's rows. 183 groups are partially identical — a translated alias beside an
identical-to-English primary. Dropping the primary there would leave the locale holding rows but
no primary, violating Decision 7 and resolving the map label to an alias. A locale's rows for an
object come out only if *all* of them are redundant.

### 10. The dead `names` and `designations` columns are deleted

`dso.csv` and `stars.csv` each carry a `names` column, and `dso.csv` a `designations` column.
**Nothing reads them.** `SourceDataLoader` takes `id`, `ra_deg`, `dec_deg`, `magnitude` from
`stars.csv` and adds `type`/`size_arcmin` from `dso.csv`; every name in the DB comes from
`source-data/names/`. They are residue from `tools/harvest-v1/harvest.py`.

They are also a worse version of what replaced them — a hand-maintained lowercase, unpunctuated,
ASCII-transliterated alias layer that `object_name.name_normalized` plus FTS now derives
automatically:

| CSV column | `names/en.csv` |
|---|---|
| `crab nebula` | `Crab Nebula` |
| `scorpions tail` | `Scorpion's Tail` |
| `hercules_gc`, `eagle_nebula`, `pinwheel_galaxy` | the proper names |
| `h and chi Persei` | `h and χ Persei` |
| `Cygnus X 1` | `Cygnus X-1` |

And they have rotted, which is what dead data does: `m45` lists `pleiades, seven sisters` where
`names/en.csv` has `Matariki, Pleiades, Seven Sisters`; `omega_centauri` says `omega centauri`
against `Omega Centauri Globular Cluster`; `star/n25` has `25` and no `en.csv` entry at all.
Meanwhile 134 of 137 `designations` values are already fully present in `universal.csv`.

`objects.json` is **not** part of this problem and stays. It is a sidecar of 260 sparse
hand-authored attributes — `image_ref` (250), `see_also` (31), `type` (36), `parent` (16),
`activity` (10) — spanning kinds that have no CSV at all, since constellations come from
`constellations/iau.json` and planets and moons are computed. Only 73 entries overlap `dso.csv`
and 62 overlap `stars.csv`. The split is bulk tabular data versus sparse curated extras, which
is sound; the name is misleading, and its `ra`/`dec`/`magnitude`/`search_fov` for 10 objects do
genuinely overlap the CSVs' job.

### 11. English aliases are never copied into another locale

`tm` currently copies the baseline's alias rows verbatim when it introduces an object into a
locale that lacked it, so a freshly-translated French object arrives carrying English
alternates. It should not. A French user sees French names and universal designations; an
English alias on a French info card is the same defect as an English label.

The consequence is accepted: because fallback is whole-object, a locale gets its translated
primary and *no* alternates until someone writes them. That is the correct trade — no alternates
is a smaller wrong than wrong-language alternates, and the gap is visible as missing content
rather than disguised as translated content.

**But this only holds once language-neutral aliases are reclassified.** `en.csv` has just 36
alias rows, and eight of them are not English — and are already duplicated across most locales:

| Object | Primary | Alias | Actually | Locale files carrying it |
|---|---|---|---|---:|
| `dso/lmc` | Large Magellanic Cloud | Nubecula Major | Latin | 24 |
| `dso/smc` | Small Magellanic Cloud | Nubecula Minor | Latin | 24 |
| `star/alphekka` | Alphekka | Alphecca | Arabic transliteration variant | 20 |
| `star/alnath` | Alnath | Elnath | Arabic transliteration variant | 19 |
| `star/mirphak` | Mirphak | Mirfak | Arabic transliteration variant | 19 |
| `star/etamin` | Etamin | Eltanin | Arabic transliteration variant | 18 |
| `star/phad` | Phad | Phecda | Arabic transliteration variant | 17 |
| `dso/m44` | Beehive Cluster | Praesepe | Latin | 14 |

These move to `universal.csv`: **155 locale rows collapse to 8**. The duplication is the proof —
a string every locale independently carries verbatim is by definition not a translation. Nobody
should be translating `Praesepe` or `Elnath`, and a French user typing either should find the
object. Without the move, this decision silently removes them from 30 locales' search, which
would read as a coverage gap while actually being a data-classification bug.

**`Matariki` is not one of them and stays in `en.csv`.** It is the Māori name for the Pleiades,
and it appears in exactly one locale file — `en`. It is a Māori word naturalised into New
Zealand English (Matariki is a public holiday there, called that in English), not a
language-neutral designation. Promoting it would push a culturally specific name into 30 locales
that have no use for it. "Not English" and "universal" are different tests, and this is the case
that separates them.

The remaining 27 aliases (`Seven Sisters`, `Eye of God`, `Blaze Star`, `Silver Dollar Galaxy`,
`Hercules Globular Cluster`, …) are genuine English prose and stay in `en.csv`.

### 12. Measurements become a number plus a unit key, with a prose escape hatch

Decision 1 said `size`, `distance` and `mass` stop being translated prose. This is how, and why
the obvious version — a plain numeric column — does not work.

**The fields are 97% regular and 3% genuinely prose.** Of 647 values across the three fields,
627 decompose into `[approximation marker] [number] [unit phrase] [optional qualifier]`. The
other 20 carry information a numeric column cannot hold:

| Shape | Example |
|---|---|
| Three-axis dimensions | `15 × 12 × 11 km` (asteroids) |
| Ranges | `~10,000–16,000 light-years (range)` |
| Labelled sub-values | `Black hole mass: ~21 solar masses` (in the `size` field) |
| Scientific notation | `1.898 × 10²⁷ kg` |
| Whole sentences | `Each cluster ~70 light-years across; the pair separated by ~1,000 light-years` |

**The design.** A numeric value stored as data, plus a *unit key* resolving to a translatable
format string (`%1$s square degrees`, `%1$s light-years across`), rendered through ICU at display
time. Qualifiers become their own keys. There are **19 distinct unit phrases and 9 qualifiers —
28 translatable strings replacing 647 translated measurements per locale.** The 20 irregular
values keep a free-text override field that stays translated prose and wins when present.

**The justification is correctness, not translation volume.** Number formatting is locale-specific
and the current per-value translation gets it wrong:

- **`hi` is wrong.** Hindi uses the Indian numbering system, which regroups above 100,000:
  `123,000` should be `1,23,000`. All sampled values keep Western grouping.
- **`ko` contradicts itself.** `160,000광년` sits beside `20만 광년` — Western grouping and
  Korean myriad notation, same field, adjacent entries.
- **`fa` is the only locale that gets it right**, with Perso-Arabic digits and the Arabic
  thousands separator: `۱۶۰٬۰۰۰ سال نوری`.
- **Unit phrases drift within a locale.** Hindi renders "light-years across" three ways —
  `प्रकाश-वर्ष व्यास`, `प्रकाश-वर्ष चौड़ा`, `प्रकाश-वर्ष चौड़ाई में` — because each of the 318
  values was translated independently against no shared unit vocabulary.

A formatter cannot make these mistakes and a translator cannot reliably avoid them: rendering
`1,23,000` correctly is a property of the locale's number system, not of the sentence. One shared
phrase per unit also cannot drift three ways.

**These are live bugs, not consequences of the pivot.** `hi` and `ko` are wrong in the shipped
catalog today and could be fixed independently of everything else in this document. The sampling
covered the Indic and CJK locales plus `ar`/`fa`; `zh-Hans`, `zh-Hant`, `ja`, `th` and `ar` keep
Western grouping, which is defensible in modern technical writing but is a choice nobody made
deliberately.

## Why names stay in the database

Moving names to XML costs 1.90 MB installed / ~0.7 MB download. Against that:

**Android resource shapes fight the naming model.** Flat ordinal keys (`name_m31_1`, `_2`) are
unusable: Persian legitimately has *more* names than English for some objects, which means a key
in `values-fa/` with no counterpart in `values/` — a `MissingDefaultResource` that no
`R.string` reference can safely resolve. That leaves `<string-array>` per object, which works but
must then be hidden from `tm` (arrays replace wholesale per locale, so there is no key-by-key
coverage to compute), which reintroduces exactly the per-locale-cardinality bookkeeping the DB
does natively.

**Universal names have no home in the resource system.** Universal is not a locale, so it cannot
be a `values-*/` directory. It would need a parallel non-translatable resource plus a merge rule
at read time.

**It adds runtime machinery.** A first-run index build (~540 rows per locale), a stored locale
tag, and a re-index when the device language changes — none of which exists today, because
`createFromAsset` ships the index prebuilt.

**Names in the DB are immune to split delivery.** Assets are never split by language, so the
catalog and its search index are complete on every device from install, whatever the user later
picks as their language. Card prose in XML gives that up in exchange for the size win; names
would give it up for ~0.7 MB. See [Split delivery](#split-delivery).

**The DB already satisfies every naming requirement in this document.** That is the strongest
argument: the work is to write down what already works, not to rebuild it.

Recorded as a deferred option. If locale count or names-per-object grow substantially, or if
size pressure changes, reopen it — the price and the complexity list are above.

### What the DB already gets right

- `LocaleSpec.fallbackChain` produces `[fr-FR, fr, en, ""]` — the `en` root and the universal
  tier exactly as the requirements specify, with tests in `LocaleSpecTest`.
- Per-locale cardinality is native to a table. Decision 5's "100%" is simply what a table does.
- **Primary-name resolution, including the universal-primary case.** 75 objects have both a
  universal primary and a localized one (M102 is universal `M102` plus fr `Galaxie du Fuseau`);
  23 have only a universal primary (M10, M103, M106). `bestNameInChain` sorts primary-first,
  then `pickByChain` takes the earliest chain locale — so a localized primary wins where one
  exists, and the universal primary is the last resort. Correct today; **undocumented**, which
  is the actual defect. The design doc should state it as a rule and test it.

---

## Universal names are atomic

A universal name is **a single atomic catalog designation** — `M31`, `NGC 7662`, `Mel 25`,
`ω Centauri`. No connectives, no joins, no compounds. Expressing "these two catalog entries
together" is a job for the object model (`object_link`, or a parent object with children), not
for a name string.

**Compound designations are banned, and it costs nothing.** Exactly one exists in the corpus:
`NGC 869 & NGC 884` for Double Cluster. `universal.csv` already carries `NGC 869` and `NGC 884`
as separate atoms, so deleting the compound loses no findability — a user typing `NGC 869`
already matches. It was never worth what it cost: 23 of the 29 locales copy it verbatim, and
three (`es` with `y`, `ar` and `fa` with `و`) translate the connective, which is the entire
reason "universal names are never translated" appeared to fail. English has *two* names for
Double Cluster where translated locales have three purely because of this one row.

`h and χ Persei` is not affected and is not a compound designation. It is a conventional name
for the pair rather than a catalog-ID join, it is genuinely translated (`h und χ Persei`,
`h et χ Persei`, `h och χ Persei`), and it already lives in the per-locale files where it
belongs. It never gets a universal row.

**One real exception remains: scripts with their own numerals.** Persian writes Caldwell 22 as
`کالدول ۲۲`, Melotte 25 as `Melotte ۲۵`, NGC 7662 as `NGC ۷۶۶۲`. That is why `fa` has three
names for Blue Snowball where every other locale has one. This is handled as a locale override
on the universal tier — the mechanism the data already uses — so "never translated" is an
invariant `tm` enforces for every designation except a locale that re-renders its digits.

**Redundant copies of universal names should be deleted.** 78 locale rows are byte-identical to
a universal name for the same object, across 29 locales and 7 objects: `ω Centauri` (27
locales), `NGC 869 & NGC 884` (23), `30 Doradus` (20), `M65` and `M66` (3 each), `Alpha Leonis`
and `Alpha1 Centauri` (1 each). The fallback chain already reaches the universal row, so every
one is dead weight — `same_as_parent` / `tm compact` territory.

### Why counts differ

For the record, since this drove several of the decisions above — 59 of 563 objects have locales
disagreeing on name count, from three causes, only one of which is legitimate:

1. Locale-specific renderings of universal designations (**genuine** — Persian numerals).
2. The one compound designation (**resolved** — banned above; the row is deleted).
3. Collapsed aliases never deduplicated (**bug** — 97 rows, see Decision 4).

---

## `source-data/` cleanup

In scope. Concrete items found while measuring:

- **`info_cards/en-gb.json` is a 163 KB near-copy of `en.json`** — 89% of fields byte-identical,
  where `names/en-gb.csv` is correctly sparse at 5 lines. Under the XML move this becomes a
  small `values-en-rGB/` override.
- **97 duplicate name rows** across 85 (object, locale) groups (Decision 4). Deduplicate, and
  record the shorter list as complete rather than as missing coverage (Decision 5).
- **`spectral_class` and `image_credit` stored 31 times each** (Decision 1).
- **`size`/`distance`/`mass` as translated prose** (Decision 1).
- **The one compound designation in `universal.csv`** — `NGC 869 & NGC 884`, plus the 23
  per-locale copies and 3 translations of it (see [Universal names are
  atomic](#universal-names-are-atomic)).
- **78 locale rows byte-identical to a universal name** for the same object, across 29 locales
  and 7 objects. The fallback chain already reaches the universal row.
- **`ru` has names but no info cards; `ca`/`hu` have both but are non-core** (Decision 6).
- **1,871 stars with no primary name** — backfill per the precedence rule in Decision 7. All in
  the universal tier; the localized data is clean.
- **Dead `names` / `designations` columns** in `dso.csv` and `stars.csv` (Decision 10).
- **Eight language-neutral aliases misfiled in `en.csv`** — promote to `universal.csv`,
  collapsing 155 duplicated locale rows to 8 (Decision 11).
- **6,283 rows duplicating English** — kept in source and tagged, dropped at generation
  (Decision 9). Not a defect: mostly IAU proper names identical across languages.

---

## Split delivery

Moving card prose into `res/values-*/` makes it subject to Play's language splitting, which is
where the size win comes from and also the one new failure mode the pivot introduces.

**Splitting is already on, and already applies to UI strings.** There is no `bundle {}` block in
`app/build.gradle.kts`, no `resourceConfigurations` or `localeFilters`, and the release AAB's
`BundleConfig.pb` carries no `splitsConfig` override — so AGP's default holds and language,
density and ABI splits are all enabled. The 332 UI strings in `values-*/` have this exposure
today. The pivot extends it to card prose; it does not invent it.

**`android:localeConfig` does not change delivery.** The manifest declares
`android:localeConfig="@xml/locales_config"` with 32 locales, which populates the Android 13+
per-app language picker. It is a declaration of what the app supports, not an input to how Play
splits or delivers resources — splits are computed from the resource configurations in the
bundle and served against the device's locale settings. Declaring it does **not** force users to
download every locale.

**The app switches language live.** `LocaleSource` deliberately never caches the locale: it
re-reads the live configuration on every call and re-emits on configuration change, so catalog
reads follow a language switch without a process restart. That is the path where a missing
split would show.

**What happens when the user changes language after install.** Install-time splits match the
device's configured locales, so the common case is complete. After that:

- **System language change** — Play tracks the device locale list and fetches the missing split.
  The app may show English briefly; it self-heals. Needs network and a Play sync, so "briefly"
  is not bounded, and an offline user stays on English until they reconnect.
- **Per-app language picker** — a per-app locale is held by the system `LocaleManager` and does
  *not* alter the device locale list, which is what Play watches. The expectation is that Play
  does **not** auto-fetch for this path, which is why Google's guidance for apps combining
  `localeConfig` with language splits is to call `SplitInstallManager.installLanguages()` /
  `deferredLanguageInstall()` before applying the locale.

That second point is **unverified** — it is stated from general knowledge of Play's behaviour,
not measured, and Play has changed here before. It is also not a consequence of this document:
if it holds, it is a live defect for UI strings today. See [Open questions](#open-questions).

---

## Out of scope

- **The `.tmstate.toml` sidecar.** 2.7 MB in the repo, never ships to a device. Worth noting
  that moving cards to XML does *not* shrink it: `source_snapshot` is out-of-band for every
  format including Android XML, and is 43% of the file today, entirely from the `app` source.
  Only `same_as_parent` and `human_translated` are in-band for XML. If it is ever addressed it
  is a `tm` change, not a Sky Map one.
- **Moving object names to XML.** Deferred, with the price recorded above.

---

## Open questions

- **Whether Play auto-fetches a language split for a per-app locale selection.** Settle it on a
  device rather than from documentation: build the `gms` AAB, `bundletool build-apks` +
  `install-apks` with a restricted language set, then change the system language and Sky Map's
  per-app language separately and watch whether the split arrives. If it does not, UI strings
  need `SplitInstallManager` regardless of what happens to card prose.
- How the in-app coverage diagnostic distinguishes a delivery gap from a coverage gap
  (Decision 3).

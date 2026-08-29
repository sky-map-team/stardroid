# Localization (v2)

Status: **proposed** — this document is the approach discussion, not yet a decision.

v2 ships US-English only today. v1 ships 32 locales, machine-translated by the `tm` tool with
human corrections layered on top. This document covers how v2 gets to parity: how UI strings
should be chunked, how much of v1's translated corpus can be reused, and what the v2 data
architecture changes about the picture.

---

## 0. Where v2 stands

Translatable UI resources in `app/src/main/res/values/`:

| File | Keys | Chars | Notes |
|---|---:|---:|---|
| `strings.xml` | 332 | 8,153 | Short UI strings; longest is 698 chars |
| `help.xml` | 1 | 11,170 | **One key.** Bigger than all of `strings.xml` combined |
| `credits.xml` | 1 | 3,687 | One key |
| `eula.xml` | 1 | 839 | One key |
| `whatsnew.xml` | 3 | 1,115 | Per-release churn |
| `contributors.xml` | 2 | — | `translatable="false"` — correctly excluded |

Catalog data (object names, info cards) is **not** in string resources — it lives in the
generated Room DB (D34), already carrying ~30 locales harvested from v1. That split is the
single most important fact for the questions below.

---

## 1. Should the walls of text be broken up?

**Yes for `help.xml`. No for the others.**

The economics are per-key: `tm` retranslates a key when its source text changes. A 11 KB
single-key help document means a one-line fix to the Troubleshooting section retranslates the
*entire* document across every locale — and the v1 model comparison
(`../../stardroid-v1/docs/design/translations.md`) shows long-form text is exactly where the
models are least reliable: Sonnet had outright parse failures on `hi` and `th`, Gemini
introduced a Japanese `'s` possessive bug and translated the brand name in Welsh. Long-form
retranslation is both the most expensive operation and the one most likely to regress a locale
that was previously fine.

The current `help_text` is already cleanly sectioned by `<h2>`, so the split is mechanical:

| Proposed key | Source section |
|---|---|
| `help_intro` | Introduction and Quick Start |
| `help_navigating` | Navigating the Sky |
| `help_layers` | Visible Layers |
| `help_search` | Search |
| `help_time_travel` | Time Travel |
| `help_info_cards` | Info Cards |
| `help_gallery` | Gallery |
| `help_night_vision` | Night Vision Mode |
| `help_location` | Setting Your Location |
| `help_pointer_mode` | Telescope Users — Pointer Mode |
| `help_tips` | Tips and Settings Worth Knowing |
| `help_hardware` | Hardware Requirements |
| `help_troubleshooting` | Troubleshooting |

~13 keys averaging under 900 chars each. `HelpScreen.kt` concatenates them in order, so the
rendered output is unchanged. The win compounds: help text is the most-edited copy in the app
because it documents features as they land, and each edit currently costs a full-document
retranslation × 30 locales.

### Chunking and the first translation run: retranslate, don't salvage

**Decision: split the English source into section keys, then retranslate the help document
from scratch across all locales. Do not attempt to carry v1's help translations over.**

The salvage option was investigated and rejected. It is technically feasible — `tm translate`
fills *missing* strings keyed by resource name, so 13 new `help_*` keys read as 100% missing
everywhere, but the existing per-locale `help_text` could be split offline on `<h2>` boundaries
and written directly so `tm` sees them as already present. The structure supports it: **26 of
29 locales** have byte-identical section structure to English (14 `<h2>`, 7 `<h3>`). Three
don't — `ko` predates the HTML rewrite entirely (`<big><b>`/`<br/>`, zero `<h2>`), while `ru`
and `zh-Hans` have 13 sections against v1's 14.

It isn't worth it, for three reasons:

1. **The reusable fraction is only ~63% of v2's help by length, and it's lumpy.** v2's help was
   substantially rewritten. Per-section similarity against v1:

   | Would carry over (≥ 0.8) | Genuinely rewritten (< 0.6) |
   |---|---|
   | Pointer Mode (1.00), Info Cards (0.94), Troubleshooting (0.93), Intro (0.88), Location (0.87), Search (0.83), Hardware (0.80) | Time Travel (0.13), Night Vision (0.35), Navigating (0.44), Visible Layers (0.53), Tips (0.55) |

   Time Travel at 0.13 is effectively new copy. So five of thirteen sections need translating
   regardless, and the salvage only ever avoids about two-thirds of one document.

2. **That 63% is a similarity score, not a correctness guarantee.** A 0.8-similar section still
   has sentences that changed meaning, and those are precisely the edits that matter — v1's own
   history shows the trap (PR #895 reworded compass copy from "calibration" to "attention" on
   purpose). Every carried-over section needs a human or model to diff it against the new
   English anyway. The review burden lands on 26 locales × 7 sections, which is not obviously
   cheaper than just translating them.

3. **It buys a one-time saving for permanent complexity.** The splitter, the three outlier
   locales, and the structural-validation pass are all throwaway code that has to be right
   once and is then deleted. The chunking's actual value is *prospective* — it makes every
   future help edit cheap — and that value is fully realized whether or not the first run
   reuses v1 prose.

The one thing that would have changed this: if v2's help were a light edit of v1's rather than
a rewrite. It isn't.

So the plan is simply: split the English `help_text` into the ~13 section keys, then run
`tm translate` normally. The chunking still pays for itself from the *second* edit onward,
which is the point — a future one-line Troubleshooting fix retranslates ~1.8 KB instead of
11 KB, and sub-1 KB chunks sidestep the JSON-truncation failures that hit long-form runs.

Two caveats that keep this honest:

- **Translation quality needs context.** Chunks are translated independently, so a model can
  lose document-level consistency (a term rendered one way in Search and another in
  Troubleshooting). Mitigation: the `tm` glossary already exists for exactly this, and the
  chunks are whole sections — self-contained prose, not sentence fragments. Do **not** split
  below section granularity.
- **Sections must not be split mid-sentence or mid-markup.** Each chunk owns its complete
  `<h2>…</h2>` block including trailing `<p>` elements.

**`credits_text` (3.7 KB) should be split differently — or largely not translated at all.**
Most of its bulk is proper nouns: contributor names, image credits, institution names
(NASA/ESA/Hubble). Those are not translatable content, and sending them through a translation
model is how you get transliterated human names. The prose scaffolding around them is a few
hundred characters. Recommended: separate the translatable framing sentences from the
`translatable="false"` name/attribution blocks, mirroring what `contributors.xml` already does
correctly.

`eula_text` (839 chars) and the `whatsnew` keys stay whole — they are legal text and
per-release copy respectively, and both are rewritten wholesale when they change anyway.

---

## 2. How much of v1's translated corpus can be reused?

**Measured, not estimated.** Comparing v2's 337 translatable strings against v1's 260.

Note this is a separate question from §1's. The long-form documents are being retranslated
because v2 rewrote them; the short UI strings below were mostly *not* rewritten, so the
economics are opposite — reuse is high and the carry-over is worth automating.

| Category | Count | % of v2 | Cost |
|---|---:|---:|---|
| A. Same key, **identical** text | 93 | 28% | Free — direct copy |
| B. Different key, **identical** text | 108 | 32% | Free — copy via key remap |
| C. Fuzzy ≥ 0.85 (near-identical) | 16 | 5% | Cheap — post-edit |
| D. Fuzzy 0.70–0.85 (close) | 23 | 7% | Cheap — post-edit |
| E. Fuzzy 0.50–0.70 (loose) | 62 | 18% | Review; often genuinely reworded |
| F. Genuinely new (< 0.50) | 34 | 10% | Full translation |

**~60% of v2's UI strings are byte-identical to an already-translated v1 string**, and another
12% are within post-edit distance. Only ~10% is genuinely new copy.

Category B is the interesting one and the reason a naive port misses most of the value: the
text is identical but v2 renamed the key to fit its own conventions. Examples:

```
v2:calibration_button              <- v1:menu_calibrate
v2:layer_constellations            <- v1:show_constellations_pref
v2:settings_view_direction         <- v1:rotate_horizon_preference_title
v2:diagnostics_rotation            <- v1:diagnostics_activity_rotation_vector
v2:object_info_magnitude           <- v1:object_info_magnitude_label
```

### Mechanism (implemented — `tools/salvage-translations/salvage.py`)

A one-shot migration script, not a permanent piece of infrastructure:

1. Parse v1 `values-*/` for all 32 locales into `{locale: {key: text}}`.
2. Build an English text → v1 key index; normalize whitespace, case, and escaped apostrophes.
3. For each v2 English string, resolve a v1 source key by (a) exact key match, (b) exact
   normalized-text match, (c) fuzzy match above a threshold.
4. Emit v2 `values-<locale>/strings.xml` with the v1 translation for every A/B match.
5. Emit a review manifest listing every C/D/E match with its similarity score and both source
   texts, so fuzzy carry-overs are human- or model-reviewed rather than trusted blindly.

Categories A and B are safe to automate. **C–E must not be auto-accepted**: a 0.9 similarity
can still be a meaning inversion. The measured sample includes exactly this trap —
`no_sensor_warning` was rewritten in v2 from "may lack orientation sensors" to a definite "has
no compass or motion sensors", and v1's compass copy was deliberately reworded in PR #895 from
"needs calibration" to "needs attention". Carrying the old translation across would silently
resurrect superseded wording in 30 locales.

The remainder (F, plus everything rejected in review) goes through `tm` as a normal
translation run.

**Format-specifier hazard:** v2 modernized `%s` to `%1$s` in several strings
(`search_target_found_message`, `search_target_looking_message`). The script rewrites
positional specifiers in carried-over translations, and refuses any string whose specifier
multiset disagrees with its v2 source rather than shipping a `String.format` crash. This
caught a latent v1 bug: seven locales (`ja`, `ru`, `ko`, `nb`, `pt`, `uk`, `zh-Hant`)
translate `location_long_lat` with `%1$s` where the source declares `%1$.2f`. Those are
skipped and left for `tm`.

**Case is significant.** An early version folded case before matching and mapped v2's
`diagnostics_east` ("East", a sentence-case table row) onto v1's `east` ("EAST", the
all-caps horizon label), which would have put the shouty form in the diagnostics panel of
every locale. Matching is now case-sensitive; case-only near-misses are reported, not
applied. Ten strings were affected.

**Ambiguity is reported.** Several v1 keys can share English text (`Diagnostics` was both a
menu item and an activity title) and were translated independently. Those matches are applied
but listed in the manifest for verification.

**v1's own damage is not imported.** v1's `warm_welcome_slide3_*` strings were merged badly at
some point in its history, leaving half-translated text in ~24 locales. Four checks reject a
v1 translation rather than trust it:

- *Markup parity* — the translation must carry the same inline tags as its v2 source. Exact
  parity, not "same or none": `tm validate` reports a dropped `<b>` as `broken_html`, so
  zh-Hans and fr (whose prose is complete but unemphasized) cannot be carried either.
- *Leftover English* — the longest run shared with the English source must not be a
  meaningful chunk of the string.
- *Merge artifact* — `...erkunden!=\n <b>Manual Mode</b>!`; the stray `=` is the tell.
- *Repeated tail* — the translation is complete but followed by the last clause of the
  English original (`...宇宙全体を探索できます！<b>Manual Mode</b>!`). This is the widest of the four:
  21 locales, and the run is too short for the leftover-English check to catch.

Together these skip 50 locale/key pairs. The last two catch what `tm validate` cannot: a
*complete* tag set beside half-translated prose passes validation while shipping visible
English inside a Japanese string. An early version of the tool carried 21 of these into v2
before the tail check existed.

Both false-positive directions were tuned out, since a check that cries wolf gets ignored:

- Correct translations legitimately end the way their source does — a preserved brand
  ("Google Analytics"), an abbreviation parenthetical ("(lat, long)"), a cognate
  ("(experts)"). The shared suffix must contain real English prose words beyond those.
- English regional variants (`en-GB`) share most of their text with the source by definition,
  so they are exempt from the English-detection checks entirely.

`tools/salvage-translations/annotate_v1.py` writes a `SALVAGE-FLAGGED` comment above each
affected string **in v1**, with the specific defect, so the damage is visible where it lives.
It re-derives the list from `salvage.py`'s own verdicts rather than a hardcoded table, and is
idempotent.

**Point it at the right v1.** v1 is maintained in its own repository
(`sky-map-team/stardroid`); the `stardroid-v1/` beside `stardroid-v2/` in this monorepo is a
reference snapshot, and the two are *not* identical — the real repo has a Galician locale the
snapshot lacks. Pass `--v1-res` to target the checkout that actually gets committed:

```
python3 stardroid-v2/tools/salvage-translations/annotate_v1.py \
    --v1-res /path/to/stardroid/stardroid-v1/app/src/main/res --apply
```

The defects are re-derived against whichever tree is given, never copied from a previous run,
so pointing it at a different checkout cannot carry stale verdicts across. The annotations for
the 50 real defects landed on the `flag-damaged-translations` branch of the v1 repo.

### Results

Against v1's 32 locales, `--apply` carries **213 of 337 strings (63%)** into 31 locale files,
taking most locales from zero to ~50% coverage (`ar` 55%, `ja` 54%, `de` 51%; `ru` lags at 32%
because v1's Russian is itself sparse). `tm validate` passes for all 31, `./gradlew check` and
`assembleGmsDebug` are clean, and a sweep for known English phrases finds no leaks into
non-English locales. The remainder — 30 fuzzy, 92 unmatched, 2 caps-mismatch, 3 ambiguous — is
itemized in `tools/salvage-translations/review-manifest.md`.

### Handling what's left

The manifest is sorted by how it should be dispatched, not just by similarity:

| Tier | Count | Disposition |
|---|---:|---|
| Title-case only | 8 | **Reviewed and applied.** Capitalization changed in English only (`Solar System` → `Solar system`), and v1's translations already follow each language's own convention (fr `Système solaire`; Japanese has no case), so the change carries no information into them. |
| Caps mismatch | 2 | `diagnostics_east`/`_west` — never reuse; the v1 string is the ALL-CAPS map label. `tm` translates fresh. |
| Ambiguous | 3 | Applied, but several v1 keys shared the English. Worth an eyeball; low risk since the alternates are synonyms in context. |
| Fuzzy | 30 | Genuinely reworded — send to `tm`. Nine are pure format-specifier changes (`%s` → `%1$s`) whose translations are otherwise identical and would be cheap manual carries if the budget mattered. |
| Unmatched | 92 | New in v2 (AR mode, HUD, share, settings). `tm` translates. Mostly very short — 60 are under 20 characters. |
| v1 damage | 50 | Excluded deliberately and annotated in v1. Do **not** hand-carry these. |

Everything still outstanding converges on one action: `tm translate --all-primary`, which fills
every remaining tier plus the retranslated help sections in a single pass. Nothing needs
resolving by hand first — the manifest's value is that it records *why* each string was not
carried, so a reviewer can spot-check the tool's judgment rather than re-derive it.

The one genuine decision was the title-case tier, since reuse-vs-retranslate there is a
cost/quality tradeoff rather than a technical fact. It was resolved in favour of reuse on the
evidence above.

---

## 3. Implications of v2's data architecture

Three distinct questions here; the answers differ.

### 3a. Object names and info cards are already localized — and out of `tm`'s reach

This is the big architectural difference from v1. v1 stored celestial object names and info
card text in per-locale string resources (`celestial_objects.xml`, `celestial_info_cards.xml`)
and `tm` translated them like any other resource file. v2 stores them in the generated Room DB
(D34): `source-data/names/*.csv` (31 locales) and `source-data/info_cards/*.json` (30 locales),
harvested from v1's resources and compiled into `skymap.db` with locale-fallback resolution
at query time.

Consequences:

- **The existing 30 locales of object names and card text are already in v2.** This is a large
  body of translated content that requires no migration work at all — it was carried over in
  slice 4c. The `tm` reuse analysis in §2 covers *UI chrome only*.
- **`tm` cannot translate new catalog content as configured.** Its `[files] android` list
  enumerates XML resource files; v2's catalog sources are CSV and JSON outside `res/`. When a
  new object is added (the `skymap.add-object` skill), its name and card text land in
  English-only source data and no existing pipeline translates them.
- **Recommendation:** extend `tm` with a source-data mode, or add a sibling generator step
  that translates new/changed rows in `source-data/names/` and `source-data/info_cards/`
  against the same glossary and locale context. The glossary is *especially* valuable here —
  it exists precisely to pin canonical astronomical names per language. Until then, new
  objects are English-only in non-English locales, which the locale-fallback chain handles
  gracefully (falls back to English) but is a quiet content regression.

### 3b. The `tm` tool still works for UI strings

Yes, unchanged. v2's `strings.xml`/`help.xml`/`eula.xml`/`credits.xml`/`whatsnew.xml` are
ordinary Android resources in `app/src/main/res/`. What v2 needs is its own `.tmconfig.toml`
(it has none today), which should be adapted from v1's rather than written fresh — v1's
version encodes hard-won knowledge:

- The brand-name preservation rule in `translation_notes` (added in v1 PR #901) that fixed
  Gemini translating "Sky Map" to "Map Awyr" in Welsh.
- Per-locale register guidance (informal `du`/`je`/`du` for de/nl/da/sv/nb; the Dutch
  `het kompas` gender fix).
- The `[language_models]` override mechanism.
- The glossary path.

Deltas for v2's config: `res_path` becomes `app/src/main/res`, the `[files] android` list
drops v1-only files (`celestial_objects.xml`, `celestial_info_cards.xml`, `arrays.xml`) and
adds v2's set, and `android_skip` must list `contributors.xml`. Note `credits.xml` is a
translatable file in both, but see §1 on splitting out the proper nouns first.

Model selection should follow v1's findings rather than being re-derived: Gemini 2.5 Flash for
short strings, and for long-form text the §1 chunking meaningfully de-risks the model choice —
sub-1 KB chunks avoid the JSON-truncation failures that hit Sonnet on `hi`/`th` with a 14 KB
document.

### 3c. Will Google Play strip unused locales?

**Partially — and the DB is the part it can't touch.** This matters because v2's catalog DB is
~7 MB with all ~30 locales bundled.

- **Android App Bundle language splits** cover `res/values-<locale>/` only. Play serves a
  device just the resource splits matching its configured locales, so the §2 UI-string work
  costs users essentially nothing in download size regardless of how many locales exist.
- **`assets/` is not split by language.** `skymap.db` is an asset, so every user downloads all
  ~30 locales of names and info-card text. Play's language splitting has no visibility into
  the contents of a SQLite file.
- This was an accepted trade in D34 ("All ~30 name/card locales are bundled… the DB is ~7 MB —
  the card text it carries replaces v1's equivalent per-locale string resources, so the net
  app-size change is far smaller than the raw number suggests"). The reasoning holds: v1 shipped
  that same text as per-locale resources, so v2's DB is not a regression against v1 in
  aggregate — but it *is* a regression against what bundle splitting would otherwise have
  achieved, since v1's copy was splittable and v2's is not.
- **No action proposed now.** If DB size becomes a concern, the lever already exists: the pack
  mechanism (`PackDao`, `PackReplacementTest`) is designed for downloadable catalog content, so
  per-locale card text could become an on-demand pack. That is a real project, not a
  configuration flag, and should wait for evidence that size actually matters.

One thing to verify before shipping locales: v2's `app/build.gradle.kts` sets no
`resourceConfigurations` filter. That is correct — leaving it unset lets bundle splitting do
its job. It should stay unset.

---

## Proposed order of work

1. Split the English `help.xml` into ~13 section keys; split proper nouns out of
   `credits.xml`. Do this **before** any translation run — it defines the units that get
   translated and the units that get retranslated forever after. No per-locale splitting:
   the help document is retranslated from scratch (§1).
2. Add `stardroid-v2/.tmconfig.toml`, adapted from v1's with the deltas in §3b.
3. Write the one-shot v1 → v2 migration script; auto-apply A/B, emit a review manifest for
   C–E. Include the format-specifier validation.
4. Review the fuzzy carry-overs, then run `tm` for category F and everything review rejected.
5. Separately, decide whether to extend `tm` to `source-data/` for new catalog objects (§3a).

Steps 1–2 are prerequisites for everything else and are where the leverage is: chunking first
means the first full translation run establishes the per-section units, rather than
establishing a monolith that every future edit pays for.

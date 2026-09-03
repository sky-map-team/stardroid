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
  primary.** Resolved with an amendment: **universal is not absolute**. See
  [Universal names are not universal](#universal-names-are-not-universal).
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

This needs a unit-formatting layer that does not exist yet. That is the main implementation
cost of this decision and should be scoped in the design doc.

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

### 5. Coverage is per-locale, not counted against English

If Spanish genuinely has one distinct name where English has five aliases, that object is
**100% covered** in Spanish. A locale's name list is complete when someone has written it,
regardless of length.

Counting against English is what created the duplicates: it structurally rewards writing
"Alfa Centauri" four times to satisfy a counter. This needs a per-locale "this list is
complete" flag in `tm`, analogous to `same_as_parent`.

### 6. The generated DB is pruned to the 28 core languages

`ca` and `hu` carry 16 names and 145 partial card sets each while being deliberately excluded
from `primary_languages`; `ru` has 172 names and no info cards at all. The shipped catalog
should match what `tm` actually maintains. This is not a change to
[the intentional exclusion of ca/hu/ru](../../AGENTS.md) — it is making the build agree with it.

---

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

## Universal names are not universal

The requirement that universal names are never translated does not survive contact with the
corpus. Two genuine exceptions, plus one bug.

**Scripts with their own numerals re-render designations.** Persian writes Caldwell 22 as
`کالدول ۲۲`, Melotte 25 as `Melotte ۲۵`, NGC 7662 as `NGC ۷۶۶۲`. That is why `fa` has three
names for Blue Snowball where every other locale has one.

**Compound designations contain a translatable connective.** `NGC 869 & NGC 884` becomes
`NGC 869 y NGC 884` in Spanish and `NGC 869 و NGC 884` in Arabic; `h and χ Persei` becomes
`h a χ Persei` in Czech and `h und χ Persei` in German. English has *two* names for Double
Cluster while translated locales have three, precisely because English's compound form sits in
`universal.csv` and every other locale had to write its own.

**Resolution: split pure designations from compound ones.** A pure catalog ID (`M31`,
`NGC 7662`, `Mel 25`) is immutable and never translated. A compound or connective-bearing form
is prose: it leaves `universal.csv` entirely and becomes a locale-specific name in every locale
including `en`. Not every object has a universal name, and that is fine.

The Persian numeral case is left as a locale override on the universal tier — the mechanism the
data already uses. This means "never translated" is an invariant `tm` can enforce for pure
designations only, which is the honest version of the rule.

### Why counts differ

For the record, since this drove several of the decisions above — 59 of 563 objects have locales
disagreeing on name count, from three causes:

1. Locale-specific renderings of universal designations (genuine — Persian numerals).
2. Compound designations with a translatable connective (genuine — resolved by the split above).
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
- **Compound designations sitting in `universal.csv`** (see above).
- **`ru` has names but no info cards; `ca`/`hu` have both but are non-core** (Decision 6).

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

- The unit-formatting layer for `size`/`distance`/`mass` (Decision 1) — what it looks like, and
  whether ~318 size values and 240 distances are cleanly parseable back into numbers.
- Whether `tm` needs a new source type for the two-tier card model, or whether bundled cards
  simply become part of the existing `android` source and the `json-cards` source is retired for
  bundled content and kept for packs.
- The per-locale "list is complete" flag (Decision 5) — its representation, and whether it is
  in-band or joins `same_as_parent` in the sidecar.
- **Whether Play auto-fetches a language split for a per-app locale selection.** Settle it on a
  device rather than from documentation: build the `gms` AAB, `bundletool build-apks` +
  `install-apks` with a restricted language set, then change the system language and Sky Map's
  per-app language separately and watch whether the split arrives. If it does not, UI strings
  need `SplitInstallManager` regardless of what happens to card prose.
- How the in-app coverage diagnostic distinguishes a delivery gap from a coverage gap
  (Decision 3).

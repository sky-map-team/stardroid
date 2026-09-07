# Contributing translations to Sky Map v2

Thank you for improving Sky Map's translations! Translation PRs are very welcome — this
guide covers what to translate, how to mark your work as human-reviewed, and how to
submit it.

> Sky Map v2's strings run through an AI translation pipeline (the `tm` CLI, configured
> in `stardroid-v2/.tmconfig.toml`) that periodically retranslates stale or missing
> content. A hand-written or hand-verified string must be flagged so the pipeline never
> overwrites it — see [Marking your work as human-reviewed](#marking-your-work-as-human-reviewed).

## Before you start

- Accept the [Contributor License Agreement](../../CLA.md) when the bot prompts you on
  your PR — required for all contributions, one time only.
- Make your changes on a feature branch, and combine them into a **single commit**
  before requesting review (see the repo's [CONTRIBUTING.md](../../CONTRIBUTING.md)).
- Run `./gradlew check` from `stardroid-v2/` before opening the PR — it lints the
  resource files as well as building.

## What to translate

Translate natural-language UI text: labels, dialog copy, help/what's-new content, and
celestial object/type names where a language has an established term.

**Leave these untouched:**

- Names of people and organizations
- URLs
- Product names (`Sky Map`, `Android`)
- Error codes, IDs, and other technical keys
- Format placeholders (`%1$s`, `%s`, `%1$d`, etc.)
- XML/HTML markup (`<blockquote>`, `<b>`, `<br/>`, `&lt;`/`&gt;` entities)
- Catalog designations meant to stay canonical (`M31`, `NGC 224`)

For celestial object and type names specifically: use the established name in your
language as used in Wikipedia/scientific literature. If none exists, keep the English
or catalog name rather than inventing a translation — see `.tmconfig.toml`'s `[app]`
and `[locale_context]` tables for per-language conventions already documented for the
automated pipeline (they apply to human translators too), and the shared glossary at
[`translation_glossary.md`](../../translation_glossary.md).

## Marking your work as human-reviewed

How you mark a string as human-verified depends on which file you're editing. Only one
of the two mechanisms below is discoverable by reading the file, so read both.

### Android string resources (`values-*/strings.xml`, `whatsnew.xml`, `credits.xml`, `help.xml`, `eula.xml`)

Add `tm_human="true"` directly on the `<string>` (or `<string-array>`/`<plurals>`)
element:

```xml
<string name="calibration_heading_warning" tm_human="true">La bussola del tuo telefono richiede la tua attenzione!</string>
```

This is visible in the file itself and travels with the string in every future diff —
no other step needed. For real examples, see any locale under
`app/src/main/res/values-fr/`.

### Catalog source data (`source-data/types.json`, `source-data/names/*.csv`, `source-data/info_cards/*`)

These formats have **no in-file flag**. Human-verified status for them is tracked
out-of-band in the `.tmstate.toml` sidecar at the `stardroid-v2/` module root, which is
only ever written by the `tm` CLI — a contributor hand-editing the JSON/CSV directly
currently has no way to flag their edit as human-verified, and nothing in the files
tells them that sidecar exists.

Until that gap is closed, the supported paths are:

- If you have the `tm` CLI set up, run it against your edit so the state file records
  your values as human-authored.
  <!-- TODO(maintainer): replace with the exact supported invocation, verified against
       the tm CLI — e.g. does `tm translate <locale> --source <source> -k <key> --force`
       write the value *and* mark it human-translated? -->
- If you don't, say so in your PR description (as [#985](https://github.com/sky-map-team/stardroid/pull/985)
  did) and ask a maintainer to run it, or to hand-flag the affected keys, before merge.
  **Don't assume a plain hand-edit to one of these files is protected** — without the
  flag, a future `tm translate --include-stale` run may silently retranslate it.

## Opening the PR

- Note in the PR description what you changed and why (wording fixes, missing entries,
  etc.) — see [#985](https://github.com/sky-map-team/stardroid/pull/985) for a good
  example.
- If you notice an issue with the **English source** while translating, mention it but
  leave it out of the translation PR — file a separate issue or PR for the English text.
- Keep PRs small and focused; a wording pass and a new-locale addition are better as
  two PRs than one.

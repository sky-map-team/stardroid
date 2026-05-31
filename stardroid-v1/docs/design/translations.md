# Translations
The bulk of the languages are now translate using machine translation using our own `tm` tool.  Human corrects are still welcome since the machines don't do a perfect job.  The configuration for the tool is defined in .tmconfig.toml.  We recently did some analysis on which ML models do best for
translation.  This was done by Claude.

# Sky Map Translation Model Comparison
  
  Comparing three LLM translation providers across two string sets from PR #895:
  - **3 short compass strings** (`strings.xml`)
  - **1 long-form help document** (`help.xml`)
  
  **Models tested:**
  - `claude-haiku-4-5` (default/cheapest Anthropic)
  - `claude-sonnet-4-6` (mid-tier Anthropic)
  - `gemini-2.5-flash` (Google, default)

  **Pricing (per million tokens):**

  | Model | Input | Output |
  |---|---|---|
  | Gemini 2.5 Flash | $0.30 | $2.50 |
  | Claude Haiku 4.5 | $1.00 | $5.00 |
  | Claude Sonnet 4.6 | $3.00 | $15.00 |

  ---

  ## Part 1: Short strings (`strings.xml`)

  Keys retranslated across 26 primary locales:
  - `compass_calibration_activity_warning`
  - `compass_calibration_activity_user_heading`
  - `compass_low_accuracy_toast`

  Context: PR #895 overhauled compass/calibration messaging to say the compass "needs attention"
  rather than "needs calibration", deliberately avoiding blame language.

  18 of 26 locales were identical across all three models. Differences appeared in 13 languages.

  ### Sonnet clearly better than Haiku

  **Welsh (cy) — significant error in Haiku**
  Haiku produced "Gwellt Magnetig" for "compass" (meaningless/wrong). Sonnet and Gemini both
  correctly used "Cwmpawd" — the actual Welsh word for compass.

  **Dutch (nl) — grammatical errors in Haiku**
  Haiku wrote "De kompas" (wrong grammatical gender; Dutch is "het kompas") and "uw" (formal
  register). Sonnet correctly writes "Het kompas" and uses the informal "je", consistent with
  the rest of the app's Dutch strings.

  **Slovenian (sl) — grammatical error in Haiku**
  For `compass_low_accuracy_toast`, Haiku wrote "poroča nizko natančnost" — missing the required
  preposition. Sonnet wrote "poroča o nizki natančnosti" (correct government/locative).

  **Arabic (ar) — faithfulness issue in Haiku**
  PR #895 deliberately changed "calibration" to "needs attention". Haiku translated back to
  "معايرة" (calibration). Sonnet and Gemini both correctly used attention-based phrasing.

  **Japanese (ja)**
  Haiku: "お使いのスマートフォンのコンパスに注意が必要です" (stilted, passive)
  Sonnet: "スマートフォンのコンパスを調整してください" (natural, direct action prompt)
  Gemini: "スマートフォンのコンパスの調整が必要です" (neutral status message — most appropriate)

  **Thai (th)**
  Sonnet and Gemini both drop a redundant double "ของ" particle and use more natural phrasing
  for the low-accuracy toast. Haiku is verbose.

  **Persian (fa)**
  Sonnet alone uses "گوشی" (the natural colloquial word for mobile phone) vs. "تلفن" in Haiku
  and Gemini. Minor but more idiomatic.

  **Italian (it)**
  Sonnet's "segnala" (simple present) is more natural than Haiku's "sta segnalando" (present
  continuous) for a hardware state.

  ### Haiku better (more faithful to PR intent)

  **Norwegian (nb) and Swedish (sv)**
  Sonnet translated "needs attention" as "needs calibration" ("trenger kalibrering" /
  "behöver kalibreras") — directly contradicting PR #895's purpose. Haiku and Gemini both
  correctly rendered it as "needs attention".

  **German (de)**
  Sonnet switched to formal "Sie/Ihres" but the rest of the app's German strings use informal
  "du". Haiku is more consistent with the existing register. (Gemini also chose formal "Sie".)

  ### Short-string verdict

  | Model | Strengths | Weaknesses | PR |
  |---|---|---|---|
  | **Haiku** | Faithful in nb/sv; informal German consistent with app | Welsh wrong word; Dutch gender wrong; Slovenian grammar wrong; Arabic wrong | https://github.com/sky-map-team/stardroid/pull/900 |
  | **Sonnet** | Fixes all Haiku grammar errors; best Persian | "Calibration" regression in nb/sv — contradicts PR intent | https://github.com/sky-map-team/stardroid/pull/898 |
  | **Gemini** | Fixes all Haiku grammar errors; best Japanese, Greek, Arabic, Chinese; faithful in nb/sv | Formal "Sie" in German; slightly awkward Welsh word order | https://github.com/sky-map-team/stardroid/pull/899 |

  **Gemini is the strongest overall for short strings**, narrowly ahead of Sonnet. Two manual
  fixes recommended before merging: German register (formal → informal) in the Gemini branch;
  nb/sv calibration regression in the Sonnet branch.

  ---

  ## Part 2: Long-form help text (`help.xml`)

  One key (`help_text`) retranslated — a 14 KB HTML document covering app introduction,
  hardware requirements, sensor explanation, and troubleshooting.

  ### New issues not visible in the short-string test

  **Welsh (cy) — Gemini critically wrong, Haiku and Sonnet correct**
  Gemini translates the app name "Sky Map" to "Map Awyr" throughout the entire help document.
  App names are brand identifiers and must not be translated. Both Haiku and Sonnet correctly
  preserve "Sky Map". This reverses Gemini's Welsh advantage from Part 1.

  **Japanese (ja) — Gemini has a literal rendering bug**
  Gemini produced `スマートフォン'sのコンパス` — an English possessive apostrophe-s embedded
  in Japanese text. This is a hard rendering error. Sonnet handles the possessive correctly
  using standard Japanese grammatical construction. Haiku is also clean, though it leaves the
  `<h1>` as "Sky Map for Android" rather than the more idiomatic "Android版 Sky Map" that
  Sonnet and Gemini both use.

  **Sonnet reliability — 2 parse failures on long text**
  Hindi (hi) and Thai (th) both failed with a JSON truncation error during batch processing.
  The existing master translations were left unchanged for those locales on the Sonnet branch.
  This is a practical reliability concern for long-form text that does not affect Haiku or Gemini.

  ### Patterns that held from Part 1

  **Dutch (nl)** — Haiku still writes "de kompas" (wrong gender) in the help text. Sonnet
  correctly uses "het kompas" with informal register. Gemini uses "het kompas" (correct gender)
  but formal "uw" (inconsistent with app's established informal Dutch voice).

  **Arabic (ar) and Hindi (hi) — app name translation**
  Both Sonnet and Gemini translate "Sky Map" to its literal meaning in Arabic ("خريطة السماء")
  and Hindi ("स्काईमैप"). Haiku keeps "Sky Map" as an untranslated brand name, which is the
  correct convention for an established published app.

  **German (de) — register reverses for long-form text**
  For the help document, Haiku and Gemini use formal "Sie/Ihr/Telefon" throughout, while
  Sonnet uses informal "du/dein/Handy". Sonnet's "Handy" is the most natural German word for
  a mobile phone and the informal register is consistent with modern Android app conventions
  in German. This is a Sonnet win — though it conflicts with what Sonnet produced for the
  short strings.

  ### Long-form verdict

  | Model | Strengths | Weaknesses |
  |---|---|---|
  | **Haiku** | Preserves brand names correctly; no failures; reliable | Dutch gender error persists; formal German |
  | **Sonnet** | Best German (informal + "Handy"); correct Dutch gender; correct Welsh; correct Japanese | 2 parse failures (hi, th); formal German on short strings |
  | **Gemini** | Concise output; strong for most European languages | Translates app name in Welsh; `'s` possessive bug in Japanese; formal Dutch; formal German |

  **The Gemini quality lead from Part 1 does not hold for long-form text.** The Welsh app-name
  translation and the Japanese possessive error are both outright bugs. **Sonnet is the
  strongest for help.xml**, with the caveat that Hindi and Thai need a retry. Haiku remains
  a reliable and predictable fallback.

  ---

  ## Overall conclusions

  1. **No single model wins across all languages and string types.** Quality is task- and
     language-dependent.

  2. **Gemini 2.5 Flash is the best value for short UI strings** — cheapest at ~3× less than
     Haiku and ~10× less than Sonnet, and competitive or superior quality for most locales.
     The Welsh/Japanese bugs only surfaced in long-form text.

  3. **Sonnet is the safest choice for long-form documents** — more reliable output, better
     brand name preservation, and the strongest German. Budget accordingly (~10× Gemini cost).

  4. **Haiku is a reliable baseline** — it made the fewest outright errors on long-form text
     but has persistent issues with Dutch grammatical gender and can be unfaithful to nuanced
     source intent (Arabic/nb/sv calibration language).

  5. **Dutch needs post-edit review regardless of model** — Dutch grammatical gender is a
     recurring failure mode for Haiku specifically. Welsh has since been resolved — see follow-up
     analysis below.

  6. **For the PR #895 strings specifically**, the recommended merge candidate is the Gemini
     branch with two manual fixes: German register (Sie → du) and nb/sv phrasing
     ("calibration" → "attention").

---

## Follow-up: Welsh help text quality (PRs #898–#902)

The original analysis flagged Gemini as broken for Welsh because it translated the app name
"Sky Map" as "Map Awyr". That was fixed by adding an explicit brand-name preservation rule to
`translation_notes` in `.tmconfig.toml`. A subsequent comparison of four Welsh help-text
translation attempts (PRs #898, #899, #900, #902) confirmed Gemini is now the strongest model
for Welsh and the Sonnet override should be removed.

### What was compared

| PR | Model | Notes |
|---|---|---|
| #898 | Claude Sonnet 4.6 | First Sonnet attempt |
| #899 | Claude Sonnet 4.6 | Second Sonnet attempt (near-identical to #898) |
| #900 | Gemini 2.5 Flash | First Gemini attempt after brand-name fix |
| #902 | Gemini 2.5 Flash | Second Gemini attempt (current) |

### Errors found in Sonnet translations (#898 and #899)

Both Sonnet PRs contained the same set of Welsh grammar errors:

- **Wrong word — "crynhoi" vs "crynu"**: The section heading "Mae'r map yn crynhoi" means
  *"the map is summarising"*, not *"the map is shaking"*. The correct Welsh verb is `crynu`.
- **Gender agreement — "tair peth" vs "tri pheth"**: `peth` (thing) is masculine in Welsh,
  requiring the masculine numeral `tri` with aspirate mutation: `tri pheth`. Sonnet used
  the feminine form `tair`.
- **Soft mutation missing — "saeth cyfeiriadol"**: `saeth` (arrow) is feminine; the following
  adjective requires soft mutation: `saeth gyfeiriadol`.
- **Wrong register — "Diswyddwch"**: Used to mean "dismiss a dialog". `Diswyddo` means
  *"to dismiss/fire someone from employment"* — entirely wrong context.
- **Mutation after "a"**: `"a Dechrau Cyflym"` — after the conjunction `a` (and), `D` must
  soft-mutate to `Dd`: `"a Ddechrau Cyflym"`.
- **Cyrillic character**: `"gyrоsgop"` contained a Cyrillic `о` character instead of Latin `o`.
- **Awkward terminology**: `"Modd Llawlyfr"` for Manual mode — `llawlyfr` means "handbook"
  (a noun), not the adjective "manual". The cleaner form is `"Modd Llaw"`.

### Errors found in Gemini #900

- Mutation error: `"a Cychwyn Cyflym"` should be `"a Chychwyn Cyflym"` (C→Ch after `a`)
- Used English cardinal direction letters `N, S, E, W` instead of Welsh equivalents
- Inconsistent imperatives: mixed bare infinitives (`Tapu`, `Gwirio`) with `-wch` forms
- `"galaxïau"` (English loan plural) instead of `"Galaethau"` (correct Welsh plural)
- `"system solar"` instead of `"cysawd yr haul"` (proper Welsh term for solar system)
- `"daparol"` for "upcoming" — not a recognised standard Welsh word

### Gemini #902 quality

PR #902 is grammatically sound and consistently idiomatic:
- Correct soft mutations throughout (including `"Chychwyn"` after `a`)
- Consistent formal register (`eich/chi`, `-wch` imperative forms) throughout the document
- Proper Welsh vocabulary: `cysawd yr haul`, `Galaethau`, `Sadwrn` (Saturn), `h.y.` (i.e.)
- Welsh cardinal abbreviations rather than English letters
- Minor: `heuldroeau` plural is slightly irregular (standard form is `heuldroeon`); no impact
  on comprehension

### Conclusion

Gemini is now the recommended model for Welsh. The Sonnet override in `.tmconfig.toml` has
been removed. The "Map Awyr" brand-name bug that originally prompted the Sonnet override is
prevented by the `translation_notes` rule added in PR #901.

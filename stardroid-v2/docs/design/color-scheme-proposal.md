# v2 Color Scheme Proposal — "Deep Space / Star Gold"

**Status**: **accepted and implemented** — see D73 in `../decisions.md`. Both open questions
were resolved in favour of option (b) for `status_ok` (`#DCE775`) and the indigo
time-travel flash (`#3D2F74`). This document is kept as the rationale and the before/after
reference; D73 is the authoritative record.
**Branch**: `feature/v2-brand-colors`.

The v2 day-mode theme is currently `darkColorScheme()` with no arguments
(`ui/theme/Theme.kt:74`) — the unmodified Material 3 baseline, which is where the purple
comes from. This proposes a branded replacement whose lineage back to v1 and to the
launcher icon is explicit.

Night mode is **not** changed by this proposal. It is already fully custom, correctly
red-shifted, and well commented.

---

## 1. Where the brand already lives

The palette is not invented here; three sources already agree.

**v1's documented logo palette** (`stardroid-v1/docs/design/visual_design.md`):

| Role | Hex | Swatch |
|---|---|---|
| Deep Space | `#0D1B2A` | $${\color{#0D1B2A}\rule{80px}{18px}}$$ |
| Star Gold | `#FF9F1C` | $${\color{#FF9F1C}\rule{80px}{18px}}$$ |
| Starlight | `#E0E1DD` | $${\color{#E0E1DD}\rule{80px}{18px}}$$ |
| Lens Blue | `#7EC8E3` | $${\color{#7EC8E3}\rule{80px}{18px}}$$ |
| Planet Green | `#4DB848` | $${\color{#4DB848}\rule{80px}{18px}}$$ |
| Planet Red | `#E05C34` | $${\color{#E05C34}\rule{80px}{18px}}$$ |

**The v2 launcher icon** (`res/drawable/ic_launcher_*.xml`) independently uses the same
family — amber `#FFC107`, lens blue `#7EC8E3`, planet green `#4DB848`, planet red
`#E05C34`, over a navy gradient `#060819` → `#1A1F4E` → `#3D2F74`.

**The existing v2 launch path** already ships navy + gold: `splash_navy` `#0B1130` in
`colors.xml`, and `VersionBanner.kt` hardcodes that navy with Star Gold `#FFC107` and ink
`#EEF2FF`.

So cold start is *already* on-brand, and then hands off to stock purple. Closing that gap
is the whole point of this change.

**Why amber as the accent**, beyond brand match: it is the least dark-adaptation-hostile
of the bright hues, so day mode degrades gracefully toward night mode instead of fighting
it. Both modes then share one logic — *warm accent on near-black* — gold normally, red in
night mode.

---

## 2. Proposed color scheme

Baseline "before" values were extracted from the actual Material 3 **1.3.1** artifact in
the Gradle cache (`PaletteTokens` / `ColorDarkTokens`), not recalled from memory.

### Core roles

| Role | Before (M3 baseline) | | After (proposed) | | Contrast |
|---|---|---|---|---|---|
| `primary` | `#D0BCFF` | $${\color{#D0BCFF}\rule{50px}{16px}}$$ | `#FFC107` Star Gold | $${\color{#FFC107}\rule{50px}{16px}}$$ | 11.2:1 |
| `onPrimary` | `#381E72` | $${\color{#381E72}\rule{50px}{16px}}$$ | `#231A00` | $${\color{#231A00}\rule{50px}{16px}}$$ | 10.6:1 |
| `primaryContainer` | `#4F378B` | $${\color{#4F378B}\rule{50px}{16px}}$$ | `#5A4200` | $${\color{#5A4200}\rule{50px}{16px}}$$ | — |
| `onPrimaryContainer` | `#EADDFF` | $${\color{#EADDFF}\rule{50px}{16px}}$$ | `#FFDF9C` | $${\color{#FFDF9C}\rule{50px}{16px}}$$ | 7.4:1 |
| `secondary` | `#CCC2DC` | $${\color{#CCC2DC}\rule{50px}{16px}}$$ | `#7EC8E3` Lens Blue | $${\color{#7EC8E3}\rule{50px}{16px}}$$ | 9.8:1 |
| `onSecondary` | `#332D41` | $${\color{#332D41}\rule{50px}{16px}}$$ | `#00344A` | $${\color{#00344A}\rule{50px}{16px}}$$ | 7.1:1 |
| `secondaryContainer` | `#4A4458` | $${\color{#4A4458}\rule{50px}{16px}}$$ | `#1E3A4C` | $${\color{#1E3A4C}\rule{50px}{16px}}$$ | — |
| `onSecondaryContainer` | `#E8DEF8` | $${\color{#E8DEF8}\rule{50px}{16px}}$$ | `#9ED4EC` | $${\color{#9ED4EC}\rule{50px}{16px}}$$ | 7.4:1 |
| `tertiary` | `#EFB8C8` | $${\color{#EFB8C8}\rule{50px}{16px}}$$ | `#F5C77E` | $${\color{#F5C77E}\rule{50px}{16px}}$$ | 11.6:1 |
| `onTertiary` | `#492532` | $${\color{#492532}\rule{50px}{16px}}$$ | `#3D2A00` | $${\color{#3D2A00}\rule{50px}{16px}}$$ | — |
| `tertiaryContainer` | `#633B48` | $${\color{#633B48}\rule{50px}{16px}}$$ | `#4A3510` | $${\color{#4A3510}\rule{50px}{16px}}$$ | — |
| `onTertiaryContainer` | `#FFD8E4` | $${\color{#FFD8E4}\rule{50px}{16px}}$$ | `#F5C77E` | $${\color{#F5C77E}\rule{50px}{16px}}$$ | 7.4:1 |
| `background` | `#141218` | $${\color{#141218}\rule{50px}{16px}}$$ | `#080C1C` | $${\color{#080C1C}\rule{50px}{16px}}$$ | — |
| `onBackground` | `#E6E0E9` | $${\color{#E6E0E9}\rule{50px}{16px}}$$ | `#E0E1DD` Starlight | $${\color{#E0E1DD}\rule{50px}{16px}}$$ | 14.6:1 |
| `surface` | `#141218` | $${\color{#141218}\rule{50px}{16px}}$$ | `#0E1428` | $${\color{#0E1428}\rule{50px}{16px}}$$ | — |
| `onSurface` | `#E6E0E9` | $${\color{#E6E0E9}\rule{50px}{16px}}$$ | `#E0E1DD` Starlight | $${\color{#E0E1DD}\rule{50px}{16px}}$$ | 13.9:1 |
| `surfaceVariant` | `#49454F` | $${\color{#49454F}\rule{50px}{16px}}$$ | `#1B2340` | $${\color{#1B2340}\rule{50px}{16px}}$$ | — |
| `onSurfaceVariant` | `#CAC4D0` | $${\color{#CAC4D0}\rule{50px}{16px}}$$ | `#A9B4D0` | $${\color{#A9B4D0}\rule{50px}{16px}}$$ | 8.8:1 |
| `outline` | `#938F99` | $${\color{#938F99}\rule{50px}{16px}}$$ | `#5A6486` | $${\color{#5A6486}\rule{50px}{16px}}$$ | 3.1:1 † |
| `outlineVariant` | `#49454F` | $${\color{#49454F}\rule{50px}{16px}}$$ | `#2A3355` | $${\color{#2A3355}\rule{50px}{16px}}$$ | — |
| `error` | `#F2B8B5` | $${\color{#F2B8B5}\rule{50px}{16px}}$$ | `#FF7A5C` | $${\color{#FF7A5C}\rule{50px}{16px}}$$ | 7.1:1 |
| `onError` | `#601410` | $${\color{#601410}\rule{50px}{16px}}$$ | `#4A0E00` | $${\color{#4A0E00}\rule{50px}{16px}}$$ | 6.1:1 |
| `errorContainer` | `#8C1D18` | $${\color{#8C1D18}\rule{50px}{16px}}$$ | `#6E2010` | $${\color{#6E2010}\rule{50px}{16px}}$$ | — |
| `inverseSurface` | `#E6E0E9` | $${\color{#E6E0E9}\rule{50px}{16px}}$$ | `#E0E1DD` | $${\color{#E0E1DD}\rule{50px}{16px}}$$ | — |
| `inverseOnSurface` | `#322F35` | $${\color{#322F35}\rule{50px}{16px}}$$ | `#0E1428` | $${\color{#0E1428}\rule{50px}{16px}}$$ | 13.9:1 |
| `inversePrimary` | `#6750A4` | $${\color{#6750A4}\rule{50px}{16px}}$$ | `#7A5A00` | $${\color{#7A5A00}\rule{50px}{16px}}$$ | — |

† `outline` is deliberately below AA — it draws borders and dividers, not text. Where it
also carries the rail's "layer off" state it sits beside an icon shape, not alone.

### Surface container ramp

M3 components (bottom sheets, dialogs, cards, top bars on scroll) pull these implicitly.
Left unset they stay purple-tinted grey even after the core roles change, so they must be
specified.

| Role | Before | | After | |
|---|---|---|---|---|
| `surfaceDim` | `#141218` | $${\color{#141218}\rule{50px}{16px}}$$ | `#080C1C` | $${\color{#080C1C}\rule{50px}{16px}}$$ |
| `surfaceBright` | `#3B383E` | $${\color{#3B383E}\rule{50px}{16px}}$$ | `#232C4C` | $${\color{#232C4C}\rule{50px}{16px}}$$ |
| `surfaceContainerLowest` | `#0F0D13` | $${\color{#0F0D13}\rule{50px}{16px}}$$ | `#050813` | $${\color{#050813}\rule{50px}{16px}}$$ |
| `surfaceContainerLow` | `#1D1B20` | $${\color{#1D1B20}\rule{50px}{16px}}$$ | `#0E1428` | $${\color{#0E1428}\rule{50px}{16px}}$$ |
| `surfaceContainer` | `#211F26` | $${\color{#211F26}\rule{50px}{16px}}$$ | `#141C36` | $${\color{#141C36}\rule{50px}{16px}}$$ |
| `surfaceContainerHigh` | `#2B2930` | $${\color{#2B2930}\rule{50px}{16px}}$$ | `#1B2340` | $${\color{#1B2340}\rule{50px}{16px}}$$ |
| `surfaceContainerHighest` | `#36343B` | $${\color{#36343B}\rule{50px}{16px}}$$ | `#232C4C` | $${\color{#232C4C}\rule{50px}{16px}}$$ |

All are navy-tinted rather than neutral grey, so a dialog over the map reads as "deep sky",
not "grey panel".

---

## 3. What visibly changes

Almost all the purple arrives through M3 component *defaults*, not explicit references —
so replacing the scheme at `Theme.kt:74` fixes the large majority of it with no call-site
edits. The most prominent:

| Surface | Before | After |
|---|---|---|
| Search button (`MapChrome.kt:351`, `FilledIconButton`) | Purple `#D0BCFF` fill | Star Gold fill, dark-brown icon |
| Time travel / Night / Auto-Manual (`MapChrome.kt:359,367,386`, `FilledTonal`) | Purple-grey `#4A4458` | Deep lens-blue `#1E3A4C` |
| Time-travel player while sweeping (`TimeTravelUi.kt:88`, `tertiaryContainer`) | Mauve-pink `#633B48` | Warm amber-brown `#4A3510` |
| Layer rail checked state (`MapChrome.kt:313,322`) | Purple icon + purple wash | Gold icon + gold wash |
| HUD panel (`MapHud.kt:59-60`) | Purple-grey glass | Navy glass, Starlight values |
| All switches / radios / checkboxes | Purple | Gold |
| All `TextButton`s (~30 sites) | Purple label | Gold label |
| Dialogs, sheets, top bars | Purple-tinted grey | Navy |
| Date/time pickers (`TimeTravelUi.kt:270-330`) | Most purple-saturated screens in the app | Gold selection on navy |
| HTML links (`HtmlText.kt:30`) | Purple | Gold |
| Settings/Diagnostics section headers | Purple | Gold |

---

## 4. Status colors — the collision (your point 1)

`status_warning` is currently `#FF9800`, which against Star Gold `#FFC107` has a relative
contrast of only **1.32:1** — effectively the same color. Once amber is the brand accent,
an orange "sensor degraded" warning stops reading as a warning and reads as "normal accent".

| Status | Before | | After | | Note |
|---|---|---|---|---|---|
| `status_good` | `#8BC34A` | $${\color{#8BC34A}\rule{50px}{16px}}$$ | `#8BC34A` unchanged | $${\color{#8BC34A}\rule{50px}{16px}}$$ | near Planet Green already |
| `status_ok` | `#FFEB3B` | $${\color{#FFEB3B}\rule{50px}{16px}}$$ | **`#DCE775`** ‡ | $${\color{#DCE775}\rule{50px}{16px}}$$ | 1.33:1 vs gold — see below |
| `status_warning` | `#FF9800` | $${\color{#FF9800}\rule{50px}{16px}}$$ | **`#FF7043`** | $${\color{#FF7043}\rule{50px}{16px}}$$ | 1.32 → 1.68 vs gold; leans Planet Red |
| `status_bad` | `#E91E63` | $${\color{#E91E63}\rule{50px}{16px}}$$ | `#E91E63` unchanged | $${\color{#E91E63}\rule{50px}{16px}}$$ | magenta, unambiguous |
| `status_absent` | `#A0A0A0` | $${\color{#A0A0A0}\rule{50px}{16px}}$$ | `#A0A0A0` unchanged | $${\color{#A0A0A0}\rule{50px}{16px}}$$ | neutral by design |

‡ **A caveat I want to flag rather than quietly decide.** Running the numbers exposed that
`status_ok` `#FFEB3B` is 1.33:1 against gold — *the same collision the warning color has*.
Unlike the warning case this one is genuinely new: `status_ok` sits at 1.77:1 against the
old warning orange, so yellow is currently distinguishable from everything around it, and
introducing gold as the accent is what creates the ambiguity. Options:

- **(a)** Change warning only, as tabled above. Minimal, fixes the worst case.
- **(b)** Also shift `status_ok` toward `#DCE775` (yellow-green) to separate it from gold.
- **(c)** Leave both, accept that status colors are always adjacent to an icon/label.

Given that the yellow collision is created by this change rather than inherited, I lean
**(b)** — change both. Status indicators are always paired with a shape and label in
`DiagnosticsUi` / `CompassCalibrationUi`, so neither is load-bearing alone, but "acceptable"
and "brand accent" being the same yellow is a regression the rebrand introduces.

**On what metric to judge this by.** These colors are separated by *hue*, not luminance —
WCAG contrast ratio is the wrong tool, and applied naively it misleads: `#DCE775` scores
1.22:1 against gold, *worse* than the current `#FFEB3B`'s 1.33:1, while being obviously
more distinguishable. Measured by hue angle instead:

| | Hue | Δ vs gold (45°) | Δ vs `status_good` (88°) |
|---|---|---|---|
| `#FFEB3B` current | 54° | **8.9°** | 33.9° |
| `#DCE775` proposed | 66° | 20.8° | 22.0° |
| `#AEEA00` | 75° | 30.4° | 12.4° |

The constraint is that `status_ok` lives *between* gold and green, so separating it from
the accent pushes it toward `status_good`. `#DCE775` is near the balance point — roughly
equidistant from both — which is why it's the pick over a more aggressive yellow-green.

Same caveat applies to the warning color: `#FF7043` is 1.31:1 against `status_good` and
1.58:1 against `status_bad`, but those are green/orange/magenta — the ramp works by hue.
Don't read those ratios as a problem.

Night status colors are unchanged — red-shifted by design, no gold nearby.

---

## 5. Hardcoded surfaces — before / after (your point 2)

These bypass the theme entirely, so no scheme change reaches them. **Two of the three are
deliberate v1 ports**, which matters for how aggressively to touch them.

### 5a. Time-travel flash — `MapScreen.kt:812`

```kotlin
// v1's time-travel flash: the `view_mask` color (#990099) pulsed to 0.7 alpha and back
private val FLASH_COLOR = Color(0xFF990099)
```

A **full-screen magenta flash** at 0.7 alpha on time-travel engage. The loudest purple left
in the app, and a faithful port of v1's `view_mask`.

| | Color | Swatch |
|---|---|---|
| Before | `#990099` magenta | $${\color{#990099}\rule{90px}{18px}}$$ |
| Option A | `#3D2F74` icon indigo | $${\color{#3D2F74}\rule{90px}{18px}}$$ |
| Option B | `#B8860B` dark gold | $${\color{#B8860B}\rule{90px}{18px}}$$ |

**Recommend A** — the icon's own indigo. It keeps the "something unusual just happened"
jolt and stays in the navy family, whereas gold at 0.7 alpha over the sky is closer to a
white-out and could read as a rendering fault. This is a v1 behavior port, so if you'd
rather preserve it exactly, leaving it is defensible.

### 5b. Tour spotlight — `ChromeTour.kt:59`

```kotlin
/** v1's neon-cyan highlight, and its night-mode red counterpart (D46). */
private val NeonCyan = Color(0xFF00FFFF)
```

| | Color | Swatch | On navy |
|---|---|---|---|
| Before | `#00FFFF` neon cyan | $${\color{#00FFFF}\rule{90px}{18px}}$$ | 14.6:1 |
| After | `#7EC8E3` Lens Blue | $${\color{#7EC8E3}\rule{90px}{18px}}$$ | 9.8:1 |

Lens Blue is the same hue family, still well clear of AA, and on-brand. The spotlight
should stay *distinct* from gold (it points *at* gold controls), so keeping it blue rather
than moving it to the accent is deliberate. `NeonRed` for night mode is unchanged.

### 5c. HTML heading accents — `StyledHtml.kt:99`

Ported from v1's `help.css` (h1 blue, h2 amber, h3 salmon). The structure already mirrors
the brand; this just snaps the values onto it.

| Level | Before | | After | | |
|---|---|---|---|---|---|
| h1 | `#56B0F5` | $${\color{#56B0F5}\rule{50px}{16px}}$$ | `#7EC8E3` Lens Blue | $${\color{#7EC8E3}\rule{50px}{16px}}$$ | 9.8:1 |
| h2 | `#F5B056` | $${\color{#F5B056}\rule{50px}{16px}}$$ | `#FFC107` Star Gold | $${\color{#FFC107}\rule{50px}{16px}}$$ | 11.2:1 |
| h3 | `#F67E81` | $${\color{#F67E81}\rule{50px}{16px}}$$ | `#E8836A` Planet Red-lt | $${\color{#E8836A}\rule{50px}{16px}}$$ | 6.9:1 |

Night heading colors unchanged.

### 5d. Already on-brand — no change proposed

- `VersionBanner.kt:50-52,126` — navy `#0B1130`, Star Gold `#FFC107`, ink `#EEF2FF`, and
  the `#3D2F74`→`#1A1F4E`→`#060819` nebula gradient. This *is* the reference the rest of
  the scheme is being pulled toward.
- `WelcomeScreen.kt:81,84` — `#DD0B0F19` / `#55000000` scrims. Navy-black, correct.
- `colors.xml` `splash_navy` `#0B1130`.

**One inconsistency worth noting:** the banner/splash navy is `#0B1130` while the proposed
`surface` is `#0E1428` — close but not identical. They are never adjacent on screen (the
banner fades out before chrome appears), so I've kept the banner untouched. Say the word
if you'd rather unify them to a single navy token.

### 5e. Not addressed here

`SearchUi.kt:114-136` — procedural crosshair pulse and the red→blue distance ramp. These
encode *search distance*, not brand, and the ramp's hue range is functional. Changing them
is a separate UX question; flagging rather than folding in.

---

## 6. Implementation — as landed

1. `ui/theme/Theme.kt` — `darkColorScheme()` replaced with the full scheme in §2. Single
   highest-leverage edit; covers §3 entirely with no call-site changes.
2. `ui/theme/Theme.kt` `DayStatusColors` — `ok` → `#DCE775`, `warning` → `#FF7043` per §4.
3. `ui/map/MapScreen.kt` — flash `#990099` → `#3D2F74`.
4. `ui/onboarding/ChromeTour.kt` — `#00FFFF` → `#7EC8E3`; constant renamed
   `NeonCyan` → `NeonLensBlue`, since the old name no longer described the value.
5. `ui/common/StyledHtml.kt` — day heading accents onto the brand hues.
6. `res/values/colors.xml` — comment only, recording why `splash_navy` stays `#0B1130`.
7. `docs/decisions.md` — **D73**.
8. `./gradlew check` — passes (unit tests, ktlint, Konsist, lint).

**One correction to the plan as originally sketched:** it assumed `status_*` lived in
`res/values/colors.xml` and needed updating there too. That is v1's layout — v2 has no
`status_*` XML resources at all, carrying the palette solely in `Theme.kt`, so there was no
XML status edit to make. v1's `colors.xml` is untouched by this change.

Contrast ratios throughout are WCAG 2.1 against the surface each color actually renders on.
All text pairs meet AA (≥4.5:1); `outline` is intentionally below, per the note in §2.

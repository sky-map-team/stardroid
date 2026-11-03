# Detailed Design: Sharing an Object (info-card share + landing pages)

**Status: PROPOSED.** Nothing here is built. Decision numbers are unassigned.

Today the only share paths are the support page's "share the app" text and the flag-gated sky
share (`share/SkyShare.kt`, `Experiment.SHARE_SKY`, camera-ar-mode.md Part 3), which captures the
GL plane and stamps a footer pointing at `https://getskymap.app` (D110). This design adds a share
action to the **object info card**, and the landing-site and deep-link plumbing that makes the
shared thing worth tapping. The goal is a growth loop for the iOS launch: someone finds Jupiter,
shares it, and the recipient sees a rich preview, lands on a useful page, and installs.

It does not depend on AR mode.

## 1. Goals and non-goals

Goals:
- A Share action on `ObjectInfoCard` that sends an **image** and a **link**.
- A link that unfurls with a rich preview in iMessage, WhatsApp, Slack, etc.
- Tapping it opens the object's card in the app if installed, and a useful web page if not.
- Works the same on Android and iOS, with the image renderer in common code if feasible.

Non-goals:
- Deferred deep linking (carrying the object through an install). The web page delivers the
  content, so it is not needed. Revisit only if the data shows installs losing the object.
- Sharing a *view* of the sky (that stays `SkyShare`; see §9 for how the two relate).
- Any user account, referral code or attribution that identifies the sender.

## 2. The share, as the user sees it

A Share button on the info card opens the system share sheet with:
1. A 4:5 PNG rendered from the card's data: celestial image, name, one or two facts,
   and the existing branding footer.
2. Text: the object link plus the existing invitation (`share_text` pattern).

Image-only targets drop the text; unfurling targets use the link. Both lead to the same page.

### 2.1 What the image says

- **Always**: image, name, type line (planet, galaxy, constellation), image credit.
- **Static fact**: one line from the catalog (the card's fun fact, truncated at a sentence).
- **Live fact (optional)**: "Rises 7:42 pm", "Peaks Nov 17", the Moon's phase. These come from
  `CardLive` and depend on the user's location. They appear **in the image only**, never in the
  URL (§7), and are omitted when the location is unknown or manual, rather than guessed.

## 3. Landing site (`getskymap.app`)

The site is `web/` in this repo: a static Astro site (`build.format: 'file'`, `trailingSlash:
'never'`) served by Cloudflare Workers static assets (`wrangler.jsonc`). It already has a shared
layout with Open Graph and Twitter-card tags (`src/layouts/Base.astro`), a sitemap, a `_redirects`
and `_headers` file, Cloudflare Web Analytics, and one static `public/og.png` (source:
`docs/design/og.html`). Because it lives beside `stardroid-v2/`, the object pages can read
`source-data/` directly at build time, with no export step or second copy of the text.

### 3.1 Routes

`/o/<object-id>`, using the permanent namespaced ids from `source-data/README.md`:
`/o/planet/jupiter`, `/o/dso/m31`, `/o/shower/perseids`. Ids are never renamed, only added, so links
stay valid indefinitely. In Astro this is one `src/pages/o/[...id].astro` with `getStaticPaths()`.

### 3.2 Which objects get a page

Only objects with an info card. In `source-data/info_cards/en.json` that is 342 objects (137 DSOs,
89 constellations, 78 stars, 16 moons, 10 planets, 10 showers, 2 satellites). The 5,999-star
catalog is mostly unnamed and gets no page. The app offers Share only where a card exists, so no
dead links are produced.

### 3.3 Page content

Static, generated from `source-data/` (name, description, fun fact, image, credit), the same text
the card shows. Plus:
- Open in Sky Map / App Store / Google Play / F-Droid buttons, reusing the home page's badges and
  `links.json` (`appstore` is still `null`, so the App Store button stays "Coming soon" until the
  listing is live; D110 also needs the F-Droid button).
- OG and Twitter tags (§4). `Base.astro` currently hardcodes `/og.png`, so it gains an optional
  `ogImage` prop.
- English first; localized pages follow `info_cards/{tag}.json` later (§10).

### 3.4 Root and redirects

D110 assumed the root would be a 302 branching on user-agent. In practice the root is a static
landing page, and `_redirects` only holds three 301s for old paths. That is fine for this design:
`/o/*` are ordinary static pages and need no redirect rule. The part of D110 that still matters is
the comment at the top of `_redirects`: anything a store or share link may need to retarget must
be a 302. If a UA-based `/o/*` redirect is ever added, it must be a 302 and must not apply to
crawlers.

### 3.5 Well-known files

`public/.well-known/assetlinks.json` (Android) and `public/.well-known/apple-app-site-association`
(iOS, no file extension). The latter needs `Content-Type: application/json` set in `_headers`,
because Cloudflare will not infer a type for an extensionless file, and iOS requires a direct 200
with no redirect.

## 4. Do we need an image per object?

**No hand-made art, and not even a new asset pipeline.** The data already exists:

- The app ships **256 celestial images (about 10 MB)** under `app/src/main/assets/celestial_images`
  (constellations, deep-sky objects, planets, stars, spacecraft), mapped by `image_ref` in
  `source-data/objects.json` (264 entries). Credits are in `info_cards`.
- The site has a single `public/og.png` today. Every shared link would show that same card, which
  is acceptable for the home page but wasteful for `/o/*`, where the object's own picture is the
  reason to tap.
- Open Graph wants roughly 1200x630. At build time, composite each object's existing image onto
  one template (name, type line, logo), taking the layout from `docs/design/og.html`, and write
  `og/<id>.png`. One template and one script, for about 340 objects. Astro ships `sharp` for
  `astro:assets`, so no new image dependency is required; generated files go to `dist/` and are
  not committed.
- Objects with a card but no image (about 80 of the 342, mostly stars) fall back to the existing
  `og.png`. Nothing is blocked on missing art.
- This OG image is deliberately a *different render* from the in-app share image (§2): the OG image
  is static and location-free; the in-app image can carry live facts.

Licensing: these images are already redistributed in the app. Putting them on a public site and in
previews is a new use, so confirm each source's terms (`ASSET-LICENSES.txt` `[third-party]`; CC BY
4.0 credits must appear on the page) before launch.

## 5. Opening the app from the link

### 5.1 Android

- `<intent-filter android:autoVerify="true">` for `https://getskymap.app/o/` in the manifest, and
  `/.well-known/assetlinks.json` on the site.
- `MainActivity.handleDeepLink` already routes widget taps to an object's card through
  `EXTRA_SHOW_OBJECT_ID` (D75). The new link resolves the path suffix to that same object id and
  reuses it. No new navigation path.
- Unknown id (a newer app's object, an old app): open the map and show nothing, not an error.

### 5.2 iOS

- Associated Domains `applinks:getskymap.app` in the XcodeGen shell, and
  `/.well-known/apple-app-site-association` on the site, with `paths` limited to `/o/*`.
- The host app is on the private `feature/ios-host` branch; the entitlement and Info.plist change
  live there.
- The Kotlin side receives the URL and calls the same object-card routing the Android side uses,
  so the id-resolution code belongs in `shared/`.

### 5.3 Not installed

The page is the destination. Store buttons carry no object context. (If we later want the card to
open after install, that is the deferred-deep-link work we deliberately skipped.)

## 6. Rendering the image

- A `ShareCardImage` in `shared/ui` (Compose Multiplatform) drawn from `ObjectInfo` and `CardLive`
  at a fixed size, rendered to a bitmap off-screen. Fixed layout means it does not depend on the
  dialog, scroll position or screen size.
- Platform edges: bitmap to PNG and the share sheet go through a small `expect`/`actual`
  (Android `FileProvider` + `ACTION_SEND`, as `SkyShare.sendBitmap` does; iOS
  `UIActivityViewController`). Reuse `SkyShare`'s branding footer and its bitmap-ownership rules
  (audit-2026-08 M5) rather than a second implementation.
- Night mode: the image always uses the normal palette. A red-light card in a chat thread is
  confusing.
- Fonts and RTL: render with the app's fonts; check Arabic and Persian before shipping since the
  card is localized.

## 7. Privacy

- **No location in the URL**, and no time. The link is the same for every sender.
- The page and the link carry no sender identifier. The share is a user-initiated `ACTION_SEND`;
  the app makes no network request for it, consistent with D110.
- New analytics event for the share (name only, no object-type PII issues, but a parameter for the
  object *type* needs care). Per `AGENTS.md` Privacy, update `PRIVACY_POLICY.md` and its date line,
  and check `eula.xml`, `TERMS.md` section 3, `PrivacyInfo.xcprivacy` and the Play/App Store forms
  in the same change.
- The site: it uses Cloudflare Web Analytics, which is cookieless and reports by path and
  referrer; it ignores `utm_*` parameters. `web/README.md` already says to update
  `PRIVACY_POLICY.md` when that goes live, and the object pages add a path per object to that data,
  so fold the check into the same update.
- **No UTM parameters on the share link.** The link stays plain and identical for every sender.
  Attribution lives in the store buttons on the object pages instead: Play's `referrer=utm_...`
  (Play Console) and the App Store's `ct=`/`pt=` campaign tokens (App Store Connect), both with a
  distinct campaign such as `object_share` so installs from shared objects separate from the home
  page. Per-object popularity is already visible as page paths in Cloudflare.

## 8. Flagging, strings, translation

- Gate behind `Experiment.SHARE_SKY` initially, or a new `SHARE_OBJECT` flag if we want to ship it
  before AR. Recommend the separate flag. It is simpler to reason about and the card share has
  no AR dependency.
- New strings: the button label, the share text, accessibility description, the image's
  type-line templates. US English; translation is the maintainer's call (ask before `tm translate`).

## 9. Relationship to the sky share

`SkyShare` shares *a view*; this shares *an object*. Both use the same footer, store link and
`FileProvider` plumbing. Once both exist, the AR share can include the object link when a single
object is selected, and take a subject-only label (see camera-ar-mode.md Part 3). That is a
follow-up, not part of this design.

## 10. Phasing

1. **Site**: `/o/[...id].astro`, per-object OG images, an `ogImage` prop on `Base.astro`, and the
   AASA and assetlinks files with their `_headers` entry.
   Testable with no app change by pasting links into iMessage and WhatsApp.
2. **App link handling**: Android intent filter and iOS associated domain, both routed to
   the existing object-card path.
3. **App share**: `ShareCardImage` and the Share button, behind the flag.
4. **Later**: localized pages, a 9:16 Stories format, share from search results, AR share linking
   to the object.

Order matters: the site and link handling should exist before the first shared link does, because
links outlive any app update (D110).

## 11. Open questions

- Is it acceptable to publish the celestial images on the site under their current licences?
- Separate `SHARE_OBJECT` flag or reuse `SHARE_SKY`?
- Should the Share button live on the card itself or in a card overflow, given the card is a
  small `AlertDialog` with Find/Close already?
- Do we want per-object pages for the 78 starred entries, or only the planets, DSOs, showers,
  moons and constellations?

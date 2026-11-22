---
name: skymap.release
description: Make a new release of Sky Map v2 (stardroid-v2) and publish it to the Play Store.
---

Steps involved in making a release of Sky Map v2. Makes use of other v2 skills
(`skymap.whatsnew`, `skymap.deploy-play-store`) and overlaps with `skymap.deploy-play-store`.
Some steps need input from the user.

**Releases go through PRs.** Builds, and the commits that make them (version bump, What's New,
translations and any fixes to them), are never pushed straight to `master`. Work on a single
`release/<version>` branch in one free Greek-letter worktree (root `AGENTS.md`), build and upload
from that branch, then open one PR covering everything done. Ask for a **merge commit** (not
squash or rebase) so the release tag(s) stay in master's history.

**One branch, one worktree, both platforms if both are shipping.** Android and iOS each have their
own version number and release name (Step 1), but do the actual work **serially on the same
branch, in the same worktree** — build and upload Android, commit; then, without switching
worktrees or branches, do iOS's steps on top and commit again. Git won't let the same branch be
checked out in two worktrees at once, and that constraint is a feature here, not a limitation to
work around: a prior attempt at parallel worktrees on separate `release/<version>` and
`release/ios-<version>` branches led both branches to independently run `tm translate` over
overlapping new English content (the same strings, retranslated twice by an LLM with no shared
commit to de-dupe against), producing ~100 conflicting files when the second branch tried to merge
the first's already-merged work. Serial work on one branch means `tm translate` runs **exactly
once** for the combined release, and there is nothing to merge-conflict because there is only ever
one branch tip. See Step 1 for naming when only one platform ships, and Step 4 for the single
translation run. The time cost is the two platforms' builds no longer overlapping (iOS's ~15-minute
archive plus Android's upload, back to back instead of in parallel) — cheap compared to resolving
divergent-translation conflicts by hand.

**Always capture a lane's full log.** Run `bundle exec fastlane ... > log 2>&1` and read the exit
code and the log. Piping through `tail` hides failures (the pipeline exits 0), so a build that
never uploaded looks like success. Confirm `Successfully finished the upload to Google Play` (or
`Successfully uploaded the new binary to App Store Connect`) before saying a build is up.

**Before running this for the first time**, confirm with the user that v2 is actually ready to
ship a real release — as of the last recorded decision, v2's release
signing / `app/no-checkin.properties` wiring was still being finalized. Ask rather than assuming
this gap has since been closed.

### Step 0. Ask which platform(s) this release covers

**Ask: Android, iOS, or both?** Do this first, before anything else — it decides which version/name
get set in Step 1, which What's New text gets written in Step 2 (and therefore what Step 4
translates), and which of Step 5's build substeps run. Both platforms can ship under the same name
with different version numbers (Android's and iOS's are independent — e.g. Android
`2.4.0:BepiColombo`, iOS `2.1.2:Izzie` — or the same name can be reused across both if it fits), or
under different names if their namesakes genuinely differ. Confirm with the user rather than
assuming "both" just because both are due.

### Step 1. Bring the version up to date

1. For each platform in scope, ask for a release name (e.g. Jupiter) and version number (e.g.
   2.0.0). Infer the next version number from the current `versionName` in `app/build.gradle.kts`
   (Android) or `MARKETING_VERSION` in `ios/SkyMap/project.yml` (iOS), and suggest a release name
   by inferring the pattern from recent git tags — but confirm both with the user before
   proceeding.
2. Update the version:
   - **Android:**
     ```bash
     bundle exec fastlane android bump_version name:"<version>:<ReleaseName>"
     # e.g. bundle exec fastlane android bump_version name:"2.0.0:Jupiter"
     ```
     This writes to `app/build.gradle.kts` (Kotlin DSL) — see `skymap.deploy-play-store` for the
     `fastlane-plugin-versioning_android` caveat.
   - **iOS:** edit `MARKETING_VERSION` and `SKYMAP_RELEASE_NAME` directly in `ios/SkyMap/project.yml`
     (no fastlane lane for this; `CURRENT_PROJECT_VERSION`/build number is tracked by App Store
     Connect itself, not this file — leave it alone).
3. **If a release name is new** (minor bump), record it in two places per new name — skip for
   point releases that keep an existing name:
   - `docs/release-names.md`: add a row at the top of the v2 "Used names" table (icon, name,
     Wikipedia link and a one-line description of the mission). Put `_unreleased (<version> beta)_`
     in "First released" for now.
   - `whats_new_content` (Android) and/or `whats_new_content_ios` (iOS) in
     `shared/ui/src/commonMain/res/values/whatsnew.xml`: replace the closing
     `<h2>About the release name</h2>` section with one or two sentences on the new namesake (same
     spirit as the table's description). Keep it the last item in the string. If both platforms
     share a name, write this once and it covers both strings' closing section.
4. Ask the user for an appropriate portrait image for the release (mirroring v1's step; the icon is
   also used in the `release-names.md` row above). Use the `skymap.release-splashscreen` skill for
   this — it produces the small circular icon for `CHANGELOG.md`/the GitHub release *and*
   overwrites the Android app's in-app launch splash portrait
   (`app/src/main/assets/splash/splash.png`, shown briefly in `VersionBanner.kt` on launch, distinct
   from the OS-level `installSplashScreen()` splash in `MainActivity.kt`). **If iOS is in scope
   too**, also copy the same processed square crop to `ios/SkyMap/Resources/splash/splash.png`
   (300x300) by hand — the skill's Step 4 only writes the Android path. If the user has no image
   handy, it's fine to skip — the app just keeps showing the previous release's portrait, and the
   changelog/GitHub release ships without an icon.

**Beta-only builds (`:Beta` suffix).** A build that turns on features not yet launched (e.g. 2.2.0
Buran's satellites) is named `<version>:<ReleaseName>:Beta`, e.g. `2.2.0:Buran:Beta`. The gms
Remote Config rules match on `Version contains :Beta` to enable those experiments; the launch
build drops the suffix (`2.2.0:Buran`) so the flags fall back to their off defaults. Never promote a
`:Beta` build to production, and fill `beta_user_help_text` (`whatsnew.xml`) only in Beta builds —
clear it for launch. Tag Beta builds `v<version>-betaN` so they never clash with the launch tag.

### Step 2. Bring the metadata up to date

**Write the English What's New content for every platform in scope now, in this one step** —
translation (Step 4) depends on this being finished and approved first, for both platforms at
once if both are shipping, not one now and the other added later. Regenerate the text
(`skymap.whatsnew` skill): Android's `whats_new_content` string, iOS's `whats_new_content_ios`
(update by hand, relative to the *last iOS release users have* — check
`git merge-base --is-ancestor <commit> ios-v<last>-bN` to see what's after it, not the last
Android one; mention satellites only with the gradual-rollout note while they're flag-gated, and
update `fastlane/metadata/ios/en-US/release_notes.txt` too), both in
`shared/ui/src/commonMain/res/values/whatsnew.xml`, the Android fastlane changelog
(`fastlane/metadata/android/en-US/changelogs/default.txt`), and the GitHub changelog.

Also do Step 3 (below) before asking for approval — it touches the same `whatsnew.xml` file, so
folding it in now avoids a second round of changes to text the user already approved.

**Stop here and show the user the full English text for every platform in scope, including Step
3's change. Wait for explicit approval before Step 4 translates anything.** Don't go back and add
a second platform's content, or any other change to translatable text, after translation has
already run — that leaves the newly-added content stale and forces a second `tm translate` run.

v1's release process also syncs sponsors and contributors here (`skymap.sponsors`,
`skymap.contributors` skills). v2 now mirrors this too: the Buy Me a Coffee supporter list and
GitHub contributor list live in `shared/ui/src/commonMain/res/values/contributors.xml` (`sponsors_text`,
`contributors_text`, both `translatable="false"`), spliced into `credits_text`
(`shared/ui/src/commonMain/res/values/credits.xml`) via `stringResource` format args in `HelpScreen.kt`. Ask
the user whether they want sponsors and/or contributors refreshed for this release, then use the
`skymap.sync-credits` skill to do it — v2 has its own sync process (`tools/sync-credits.py` plus
the `buymeacoffee` MCP server for sponsor data), separate from v1's token-based scripts.

### Step 3. Remove unneeded text

v2 has a `beta_user_help_text` string (`shared/ui/src/commonMain/res/values/whatsnew.xml`), mirroring v1's
usually-empty beta-instructions string shown in the What's New dialog and Help screen. Check its
current content and ask the user whether it should be updated or cleared for this release, same
as v1's equivalent step.

### Step 4. Translations

**Don't start this step until Step 2's English text (for every platform in scope) has been shown
to the user and explicitly approved.** Translating before that approval means re-translating once
the text changes — show the `tm translate --dry-run` count alongside the English text when asking
for approval, so the user sees both at once.

v2's translation pipeline (`.tmconfig.toml` + the `tm` CLI, same tool as v1) is now live — see
`stardroid-v2/AGENTS.md`'s Translations section. Any translatable content touched in Steps 1–3
(`whatsnew.xml`, `strings.xml`, the fastlane changelog, etc.) goes stale in every locale the
moment its English source changes, so run this before moving on:

```bash
tm translate --all-primary --include-stale
```

Then verify with `tm languages` — every locale in `.tmconfig.toml`'s `primary_languages` list
should read 100% coverage, 0 stale.

Practical notes from running it:
- Run it in the background with output to a file; a full run is 28 locales x ~20 strings and takes
  a while. If files stop appearing and the process has used almost no CPU for 10+ minutes, a call
  has hung: kill it, discard nothing already written, and re-run just the stuck locale
  (`tm translate <locale> --include-stale`). Do not look for `tm` with `pgrep -f "tm translate"`
  from the same shell command: it matches the wrapper shell. Use `ps aux | grep "[t]m translate"`.
- If a run is interrupted on purpose, `git checkout --` the partial locale files and
  `.tmstate.toml` before starting again.
- **Always run a string check straight after, before any long build.** `tm` has produced bad
  output that only the build catches:
  ```bash
  ./gradlew :shared:ui:convertAndroidStrings -q          # fails on unescaped/double-escaped '
  grep -rn -F "\\\\'" shared/ui/src/commonMain/res        # a double-escaped apostrophe: should be none
  ```
  Also look for keys that now exist both in `shared/ui/src/commonMain/res/values-<loc>/strings.xml`
  and in `app/src/main/res/values-<loc>/strings.xml` (the older Android-only files): tm adds a
  string to the shared file that the app file already has, and the resource merger fails with
  "Duplicate resources". Delete the old copy from `app/`. (Seen in 2.4.0 beta 2: fa
  `diagnostics_*` keys and an en-GB `layer_help_grid` written as `sky\\'s`.)
- **Check every locale's fastlane changelog length, not just English's.** `tm`'s structural
  checks (HTML tag balance, quote escaping) don't enforce a character limit, and a translation
  can run longer than the English source. Play's `upload_to_play_store` API enforces its own
  hard **500-character** cap per locale on `fastlane/metadata/android/<locale>/changelogs/default.txt`
  — a *different, looser* limit than the 350-character ceiling `skymap.whatsnew` holds the
  English `en-US` copy to. A string under that cap can still come back over 500 once translated
  (seen in 2.4.0 BepiColombo: uk 523, fr-FR 522, es-419 510, pl-PL 509, it-IT 503 chars — the
  upload failed outright on the first over-limit locale it hit). Check all of them before
  uploading:
  ```bash
  for f in fastlane/metadata/android/*/changelogs/default.txt; do
    n=$(wc -m < "$f"); [ "$n" -gt 500 ] && echo "$n $f"
  done
  ```
  Fix by trimming redundant phrasing (drop minor words/clauses, keep all markup and meaning) —
  this is subtractive editing of an already-correct translation, not new translation, so it's
  fine to do directly even without fluency in the language. **If a locale is still too tight
  after trimming wording, it's fine to drop the `<b><font color="#F67E81">...</font></b>`
  heading markup entirely for that locale's changelog** (plain "New in X.Y" text instead) — the
  markup is a visual nice-to-have, not load-bearing, and losing a few dozen characters to it when
  a locale is genuinely cramped beats cutting content. Don't do this preemptively across the
  board; only when a specific locale is still over after trimming words.
- **Watch for `tm`'s repair step giving up and writing a defect anyway.** When a translation
  fails its structural check (unbalanced tags, unescaped quotes) `tm` retries translation up to
  `--repair-attempts` times (default 2), then **writes the last attempt anyway** if still
  defective, printing `N translation(s) written with structural defects — see \`tm validate
  <locale>\`` — it does not hold the release up for you. Run `tm validate <locale>` for every
  locale touched and fix what it flags before committing. Seen in 2.4.0 BepiColombo: 14 locales'
  `credits_body` kept the English source's literal `"Google Sky Map"` without re-adding the
  backslash escape the source uses (`\"Google Sky Map\"`) — harmless for XML well-formedness
  (it's inside a CDATA block) but inconsistent with the source's own escaping convention, so
  worth fixing to match rather than leaving mixed.
- Because both platforms' English content lands in one Step 2 pass on the one release branch
  (see the note at the top of this file), this is also a single `tm translate` run covering both
  platforms — there is no separate "iOS translate" step and nothing to carry across branches.
  `ca`, `hu`, and `ru` are intentionally excluded from `primary_languages` and will show low
  coverage — that's expected, not a gap to fix.
- **`tm` needs a provider API key (`GOOGLE_API_KEY`/`GEMINI_API_KEY` for the default Gemini
  provider) in the process environment, not just in a project `.env` file.** `tm`'s own
  `Settings` only auto-loads `.env` keys prefixed `TM_...`; provider keys are read straight from
  `os.environ` by the langchain client underneath, so they need an actual env-var export (or a
  `tm` version with `load_dotenv()` wired into its CLI entrypoint — fixed upstream as of
  2026-11-22, branch `fix/load-dotenv-for-api-keys` in the `translationmanager` repo). If
  `tm translate` fails immediately with `pydantic_core... API key required for Gemini Developer
  API`, that's this — check `tm`'s own version is current (see the staleness note below) before
  assuming the key itself is wrong.
- **`tm` can silently drift behind its own repo** (a separate sibling checkout, not something
  that updates with stardroid). An old `tm` can lack flags like `--include-stale` entirely, which
  makes a small, real translation gap look like a huge pre-existing backlog. Before trusting a
  surprisingly large `tm coverage`/`tm translate --dry-run` count, check
  `cd /Users/jdt/Code/production/translationmanager && git fetch && git log HEAD..origin/master --oneline`;
  if behind, `git pull --ff-only` then `pip install -e .` (watch for new transitive dependencies
  breaking the import, e.g. `json_repair`).
- **Never background `bundle exec fastlane ...` with a bare `cmd &` inside a single shell
  string**, even under a tool's own "run in background" option — that only tracks the *wrapper*
  shell, which returns instantly after forking, not the real fastlane process, so a "completed,
  exit 0" signal arrives almost immediately while fastlane is still running for real minutes
  later. Either let the run-in-background mechanism background the `bundle exec fastlane`
  command directly (no extra `&`), or explicitly wait on the real child PID.

### Step 5. Build and upload each platform in scope, one after another

Do these **serially, in the same worktree, on the same branch** — see the note at the top of this
file for why. If only one platform is in scope, skip the other's substep.

**5a. Android.** Use the `skymap.deploy-play-store` skill to build and upload a new bundle and
store metadata to the internal track. **Note:** the `internal` fastlane lane automatically
increments `versionCode` in `app/build.gradle.kts`. Commit the bump afterwards (it is part of the
release PR). If the lane fails after bumping, the next run bumps again; that is harmless, the lane
syncs from Play first. Check `app/build/outputs/bundle/gmsRelease/app-gms-release.aab` exists and
the log says the upload finished.

**5b. iOS.** Before starting, copy `ios/SkyMap/GoogleService-Info.plist` from another worktree
that has it (it is git-ignored, so `git status` stays clean); without it the build works but
analytics is a no-op.
```bash
bundle exec fastlane ios beta changelog:"What to test ..." > log 2>&1
```
It generates the project (xcodegen), archives (~15 min), exports a signed App Store `.ipa` and
uploads. The build number is TestFlight's latest for that version plus 1 (a new version starts at
1). Success is `Successfully finished processing the build ... Successfully set the changelog`.
- **Xcode must be signed in to the Penterakt team** (Settings > Accounts), or export fails with
  `No Accounts` / `No signing certificate "iOS Distribution" found` after the whole archive has
  run. Check this *before* starting. Xcode signs out often. If it rejects a correct password, sign
  in to App Store Connect in the browser first, then retry in Xcode.
- A `unescaped apostrophe in string` error during the archive's Kotlin phase is a translation
  problem (Step 4), not an iOS issue.
- **Dev build on the phone (optional):** Debug build for the connected iPhone, without touching
  the TestFlight install (see the iOS device notes in memory for ids; phone must be unlocked):
  ```bash
  xcodebuild -project ios/SkyMap/SkyMap.xcodeproj -scheme SkyMap -configuration Debug \
    -destination 'id=<udid>' -derivedDataPath ios/SkyMap/build -allowProvisioningUpdates \
    -authenticationKeyPath ~/.appstoreconnect/private_keys/AuthKey_8XZ54P8LKA.p8 \
    -authenticationKeyID 8XZ54P8LKA -authenticationKeyIssuerID 5d668d96-b7d1-41e4-aacc-8fb07cc2aba3 build
  xcrun devicectl device install app --device <coredevice-id> \
    ios/SkyMap/build/Build/Products/Debug-iphoneos/SkyMap.app
  ```
  Do not run it while a TestFlight archive is running in the same worktree (both drive Gradle).

### Step 6. Update GitHub with a new release

After getting explicit confirmation from the user:

In all commands below, substitute `<version>` and `<ReleaseName>` with the values confirmed in
Step 1 — for each platform in scope, since Android and iOS have independent version numbers (e.g.
Android `2.4.0`/`BepiColombo`, iOS `2.1.2`/`Izzie`).

1. Update `../CHANGELOG.md` (repo root, shared with v1) for this release. If the
   `skymap.whatsnew` skill was run (Step 2), it will have prepended content without a version
   heading — add the heading now. If not, add a new entry manually based on commits since the
   last tag (`git log <last-tag>..HEAD --oneline`). Add one entry per platform in scope if their
   version numbers differ:
   ```
   ## [<version>] (v2) - YYYY-MM-DD

   <img src="stardroid-v2/assets/release-icons/<version>_<name_lowercase>_icon.png" width="80" alt="<ReleaseName>" />

   ### Added / Fixed / Changed
   - ...
   ```
   Use today's date. Follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Since
   both v1 and v2 share this changelog, make the entry heading clearly identify it as a v2
   release (e.g. include "(v2)" or similar) to avoid confusing it with v1 tags. Include the
   `<img>` tag only if Step 1.4 produced an icon for this release — omit it entirely otherwise,
   same as v1.

2. **Launch (non-Beta) builds only:** in `docs/release-names.md`, replace the name's
   `_unreleased (<version> beta)_` placeholder with `YYYY-MM-DD (<version>)` — the first *public*
   (stable) release date. Do this for the first launch under a new name; point releases leave it
   alone.

3. Commit all changes from Steps 1–5 (version bump(s), whatsnew content, translations, CHANGELOG)
   on the `release/<version>` branch and open **one** PR covering both platforms if both shipped
   (not a push to `master`). Use `gh pr create` with the repo's PR template; say in the body what
   was built for each platform, which commit(s) are tagged, and that it should be merged with a
   merge commit.
   ```bash
   git add -A
   git commit -m "Prepare v2 <version>:<ReleaseName> release"
   git push -u origin release/<version>
   gh pr create --base master --head release/<version> --title "..." --body-file ...
   ```
   `gh` sometimes fails with a TLS handshake timeout; check `gh pr list --head <branch>` and retry.

4. Tag the commit(s) that were built. Since both platforms' work lands as commits on the same
   branch, this is usually the same final commit for both tags unless one platform's build
   failed and needed a fix-up commit after the other's — tag whichever commit each platform's
   build actually came from, not necessarily the branch tip.
   - **Android:** the established convention (already used for `v2.0.0`, `v2.0.7`, and
     `v2.0.0-betaXX`) is `v<version>`, where `<version>` is the full version number *without*
     the release name, e.g. `v2.0.8`. The `v2` is simply the leading digit of the version
     (2.x.y), not a separate prefix — do **not** write `v2.<version>`, which would give
     `v2.2.0.8`. Beta builds are `v<version>-betaN` (e.g. `v2.4.0-beta2`).
   - **iOS:** `ios-v<version>-bN` (e.g. `ios-v2.1.0-b1`).
   - Confirm with the user if a release ever needs to deviate from these:
   ```bash
   git tag v<version> <built-commit>          # Android
   git tag ios-v<version>-bN <built-commit>   # iOS
   git push origin <tag> [<tag> ...]
   ```

5. **Android only:** build the signed release APK for direct download (the `internal` fastlane
   lane builds an AAB for Play Store, not an installable APK):
   ```bash
   ./gradlew :app:assembleGmsRelease
   ```
   Output: `app/build/outputs/apk/gms/release/app-gms-release.apk`. Requires
   `app/no-checkin.properties` to be present and correctly configured for release signing —
   if this fails, stop and check signing config with the user rather than working around it.

6. **Android only:** extract the release notes for this version from `../CHANGELOG.md` and upload
   the GitHub release:
   ```bash
   # Substitute the actual version number in the awk pattern
   awk '/^## \[<version>\]/{flag=1; next} /^## \[/{flag=0} flag' ../CHANGELOG.md \
     | sed '/^[[:space:]]*---[[:space:]]*$/d' \
     > /tmp/release_notes.md
   ```

   **If Step 1.4 produced an icon**, the `<img>` tag's path is relative to the repo root
   (`stardroid-v2/assets/release-icons/...`), which does not resolve inside a standalone GitHub
   release description. Rewrite it to an absolute `raw.githubusercontent.com` URL pinned to the
   commit just pushed in step 3, so the icon keeps rendering even if the file is later renamed or
   moved — run this *before* `gh release create`:
   ```bash
   commit=$(git rev-parse HEAD)
   sed -i '' "s#stardroid-v2/assets/release-icons/#https://raw.githubusercontent.com/sky-map-team/stardroid/${commit}/stardroid-v2/assets/release-icons/#" /tmp/release_notes.md
   ```

   ```bash
   gh release create <agreed-tag> \
     app/build/outputs/apk/gms/release/app-gms-release.apk \
     --title "Sky Map v2 <version>: <ReleaseName>" \
     --notes-file /tmp/release_notes.md
   ```
   iOS has no equivalent GitHub release — TestFlight/App Store Connect is the only distribution
   channel, covered by Step 7 below.

### Step 7. Progress each platform in scope beyond internal/TestFlight

**Before progressing either platform, pause and explicitly ask the user to confirm that the
internal/TestFlight build has been reviewed and tested.** Do not proceed until you receive
explicit approval, and ask separately per platform if both are in scope — one may be ready before
the other.

Once approved:
- **Android:**
  1. `bundle exec fastlane android promote_to_alpha`
  2. `bundle exec fastlane android promote_to_beta`
- **iOS:** `fastlane ios external` sends a processed build to external testers (Beta App Review);
  submitting for App Store review and releasing is the user's call.

---
name: skymap.release
description: Make a new release of Sky Map v2 (stardroid-v2) and publish it to the Play Store.
---

Steps involved in making a release of Sky Map v2. Makes use of other v2 skills
(`skymap.whatsnew`, `skymap.deploy-play-store`) and overlaps with `skymap.deploy-play-store`.
Some steps need input from the user.

**Releases go through PRs.** Builds, and the commits that make them (version bump, What's New,
translations and any fixes to them), are never pushed straight to `master`. Work on a
`release/<version>` branch in a free Greek-letter worktree (root `AGENTS.md`), build and upload
from that branch, then open a PR. Android and iOS use separate worktrees and branches; when one is
stacked on the other, base the second PR on the first branch and merge in order. Ask for a
**merge commit** (not squash or rebase) so the release tag stays in master's history. This file
covers Android first; iOS (TestFlight / App Store) is the last section.

**Always capture a lane's full log.** Run `bundle exec fastlane ... > log 2>&1` and read the exit
code and the log. Piping through `tail` hides failures (the pipeline exits 0), so a build that
never uploaded looks like success. Confirm `Successfully finished the upload to Google Play` (or
`Successfully uploaded the new binary to App Store Connect`) before saying a build is up.

**Before running this for the first time**, confirm with the user that v2 is actually ready to
ship a real release — as of the last recorded decision, v2's release
signing / `app/no-checkin.properties` wiring was still being finalized. Ask rather than assuming
this gap has since been closed.

### Step 1. Bring the version up to date

1. Ask for a release name (e.g. Jupiter) and version number (e.g. 2.0.0). Infer the next version
   number from the current `versionName` in `app/build.gradle.kts`, and suggest a release name by
   inferring the pattern from recent git tags — but confirm both with the user before proceeding.
2. Update the version name using fastlane, substituting the confirmed version and name:
   ```bash
   bundle exec fastlane android bump_version name:"<version>:<ReleaseName>"
   # e.g. bundle exec fastlane android bump_version name:"2.0.0:Jupiter"
   ```
   This writes to `app/build.gradle.kts` (Kotlin DSL) — see `skymap.deploy-play-store` for the
   `fastlane-plugin-versioning_android` caveat.
3. **If the release name is new** (minor bump), record it in two places — skip for point releases
   that keep the name:
   - `docs/release-names.md`: add a row at the top of the v2 "Used names" table (icon, name,
     Wikipedia link and a one-line description of the mission). Put `_unreleased (<version> beta)_`
     in "First released" for now.
   - `whats_new_content` in `app/src/main/res/values/whatsnew.xml`: replace the closing
     `<h2>About the release name</h2>` section with one or two sentences on the new namesake (same
     spirit as the table's description). Keep it the last item in the string.
4. Ask the user for an appropriate portrait image for the release (mirroring v1's step; the icon is also used in the `release-names.md` row above). Use the
   `skymap.release-splashscreen` skill for this — it produces the small circular icon for
   `CHANGELOG.md`/the GitHub release *and* overwrites the app's in-app launch splash portrait
   (`app/src/main/assets/splash/splash.png`, shown briefly in `VersionBanner.kt` on launch, distinct
   from the OS-level `installSplashScreen()` splash in `MainActivity.kt`). If the user has no image
   handy, it's fine to skip — the app just keeps showing the previous release's portrait, and the
   changelog/GitHub release ships without an icon.

**Beta-only builds (`:Beta` suffix).** A build that turns on features not yet launched (e.g. 2.2.0
Buran's satellites) is named `<version>:<ReleaseName>:Beta`, e.g. `2.2.0:Buran:Beta`. The gms
Remote Config rules match on `Version contains :Beta` to enable those experiments; the launch
build drops the suffix (`2.2.0:Buran`) so the flags fall back to their off defaults. Never promote a
`:Beta` build to production, and fill `beta_user_help_text` (`whatsnew.xml`) only in Beta builds —
clear it for launch. Tag Beta builds `v<version>-betaN` so they never clash with the launch tag.

### Step 2. Bring the metadata up to date

Regenerate the What's New text (`skymap.whatsnew` skill) for the app (`whats_new_content` string
in `shared/ui/src/commonMain/res/values/whatsnew.xml`; iOS releases update `whats_new_content_ios` by hand), the fastlane changelog, and the GitHub changelog.

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

**Finish the English first.** Update `whats_new_content` / `whats_new_content_ios`, the fastlane
changelog and any other source strings *before* translating, or the new text is translated stale
and has to be run again. Show the user the final English text and the `tm translate --dry-run`
count, and get approval before running it.

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
- When Android and iOS releases are prepared together, translate once on the first branch and
  carry the commit across (`git merge --ff-only` / cherry-pick); the second run is then only the
  iOS-specific delta. `ca`, `hu`, and `ru` are intentionally excluded from that
list and will show low coverage — that's expected, not a gap to fix.

### Step 5. Upload to the Google Play Store

Use the `skymap.deploy-play-store` skill to build and upload a new bundle and store metadata to
the internal track. **Note:** the `internal` fastlane lane automatically increments
`versionCode` in `app/build.gradle.kts`. Commit the bump afterwards (it is part of the release PR).
If the lane fails after bumping, the next run bumps again; that is harmless, the lane syncs from
Play first. Check `app/build/outputs/bundle/gmsRelease/app-gms-release.aab` exists and the log says
the upload finished.

### Step 6. Update GitHub with a new release

After getting explicit confirmation from the user:

In all commands below, substitute `<version>` and `<ReleaseName>` with the values confirmed in
Step 1 (e.g. `2.0.0` and `Jupiter`).

1. Update `../CHANGELOG.md` (repo root, shared with v1) for this release. If the
   `skymap.whatsnew` skill was run (Step 2), it will have prepended content without a version
   heading — add the heading now. If not, add a new entry manually based on commits since the
   last tag (`git log <last-tag>..HEAD --oneline`):
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

3. Commit all changes from Steps 1–5 (version bump, whatsnew content, translations if any,
   CHANGELOG) on the `release/<version>` branch and open a PR (not a push to `master`). Use
   `gh pr create` with the repo's PR template; say in the body what was built, which commit is
   tagged, and that it should be merged with a merge commit.
   ```bash
   git add -A
   git commit -m "Prepare v2 <version>:<ReleaseName> release"
   git push -u origin release/<version>
   gh pr create --base master --head release/<version> --title "..." --body-file ...
   ```
   `gh` sometimes fails with a TLS handshake timeout; check `gh pr list --head <branch>` and retry.

4. Tag the commit that was built (the last commit on the release branch, not a merge or squash
   commit) — the established convention (already used for `v2.0.0`, `v2.0.7`, and
   `v2.0.0-betaXX`) is `v<version>`, where `<version>` is the full version number *without*
   the release name, e.g. `v2.0.8`. The `v2` is simply the leading digit of the version
   (2.x.y), not a separate prefix — do **not** write `v2.<version>`, which would give
   `v2.2.0.8`. Confirm with the user if a release ever needs to deviate from this:
   ```bash
   git tag v<version> <built-commit>
   git push origin v<version>
   ```
   Beta builds are `v<version>-betaN` (e.g. `v2.4.0-beta2`).

5. Build the signed release APK for direct download (the `internal` fastlane lane builds an AAB
   for Play Store, not an installable APK):
   ```bash
   ./gradlew :app:assembleGmsRelease
   ```
   Output: `app/build/outputs/apk/gms/release/app-gms-release.apk`. Requires
   `app/no-checkin.properties` to be present and correctly configured for release signing —
   if this fails, stop and check signing config with the user rather than working around it.

6. Extract the release notes for this version from `../CHANGELOG.md` and upload the GitHub release:
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

### Step 7. Progress the new release to beta

**Before promoting, pause and explicitly ask the user to confirm that the internal build has
been reviewed and tested.** Do not proceed until you receive explicit approval.

Once approved:
1. `bundle exec fastlane android promote_to_alpha`
2. `bundle exec fastlane android promote_to_beta`

## iOS releases (TestFlight / App Store)

iOS has its own version and release name, independent of Android: `MARKETING_VERSION` and
`SKYMAP_RELEASE_NAME` in `ios/SkyMap/project.yml` (e.g. `2.1.0` / `Izzie`; the app shows
`2.1.0:Izzie`). Android's `versionCode`, `bump_version` and Play lanes do not apply.

1. **Worktree and branch.** Free Greek-letter worktree, `release/ios-<version>` from
   `origin/master` (or stacked on the Android release branch if they share translations). Copy
   `ios/SkyMap/GoogleService-Info.plist` from another worktree that has it (it is git-ignored, so
   `git status` stays clean); without it the build works but analytics is a no-op.
2. **Version and name.** Edit `project.yml`. Add the name's row to `docs/release-names.md` (icon,
   name, "unreleased (<version>)", subject).
3. **Splash and icon.** The iOS launch splash is its own file,
   `ios/SkyMap/Resources/splash/splash.png` (300x300), *not* Android's
   `app/src/main/assets/splash/`. `skymap.release-splashscreen` Step 4 only writes the Android one,
   so copy the processed square crop to the iOS path by hand, and produce the circular icon with
   `tools/make_release_icon.py` (needs Pillow: use a throwaway venv, the system Python is
   externally managed). Crop to the subject, view the result, and make sure no person is in it.
   Do not commit the original photo. Update the splash comment in `ASSET-LICENSES.txt`.
4. **What's New.** Edit `whats_new_content_ios` in `shared/ui/src/commonMain/res/values/whatsnew.xml`
   and `fastlane/metadata/ios/en-US/release_notes.txt`. Write it relative to the *last iOS
   release that users have* (check `git merge-base --is-ancestor <commit> ios-v<last>-bN` to see
   what is after it), not the last Android one. Mention satellites only with the gradual-rollout
   note while they are flag-gated.
5. **Translate** (Step 4 above), then run the string check.
6. **Build and upload to TestFlight:**
   ```bash
   bundle exec fastlane ios beta changelog:"What to test ..." > log 2>&1
   ```
   It generates the project (xcodegen), archives (~15 min), exports a signed App Store `.ipa` and
   uploads. The build number is TestFlight's latest for that version plus 1 (a new version
   starts at 1). Success is `Successfully finished processing the build ... Successfully set
   the changelog`.
   - **Xcode must be signed in to the Penterakt team** (Settings > Accounts), or export fails
     with `No Accounts` / `No signing certificate "iOS Distribution" found` after the whole
     archive has run. Check this *before* starting. Xcode signs out often. If it rejects a
     correct password, sign in to App Store Connect in the browser first, then retry in Xcode.
   - A `unescaped apostrophe in string` error during the archive's Kotlin phase is the
     translation problem above, not an iOS issue.
7. **Dev build on the phone (optional):** Debug build for the connected iPhone, without touching
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
8. **PR and tag.** Push the branch, open the PR, tag the built commit `ios-v<version>-bN`
   (e.g. `ios-v2.1.0-b1`), and push the tag. Merge with a merge commit.
9. **External testers / App Store.** `fastlane ios external` sends a processed build to external
   testers (Beta App Review); submitting for App Store review and releasing is the user's call.

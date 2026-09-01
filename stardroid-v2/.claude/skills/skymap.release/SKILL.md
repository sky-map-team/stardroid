---
name: skymap.release
description: Make a new release of Sky Map v2 (stardroid-v2) and publish it to the Play Store.
---

Steps involved in making a release of Sky Map v2. Makes use of other v2 skills
(`skymap.whatsnew`, `skymap.deploy-play-store`) and overlaps with `skymap.deploy-play-store`.
Some steps need input from the user.

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
3. Ask the user for an appropriate portrait image for the release's changelog/GitHub-release icon
   (mirroring v1's step). Use the `skymap.release-splashscreen` skill for this — note that unlike
   v1, this only produces a small circular icon for `CHANGELOG.md`/the GitHub release; it does
   **not** touch the app's actual splash screen. v2 uses the stock AndroidX SplashScreen API
   (`installSplashScreen()` in `MainActivity.kt`) with a single static image not swapped per
   release — don't fabricate an app-branding step that doesn't exist. If the user has no image
   handy, it's fine to skip and publish without an icon.

### Step 2. Bring the metadata up to date

Regenerate the What's New text (`skymap.whatsnew` skill) for the app (`whats_new_content` string
in `app/src/main/res/values/whatsnew.xml`), the fastlane changelog, and the GitHub changelog.

v1's release process also syncs sponsors and contributors here (`skymap.sponsors`,
`skymap.contributors` skills). v2 now mirrors this too: the Buy Me a Coffee supporter list and
GitHub contributor list live in `app/src/main/res/values/contributors.xml` (`sponsors_text`,
`contributors_text`, both `translatable="false"`), spliced into `credits_text`
(`app/src/main/res/values/credits.xml`) via `stringResource` format args in `HelpScreen.kt`. Ask
the user whether they want sponsors and/or contributors refreshed for this release, then use the
`skymap.sync-credits` skill to do it — v2 has its own sync process (`tools/sync-credits.py` plus
the `buymeacoffee` MCP server for sponsor data), separate from v1's token-based scripts.

### Step 3. Remove unneeded text

v2 has a `beta_user_help_text` string (`app/src/main/res/values/whatsnew.xml`), mirroring v1's
usually-empty beta-instructions string shown in the What's New dialog and Help screen. Check its
current content and ask the user whether it should be updated or cleared for this release, same
as v1's equivalent step.

### Step 4. Translations

v2's translation pipeline (`.tmconfig.toml` + the `tm` CLI, same tool as v1) is now live — see
`stardroid-v2/AGENTS.md`'s Translations section. Any translatable content touched in Steps 1–3
(`whatsnew.xml`, `strings.xml`, the fastlane changelog, etc.) goes stale in every locale the
moment its English source changes, so run this before moving on:

```bash
tm translate --all-primary --include-stale
```

Then verify with `tm languages` — every locale in `.tmconfig.toml`'s `primary_languages` list
should read 100% coverage, 0 stale. `ca`, `hu`, and `ru` are intentionally excluded from that
list and will show low coverage — that's expected, not a gap to fix.

### Step 5. Upload to the Google Play Store

Use the `skymap.deploy-play-store` skill to build and upload a new bundle and store metadata to
the internal track. **Note:** the `internal` fastlane lane automatically increments
`versionCode` in `app/build.gradle.kts`. Do not commit before this step — the Step 6 commit
captures everything including this bump.

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
   `<img>` tag only if Step 1.3 produced an icon for this release — omit it entirely otherwise,
   same as v1.

2. Commit all changes from Steps 1–5 (version bump, whatsnew content, translations if any,
   CHANGELOG) in a single commit to `master`:
   ```bash
   git add -A
   git commit -m "Prepare v2 <version>:<ReleaseName> release"
   git push origin master
   ```

3. Tag the commit — use a v2-distinguishing tag scheme since tags are shared with v1
   (ask the user for the exact tag format if unsure, e.g. `v2-<version>` vs `stardroid-v2-<version>`):
   ```bash
   git tag <agreed-tag>
   git push origin <agreed-tag>
   ```

4. Build the signed release APK for direct download (the `internal` fastlane lane builds an AAB
   for Play Store, not an installable APK):
   ```bash
   ./gradlew :app:assembleGmsRelease
   ```
   Output: `app/build/outputs/apk/gms/release/app-gms-release.apk`. Requires
   `app/no-checkin.properties` to be present and correctly configured for release signing —
   if this fails, stop and check signing config with the user rather than working around it.

5. Extract the release notes for this version from `../CHANGELOG.md` and upload the GitHub release:
   ```bash
   # Substitute the actual version number in the awk pattern
   awk '/^## \[<version>\]/{flag=1; next} /^## \[/{flag=0} flag' ../CHANGELOG.md \
     | sed '/^[[:space:]]*---[[:space:]]*$/d' \
     > /tmp/release_notes.md
   ```

   **If Step 1.3 produced an icon**, the `<img>` tag's path is relative to the repo root
   (`stardroid-v2/assets/release-icons/...`), which does not resolve inside a standalone GitHub
   release description. Rewrite it to an absolute `raw.githubusercontent.com` URL pinned to the
   commit just pushed in step 2, so the icon keeps rendering even if the file is later renamed or
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

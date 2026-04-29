---
name: skymap.release
description: Make a new release of Sky Map and publish it to the Play Store.
---

This skill does all the steps involved in making a major or minor release of Sky Map. This skill might make use of other skills and it has some
overlap with the skymap.deploy-play-store skill. Some steps might require input from the user.

### Step 1. Bring the branding up to date
1. Ask for release name (e.g. Jupiter) and version number (e.g. 1.14.0). Infer the next version number from the current versionName in `app/build.gradle`, and suggest a release name by inferring the pattern from recent git tags (e.g. planets, constellations, stars) — but confirm both with the user before proceeding, since the theme may change.
2. Update the version name in build.gradle using fastlane, substituting the confirmed version and name:
   ```bash
   bundle exec fastlane android bump_version name:"<version>:<ReleaseName>"
   # e.g. bundle exec fastlane android bump_version name:"1.14.0:Jupiter"
   ```
3. Ask the user for an appropriate image for the splashscreen. Use the `skymap.release-splashscreen` skill for this.

### Step 2. Bring the metadata up to date
Ask the user if they need to:
1. Update the sponsors (`skymap.sponsors` skill)
2. Update the contributors (`skymap.contributors` skill)
3. Regenerate the 'whats new' text (`skymap.whatsnew` skill) for the app, the fastlane update and the github changelog

### Step 3. Remove unneeded text
1. The `beta_user_help_text` string in `app/src/main/res/values/whatsnew_content.xml` is filled with instructions for beta testers during the beta period, and must be cleared to an empty string before a full release. Check if it has content; if so, set it to empty. It does not exist in other locales so no action is needed there.
2. Delete the `fastlane/metadata/android/<locale>/changelogs/default.txt` file for every non en-US locale (delete the file entirely — Step 4 will regenerate them via translation).
3. Delete the translated `whatsnew_content.xml` files in non en-US locales (i.e. `app/src/main/res/values-<locale>/whatsnew_content.xml`). Delete the files entirely — Step 4 will regenerate them.

### Step 4. Translate missing text

**Before running any translation commands, show the user the current content of
`app/src/main/res/values/whatsnew_content.xml` and `fastlane/metadata/android/en-US/changelogs/default.txt`
and ask: "Does this look good to translate?" Do not proceed until you receive explicit approval.**


**Prerequisites:** `tm` must be installed globally (`which tm`). If missing, install it from the translationmanager project:
```bash
pip install -e /path/to/translationmanager
```
The project has a `.tmconfig.toml` already configured. Run all `tm` commands from `stardroid-v1/`.

1. Check current coverage to see what's missing:
   ```bash
   tm languages
   ```
2. Translate all primary languages (covers both Android XML strings and fastlane changelogs):
   ```bash
   tm translate --all-primary
   ```
   To translate a single locale: `tm translate de-DE`
   To preview without calling the LLM: `tm translate --all-primary --dry-run`

3. After primary translation, also translate fastlane changelogs for **all** supported locales
   (not just primary ones — Play Store serves changelogs to every locale it supports):
   ```bash
   tm translate --all --source fastlane
   ```
   Locales already translated by `--all-primary` will be skipped automatically.

4. If you only need to retranslate the what's new content and changelogs (e.g. after Step 3 deletions):
   ```bash
   tm translate --all-primary --file whatsnew_content.xml
   tm translate --all --source fastlane
   ```
4. Spot-check a few locales for structural issues (no LLM needed):
   ```bash
   tm validate de-DE
   tm validate fr-FR
   ```

### Step 5. Upload to the Google Play Store
1. Use the `skymap.deploy-play-store` skill to build and upload a new bundle and store metadata to the internal track.
   **Note:** the `internal` fastlane lane automatically increments `versionCode` in `app/build.gradle`. Do not commit before this step — the Step 6 commit captures everything including this bump.

### Step 6. Update github with a new release
After getting explicit confirmation from the user:

In all commands below, substitute `<version>` and `<ReleaseName>` with the values confirmed in Step 1 (e.g. `1.14.0` and `Jupiter`).

1. Cap the `../CHANGELOG.md` entry for this release. The `skymap.whatsnew` skill (Step 2.3) prepends content but leaves it without a version heading. Add the heading now:
   ```
   ## [<version>] - YYYY-MM-DD
   ```
   Use today's date. The CHANGELOG format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

2. Commit all changes from Steps 1–5 (version name, version code bump, splash, sponsors, contributors, whatsnew content, translations, CHANGELOG) in a single commit to master:
   ```bash
   git add -A
   git commit -m "Prepare <version>:<ReleaseName> release"
   git push origin master
   ```

3. Tag the commit:
   ```bash
   git tag v<version>
   git push origin v<version>
   ```

4. Build the signed release APK:
   ```bash
   ./build_skymap.sh
   ```
   The signed APK will be at `app/build/outputs/apk/gms/release/app-gms-release.apk`.

5. Extract the release notes for this version from `../CHANGELOG.md` (the section between the new heading and the previous one) and upload the GitHub release:
   ```bash
   # Substitute the actual version number in the awk pattern
   awk '/^## \[<version>\]/{flag=1; next} /^## \[/{flag=0} flag' ../CHANGELOG.md \
     | sed '/^[[:space:]]*---[[:space:]]*$/d' \
     > /tmp/release_notes.md

   gh release create v<version> \
     app/build/outputs/apk/gms/release/app-gms-release.apk \
     --title "Sky Map <version>: <ReleaseName>" \
     --notes-file /tmp/release_notes.md
   ```

### Step 7. Progress the new release to beta
**Before promoting, pause and explicitly ask the user to confirm that the internal build has been reviewed and tested.** Do not proceed until you receive explicit approval.

Once approved:
1. Use fastlane to promote the internal release to alpha: `bundle exec fastlane android promote_to_alpha`
2. Use fastlane to promote the alpha to beta: `bundle exec fastlane android promote_to_beta`

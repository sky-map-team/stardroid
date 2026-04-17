---
name: skymap.release
description: Make a new release of Sky Map and publish it to the Play Store.
---

This skill does all the steps involved in making a major or minor release of Sky Map. This skill might make use of other skills and it has some
overlap with the skymap.deploy-play-store skill. Some steps might require input from the user.

### Step 1. Bring the branding up to date
1. Ask for release name (e.g. Jupiter) and version number (e.g. 1.14.0). Infer the next version number from the current versionName in `app/build.gradle`, and suggest a release name by inferring the pattern from recent git tags (e.g. planets, constellations, stars) — but confirm both with the user before proceeding, since the theme may change.
2. Update the version name in build.gradle using fastlane: `bundle exec fastlane android bump_version name:"1.14.0:Jupiter"`
3. Ask the user for an appropriate image for the splashscreen. Use the `skymap.release-splashscreen` skill for this.

### Step 2. Bring the metadata up to date
Ask the user if they need to:
1. Update the sponsors (`skymap.sponsors` skill)
2. Update the contributors (`skymap.contributors` skill)
3. Regenerate the 'whats new' text (`skymap.whatsnew` skill) for the app, the fastlane update and the github changelog

### Step 3. Remove unneeded text
1. The instructions for beta users (key `beta_user_help_text` in `app/src/main/res/values/whatsnew_content.xml`) should be set to the empty string in en-US. It does not exist in other locales so no action is needed there.
2. Delete the `fastlane/metadata/android/<locale>/changelogs/default.txt` file for every non en-US locale (delete the file entirely — Step 4 will regenerate them via translation).
3. Delete the translated `whatsnew_content.xml` files in non en-US locales (i.e. `app/src/main/res/values-<locale>/whatsnew_content.xml`). Delete the files entirely — Step 4 will regenerate them.

### Step 4. Translate missing text
1. Use the `translate-skymap` skill to ensure 100% coverage in all supported languages. In particular, `whats_new_content` (in `whatsnew_content.xml`) and `fastlane/metadata/android/<locale>/changelogs/default.txt` will need to be regenerated for all non en-US locales.

### Step 5. Upload to the Google Play Store
1. Use the `skymap.deploy-play-store` skill to build and upload a new bundle and store metadata to the internal track.

### Step 6. Update github with a new release
After getting explicit confirmation from the user:
1. Check in all the resulting changes into master.
2. Tag the current head with (for example) `v1.14.0`: `git tag v1.14.0 && git push origin v1.14.0`
3. Build a signed release APK: `./build_skymap.sh` (the gmsRelease APK will be at `app/build/outputs/apk/gms/release/app-gms-release.apk`)
4. Update `../CHANGELOG.md` (one level above `stardroid-v1/`) to prepend the new release section — the `skymap.whatsnew` skill generates this content as Target C.
5. Upload a GitHub release using the CLI:
   ```bash
   gh release create v1.14.0 \
     app/build/outputs/apk/gms/release/app-gms-release.apk \
     --title "Sky Map 1.14.0: Jupiter" \
     --notes-file /tmp/release_notes.md
   ```

### Step 7. Progress the new release to beta
**Before promoting, pause and explicitly ask the user to confirm that the internal build has been reviewed and tested.** Do not proceed until you receive explicit approval.

Once approved:
1. Use fastlane to promote the internal release to alpha: `bundle exec fastlane android promote_to_alpha`
2. Use fastlane to promote the alpha to beta: `bundle exec fastlane android promote_to_beta`

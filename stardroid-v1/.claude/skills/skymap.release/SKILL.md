---
name: skymap.release
description: Make a new release of Sky Map and publish it to the Play Store.
---

This skill does all the steps involved in making a major or minor release of Sky Map. This skill might make use of other skills and it has some
overlap with the skymap.deploy-play-store skill. Some steps might require input from the user.

### Step 1. Bring the branding up to date
1. Ask for release name (e.g. Mars) and build number (e.g. 1.13.0). You might be able to infer these from previous releases so suggest something.
2. Update the release name in build.gradle to, for example, 1.13.0:Mars. You should use fastlane for this.
3. Ask the user for an appropriate image for the splashscreen. You can use the skymap.release-splash screen for this

### Step 2. Bring the metadata up to date
Ask the user if they need to
1. Update the sponsors (skymap.sponsors skill)
2. Update the contributors (skymap.contributors skill)
3. Regenerate the 'whats new' text (skymap.whatsnew skill) for the app, the fastlane update and the github changelog

### Step 3. Remove unneeded text
1. The instructions for beta users (key beta_user_help_text in whatsnew_content.xml) should be set to the empty string in en-US and removed from other locales. 
2. The fastlane changelogs/default.txt should be deleted for non en-US locales
3. The whatsnew text (key whats_new_content in whatsnew_content.xml) should be deleted in non en-US locales

### Step 4. Translate missing text
1. The translation tool "tm" should be used to ensure 100% coverage in primary languages. In particular, what whats_new_content and changelogs/default.txt will need to be regenerated for non en-US.

### Step 5. Upload to the Google Play Store
1. The skymap.deploy-play-store skill can build and upload a new bundle and store metadata to the internal track.

### Step 6. Update github with a new release
After getting confirmation from the user
1. Check in the resulting changes into master
2. Tag the current head with (for example) v1.13.0
3. Build a new release apk
4. Update the changelog.md file to reflect the changes in this release
5. Upload a release to github with the signed apk.

### Step 7. Progress the new release to beta
1. Use fastlane to promote the internal release to alpha
2. Use fastlane to promote the alpha to beta.


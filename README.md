# Sky Map [![Build Status](https://travis-ci.org/sky-map-team/stardroid.svg?branch=master)](https://travis-ci.org/sky-map-team/stardroid)
[![Code Quality: Java](https://img.shields.io/lgtm/grade/java/g/sky-map-team/stardroid.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/sky-map-team/stardroid/context:java)
[![Total Alerts](https://img.shields.io/lgtm/alerts/g/sky-map-team/stardroid.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/sky-map-team/stardroid/alerts)

This is the source repository for Sky Map.  You can find it in the [Google Play Store](https://play.google.com/store/apps/details?id=com.google.android.stardroid) and on [F-Droid](https://f-droid.org/en/packages/com.google.android.stardroid/).

<a href="https://www.buymeacoffee.com/skymapdevs" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/default-blue.png" alt="Buy Me A Coffee" height="41" width="174"></a>

Sky Map is free - there is no charge, no ads and we don't sell your data. However, some folks have asked if they can contribute in some small way so if you'd like to you can buy us a coffee by clicking on the above link. This is in no way expected or required but if you insist, I'll take a cappuccino.

## Building

You should see the following
two directories:
 * app: Application source
 * tools: Source for generating binary data used by the app.

To build SkyMap, you can use Android Developer Studio or Gradle.  Begin by
creating a `local.properties` file containing the location of your
Android installation:

    sdk.dir=<path to your SDK>

Android Developer Studio can create this for you.  You can regenerate the datafiles and
rebuild everything with the `build_skymap.sh` script:

    ./build_skymap.sh
    
(or its f-droid equivalent).

If you just want to quickly regenerate an apk please see the following instructions
(note: assembleRelease won't work because the f-droid flavor needs some tweaking which
is done by the shell script - so make sure you build the Gms flavor specifically).

## Building a debug apk

From the root directory execute

    ./gradlew assembleGmsDebug

The apk can be found in `app/build/outputs/apk/`.

## Building a release apk
(Sky Map team only)

Create a file in the app directory called
`no-checkin.properties` with appropriate values for the
keys
    store-pwd=
    key-pwd=
    analytics-key=

and overwrite google-services.json with the correct file.

From the root directory execute

    ./gradlew assembleGms

or

    ./gradlew assembleGmsRelease

The apk can be found in `app/build/outputs/apk/`.


## Running tests
Unit tests:

    ./gradlew test

Connected device/emulator required tests:

    ./gradlew app:connectedAndroidTest

# Code and Language Contributions
Yes, we know that Sky Map's code and UX is very dated. It needs a big overhaul.

In general, bug fix contributions are welcome, for example, simple one file fixes or dependency version upgrades.  We're particularly grateful for fixed or new translations since as the app is developed we lose the 100% coverage of non-English languages that we once had.

**However, please email us (or file a feature request) first before embarking on any major changes or feature additions. We may have a different vision for the direction of the app and it would be a pity to do work that we can't accept and would be wasted.**

It is likely we'll be slow to respond to emails and PR requests. Depending on what else is going on it might be days, it might be months. I do apologize for that - life is busy. Sometimes the reply might be simply to point you at this documentation, which will seem very ungrateful and unfriendly. Again, I apologize, but it's the only way to keep up with the emails.

Small, focussed PRs to fix bugs or upgrade dependencies etc are very easy for us to approve. If your PR does too much it might get stalled because even if 90% of it is welcome there might be 10% that we're not happy with. So keep them small if you can. Plus, we'll be able to review them faster.

Thanks for your contributions! They're definitely appreciated even if our slowness to respond might make it seem otherwise.

## Coding Style

We follow the [Google style guide](https://google.github.io/styleguide/javaguide.html) (or try to).  We wrap at 100 chars and we do not use the common Android style of prefixing member variables with a 'm'. 

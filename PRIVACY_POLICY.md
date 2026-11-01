# Privacy Policy

**Last Updated:** November 2026

This policy covers Sky Map on Android (Google Play and F-Droid) and on iOS (the App Store).

## Who is responsible for your data

Sky Map is published and operated by **Penterakt LLC**, which is the data controller for any
personal data processed through the app. Questions about this policy, or about data the app may
hold about you, go to [skymapdevs@gmail.com](mailto:skymapdevs@gmail.com).

Sky Map is open-source and community-built. Contributors to the project do not receive or
process your data; where this policy says "we", it means Penterakt LLC.

## In short

- The F-Droid version sends no usage statistics. The Google Play and iOS versions send usage
  statistics, not linked to your name or account, to Firebase Analytics. You can turn this off
  in Settings.
- Sky Map has no accounts, no ads, and no advertising identifier. It does not track you across
  other apps or websites.
- Your location is used on your device to draw the sky. The one time it leaves your device is
  to fetch a map picture on the location screen (see **Map images**).

## Analytics

Sky Map uses **Firebase Analytics** (provided by Google) to help us understand how people use
the app. It is part of the Google Play and iOS versions. The F-Droid version collects no
analytics data whatsoever. We use this information to improve the app and for no other purpose.
We share it with no one other than Google, which processes it for us.

### What we collect

Firebase Analytics collects the following about how the app is used. None of it includes your
name, email address, or any account, because Sky Map has none.

- **Usage patterns**: when you launch the app (hour of day, day of week), how long each
  session lasts, and which features you open (gallery, time travel, settings, etc.)
- **Search terms**: the text you type when you search, the name of the object you pick, and
  whether the search succeeded. Searches are unlikely to contain personal information, but
  whatever you type is logged.
- **Feature interactions**: which sky layers you toggle, which time travel events you select,
  which gallery images you view, whether you use manual or automatic sensor mode, which
  announcements you see or dismiss, and which options you choose on the Support screen.
- **Device capabilities**: which motion sensors your device has (accelerometer, gyroscope,
  magnetometer, rotation vector) and which sensor path is active. This helps us understand
  which hardware to support. On Android this includes whether you navigate by buttons or
  gestures.
- **Settings snapshot**: key preference values at each session start (e.g. whether gyro is
  disabled, sensor speed/damping, night mode state). This helps us understand which settings
  people actually use.
- **Language**: your device locale (e.g. `en-US`) to help us prioritise translations.
- **Install version**: for new installs only, the app version you first installed. This helps
  us understand user retention across releases.
- **Device and install details**: the device and system model, the app version, and a random
  identifier that Firebase creates for each installation of the app. The identifier is not linked
  to your name, your Apple or Google account, or other apps. Google also uses your IP address to
  work out your approximate country or city. According to Google, it does not log or store the
  address in its analytics data.

### What we do NOT collect

- **Your device's location**: your location (whether entered manually or from GPS) is never sent
  to analytics. Google derives an approximate country or city from your IP address, as
  described above.
- **Your precise location**: Sky Map only asks your phone for an approximate location (Android's
  approximate-location permission; on iOS, accuracy of about a kilometre), so it never has your
  precise position. If you type in a place yourself, the app has whatever you typed. Your
  phone's own location service, including Google's on Android devices, works under its own
  privacy policy.
- **The advertising identifier**: Sky Map never reads the advertising ID (IDFA on iOS) and
  never asks permission to track you.
- **Your name or contact details**: Sky Map has no accounts and never asks for them.
- **Contacts, photos, microphone, health data, or payment details.**

### How the data is used

Firebase Analytics compiles statistical reports for us that tell us things like which features
are popular, which device capabilities are common, and how session length varies across users.
This helps us decide which features to improve, which languages to support, and when to drop
support for obsolete hardware.

### How long it is kept

Google keeps this data for 14 months, the longest period available to us, and then deletes it.

### Opting out

You can turn this off at any time with **Settings → Send usage statistics**. Turning it off
suppresses all data collection, including Firebase's own automatic events. Settings is
available once you have accepted the terms on first launch. Until then, Firebase's automatic
events (such as the first time the app is opened) and whether you accepted or declined the
terms are recorded.

For more information on Firebase Analytics see https://firebase.google.com/support/privacy.

## Map images (Geoapify)

On the location screen, Sky Map shows a map picture of the place you have chosen or that your
device found. To get it, the app requests an image from **Geoapify**, a mapping service, and the
request contains your location rounded to three decimal places (about 100 metres at the
finest). The location Sky Map starts from is already approximate, as described above, unless
you typed a place in yourself. This applies to all three versions of the app. The request also reveals your IP address to Geoapify, as any
request over the internet does. No other personal data is sent, and we run no server that
receives these requests. See Geoapify's privacy policy at
https://www.geoapify.com/privacy-policy for how it handles them.

## Other requests the app makes

- **Remote configuration (Google Play version):** about every 12 hours Sky Map fetches settings
  and announcements from Firebase Remote Config. That request goes to Google and carries the
  app version, device details and the same per-install identifier as analytics. The usage
  statistics switch does not turn it off.
- **Satellite data (Android):** the satellite layer downloads orbit data from CelesTrak
  (celestrak.org). The request shows your IP address to that service and contains no location.

## Tips and donations

- **iOS:** the optional tips on the Support screen are in-app purchases made through Apple.
  Apple processes the payment and holds your payment details. We receive only totals in
  Apple's sales reports. We never see your Apple ID, your name, or your card.
- **Android:** the Support screen links to Buy Me a Coffee, an external service with its own
  privacy policy. The link tells Buy Me a Coffee that the visit came from the app. Anything you
  give there is handled by that service and not by the app.

## Backup

- **Android:** Sky Map uses Android's built-in backup: your app settings, including your saved
  location if you entered or granted one, are eligible for Google's encrypted cloud backup and
  for device-to-device transfer when you set up a new phone. This is intentional: it means your
  saved location and preferences carry over automatically to a new device. This data is handled
  by Android's backup system, not by Penterakt LLC or Firebase Analytics, and only Google
  and (for device transfer) you have access to it. You can turn off backup for Sky Map, along
  with any app, from your device's system backup settings.
- **iOS:** Sky Map's settings, including a saved location, are part of your normal iPhone
  backup (iCloud or a computer), which Apple handles. They are not available to Penterakt LLC.

## Changes to this policy

If this policy changes, the date at the top will change with it, and the new version will be
published at the same address.

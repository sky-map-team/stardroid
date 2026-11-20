# Sky Map on iPhone and Android: what's different

Sky Map is one app on two platforms. The sky, the catalog, search, time travel, the info cards
and the astronomy behind them are the same code on both, so positions, rise and set times and
Moon phases agree to the minute. What differs is the part that touches the phone itself:
widgets, notifications, background work, and a few features that have reached Android first.

This page lists those differences and why they exist. Most come from what each system allows an
app to do, not from a choice to leave something out. It's written for people who know their
way around iOS or Android settings; you don't need to be a developer to read it.

Some features on both platforms are switched on gradually after a release, so a feature listed
here may reach your phone a little after the update that carries it.

## At a glance

| | iPhone | Android |
|---|---|---|
| Sky, search, time travel, info cards | Yes | Yes |
| Satellites and pass predictions | Yes | Yes |
| Notifications (meteor showers, tonight's sky, satellite passes, eclipses) | Yes | Yes |
| Home Screen widgets | Moon (coming); Tonight and Countdown to follow | Moon, Tonight, Countdown |
| Lock Screen widgets | Moon (coming) | No |
| Through-the-camera (AR) mode | Not yet | In testing, not yet released |
| Share a picture of the sky | Not yet | In testing, not yet released |
| News and announcements from the Sky Map team | Not yet | Yes |
| Compass and grid labels in your language | English only for now | Yes |
| Build without Google services | No | Yes, on F-Droid |
| Oldest supported system | iOS 15 | Android 10 |

## Widgets

The widgets show the same things on both platforms, worked out by the same code, in the same
colours and languages. How they get onto your screen, and how they stay up to date, differs.

**Adding one.** On Android, Sky Map can offer to place a widget for you (from the Widgets sheet
or the Moon's info card), and the system asks you to confirm. iOS doesn't let an app place a
widget, so you add it yourself: touch and hold the Home Screen, tap **Edit** then **Add Widget**
(**+** before iOS 18), and search for Sky Map.

**Sizes.** The Android Moon widget can be resized freely, and switches to a compact disc-only
layout when it's smaller than 2×2. On iPhone it comes in the small Home Screen size.

**Lock Screen (iPhone only).** iOS 16 and later can show the Moon on the Lock Screen in two forms:

- **Circular**, in the row under the clock: the phase disc and how much is lit.
- **Inline**, in the line of text above the clock: the phase name and the percentage. This one
  only appears when you tap that line while customizing the Lock Screen, not the row below it.

Android has no Lock Screen widgets.

**Staying current.** An Android widget is refreshed by a background job every 12 hours, and
whenever you use the app. iOS doesn't let a widget run whenever it likes, so the iPhone widget
works out a whole day of hourly snapshots in advance and the system shows each one at its time.
In practice the iPhone's illumination figure moves hourly, and neither widget goes stale if you
don't open the app for a while.

**Your location.** The widgets use the location the app last saved. On iPhone, open the app once
after installing (or after moving somewhere new) so the widget picks it up. The location is
shared between the app and its widget on the phone only, through an iOS "App Group"; it isn't
sent anywhere.

**Tapping one** opens Sky Map on that object on both platforms.

## Notifications

The same kinds of alert, worked out by the same rules: meteor shower peaks, a digest of tonight's
sky, satellite passes and eclipses. All are off until you turn them on.

- **Turning kinds on and off.** On Android each kind is also a notification category in the
  system settings. iOS only has one switch per app, so the per-kind switches are in Sky Map's own
  Settings and the layer toggles. Sky Map asks for permission when you first turn a kind on, not
  at launch.
- **Timing.** iOS delivers a scheduled notification at the exact minute, even if the app hasn't
  run, so iPhone satellite alerts come 6 minutes before the pass. Android's battery saving can
  delay alerts, so Android sends them 10 minutes ahead.
- **How far ahead.** An iPhone schedules the next few nights each time you open the app. If you
  travel far without opening it, the alerts are for where you were until you next do.

## Satellites

Both apps fetch satellite orbits from CelesTrak, no more than every few hours. Android refreshes
in the background twice a day. iOS decides for itself when an app may run in the background, and
for an app you don't use daily that can be rarely. So the iPhone app also fetches when you open
it if its data is more than about three days old. Pass times are hidden once the data is too old
to be precise, on both platforms.

## Features that reached Android first

- **AR mode** (the sky drawn over the camera view) and **sharing a picture of the sky** are built
  on Android and being tested there. They aren't public on either platform yet, and the plan is
  to release them on both at once.
- **News and announcements** (an aurora alert, a bright planet tonight) need the app to check for
  messages while it's closed. iOS doesn't reliably allow that without push notifications, so
  they're iPhone-only later.
- **Compass and grid labels** (North, Zenith, Ecliptic and so on) are still in English on iPhone.
  Everything else follows your language, including object names.

## Things that are just different

- **Drawing.** Android draws the sky with OpenGL, and has a newer OpenGL renderer that some
  phones can choose in Settings. iOS uses Apple's Metal. They're built to look the same.
- **Sensors.** Both need a compass for the sky to follow the phone. A device without one, such as
  an iPod touch, starts with manual (drag to look around) mode and doesn't offer automatic.
- **Privacy.** Both apps collect the same, opt-out usage statistics through Firebase, and neither
  reads your advertising ID. Android also has an F-Droid build with no Google services at all;
  the App Store has only the one version. See the [privacy policy](../../PRIVACY_POLICY.md).
- **Language.** Both follow the language you pick for Sky Map in the system settings (on iPhone:
  Settings → Apps → Sky Map → Language, or Settings → Sky Map before iOS 18).

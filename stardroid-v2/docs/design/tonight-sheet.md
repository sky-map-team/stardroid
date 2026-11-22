# Detailed Design: The Tonight Sheet

**Status: IMPLEMENTED** (translations of the three new strings pending). Decision: **D149**.

## 1. Problem

Tonight's highlights exist in the app only as widget rows and a digest notification, and both cut
them short:

- A widget row is one line. On iPhone the Lock Screen version has three lines for the whole
  widget, and a long row ends in an ellipsis on the Home Screen too. Nothing in the app shows the
  whole sentence.
- The Lock Screen widget takes only one tap, so it can open only one highlight's card.
- The digest notification names no object, so tapping it just opens the map.
- A one-off announcement (the Voyager message, for example) is lost once its notification is
  dismissed. The dialog only shows a message that hasn't been shown anywhere interruptive, so
  there is no way back to it.

Tapping a widget row already reaches that event in full: the row opens the object's card, whose
Upcoming section is built from the same `SkyEvent`s (D141). What's missing is the list as a
whole.

## 2. What it is

One bottom sheet in `:shared:ui`, used by both apps, titled **Tonight**:

1. **Header**: the date, the sunset time and when it gets dark, as on the widget.
2. **Announcements**: any active message from the Sky Map team, shown in full (title and
   body), with its search action as a button where it has one (§5).
3. **Highlights**: every highlight `tonightSky` ranks, in full. Each row has the dot, the sentence
   wrapped over as many lines as it needs, and the trailing note on its own line. Tapping a row
   opens its object's card (`targetObjectId`), the same as a widget row.
4. **Quiet night**: "Quiet sky tonight" and what's next, as on the widget.

The sheet is read-only: no settings and no toggles. Its rows say what the widget says, worded by
the shared `NotificationText`, so the three surfaces can't disagree.

### 2.1 How many highlights

`tonightSky` keeps the top three. The sheet shows the same three for now, so it agrees with the
widget and the digest. It may show more later, once we've seen it, so it is built for that:
`tonightSky` takes the count as a parameter (`limit`, default 3), and the sheet passes its own
constant. Showing more is then a one-line change that leaves the widget and the digest as they
are.

## 3. What opens it

| Entry point | Today | With the sheet |
|---|---|---|
| Tonight widget, a row | the row's card | unchanged |
| Tonight widget, header and background (Android) | the map | the sheet |
| Tonight widget, Home Screen background (iOS) | the map | the sheet |
| Tonight widget, Lock Screen (iOS) | the first row's card | the sheet |
| Digest notification | the map | the sheet |
| Announcement notification | the map, or its search | the sheet, which carries the search button |
| In the app | none | a "Tonight" item in the map's menu |

The in-app entry matters most for announcements: it is how someone gets back to a message whose
notification they dismissed.

- **Android** opens it with an intent extra, `EXTRA_SHOW_TONIGHT`, handled in `MainActivity`
  beside `EXTRA_SHOW_OBJECT_ID`.
- **iOS** opens it with `<scheme>://tonight` (`ObjectLink` gains the route), handled by
  `DeepLinks` beside `object/<id>`. The digest notification carries `ObjectLink.TONIGHT_TARGET`
  in its `objectId` field, deliberately: catalog ids are `type/name`, so it can't clash, and
  the Swift notification plumbing stays unchanged. A typed target is a possible follow-up.

## 4. Where the work lives

- **`TonightViewModel`** (`:shared:viewmodels`): `tonightSky` for now, at the saved location,
  with the catalog's showers. Satellite passes come from a function each app supplies, because the
  layer and flag gates differ: Android's `tonightSatellitePasses`, iOS's `passesWithin` over its
  repository with the layer check. Announcements come from a second supplied function. It returns
  a small common type, `TonightNotice(title, body, searchQuery)`, so the sheet needn't know
  about announcements; both apps fill it with the shared `tonightNotices`.
- **Wording**: `NotificationText` over a `TextSource` read from Compose resources by
  `TextId.resourceName`. That is today's `loadIosTextSource`, which moves to `:shared:ui`'s common
  code and serves both apps: every `TextId` string is already in `:shared:ui`'s resources.
- **The widget-only strings** the sheet also needs (`tonight_widget_sun_times`, `tonight_quiet`,
  `tonight_quiet_next`, the countdown names) live in `:app`'s resources. They move to `:shared:ui`,
  every locale with them, so both the sheet and the iOS extension read one copy. A move changes
  no text, so no translation is stale. The Android widget keeps reading them as `R.string`
  because `:app` merges `:shared:ui`'s resources.
- **New strings**: the sheet's title ("Tonight"), the menu item, and the announcements section
  heading. Those three need a `tm translate` run.

## 5. Announcements in the sheet

The sheet is a **passive** surface, like the widget, so `AnnouncementPolicy` treats it the same
way:

- It shows every active message (`start` to `end`, `min_version`) whatever has been shown
  elsewhere. That is the point: a dismissed notification doesn't hide it here.
- An explicit **Dismiss** in the dialog still hides it everywhere, the sheet included, because
  the user asked for that.
- Opening the sheet records nothing, so it never suppresses a notification or the dialog.

This adds `Surface.SHEET` to the policy (non-interruptive), but not to the payload. The sheet is
implicit: a message lists `notification`, `widget` or `interstitial` in Remote Config as now, and
the sheet shows it whichever it lists, so publishing (`skymap.push-announcement`) doesn't change.
A message aimed only at the dialog is still news the user may want to find again. The cost is
that no message can be kept out of the sheet. If one ever needs to be, `sheet` becomes a payload
value then.

On iOS the sheet and the launch dialog are the only surfaces, and messages are as fresh as the
last open's fetch ([remote-announcements.md](remote-announcements.md#ios)).

## 6. Decisions (confirmed in review)

1. **D149a**: the sheet shows the three highlights the widget shows, for now. The count is a
   parameter so this can be revisited once it's built (§2.1).
2. **D149b**: a dismissed notification doesn't hide a message from the sheet; a Dismiss in the
   dialog does (§5).
3. **D149c**: the widget-only strings move from `:app` to `:shared:ui` (§4).
4. **D149d**: the Lock Screen widget opens the sheet instead of the first row's card.
5. **D149e**: the map's menu has a "Tonight" item, the only way in for someone with no Tonight
   widget, and the only way back to an announcement after its notification is dismissed (§3).

## 7. Build order

1. Move the strings and the text source (no behaviour change).
2. `TonightViewModel`, the sheet, and the in-app menu entry on both platforms.
3. The Android entry points (the widget header and background, the digest notification, the
   announcement notification).
4. The iOS entry points (the `tonight` link from both widget sizes and from the digest).
5. Announcements in the sheet, with `Surface.SHEET` and its policy test.
6. Strings: `tm translate --all-primary --include-stale`, once approved.

## 8. Tests

- `TonightViewModel` against fakes: the highlights match `tonightSky`, passes are off with the
  layer off, and there is no location.
- `AnnouncementPolicy`: the sheet shows a message after its notification was shown, and hides
  one after a Dismiss.
- The `tonight` route in `ObjectLink`'s tests, and the extra in `MainActivity`'s intent
  handling.
- The sheet's rows wrap rather than truncate, in a Compose UI test at a narrow width with a long
  German string.

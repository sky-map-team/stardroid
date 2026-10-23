# Remote announcements

Status: built, flag-gated (`Experiment.ANNOUNCEMENTS`, default off).

Ad-hoc messages that cannot be baked into a release ("strong aurora tonight", "Saturn is
unusually bright"), authored in Firebase Remote Config and shown on up to three surfaces.

## Decisions

- **Delivery: Remote Config, ~12 h latency.** One WorkManager job (`AnnouncementWorker`) fetches
  twice a day. The install base is ~5M, so request volume matters: at the client's 12 h throttle
  that is roughly 60–120 requests/s averaged, with no per-project fetch cap and no cost, per
  Firebase's published limits. Do not poll faster. Author messages a day ahead. FCM is the
  upgrade path for same-hour alerts (see below).
- **gms only.** fdroid binds `AnnouncementSource.None` through `FlavorEdges`, so it makes no
  request and shows nothing.
- **Localization: per-locale map in the payload.** The app walks `LocaleSpec.fallbackChain`
  (`pt-BR` → `pt` → `en`). A message with no matching language and no `en` is dropped.
- **Targeting: time window plus Firebase RC conditions** (country, app version). No in-app
  latitude filter yet; an aurora message goes to everyone the RC condition selects.

## Payload

One RC string parameter, `announcements`:

```json
{ "v": 1, "messages": [ {
  "id": "2026-10-aurora-1",
  "start": "2026-10-03T16:00:00Z", "end": "2026-10-04T10:00:00Z",
  "surfaces": ["notification", "widget", "interstitial"],
  "min_version": 1760,
  "text": { "en": {"title": "...", "body": "..."}, "de": {"title": "...", "body": "..."} },
  "action": { "type": "open_sky" }
} ] }
```

- `id` is the dedup key. A corrected message needs a **new id**, or devices that already saw the
  old one will not see it again.
- `surfaces` picks where it may appear. Unknown surface names are ignored.
- `action` is `open_sky` (default) or `{"type":"search","arg":"Saturn"}`.
- At most 3 messages are kept (newest `start` first). Malformed messages are dropped one by one;
  a malformed document yields nothing. Keep the value well under a few KB.

## Dedup (`AnnouncementPolicy`)

- Notification and interstitial are **interruptive**: a message fires on at most one of them.
  Posting the notification suppresses the dialog, and showing the dialog suppresses a later
  notification.
- A notification the system blocks (permission or channel) is not recorded as shown, so the
  interstitial takes over.
- The **widget is passive** and exempt: it carries the message until `end`.
- **Dismiss** in the dialog hides the message on every surface.
- Seen-state is one JSON preference (`announcement_seen`) in the shared settings DataStore,
  pruned 14 days after a message leaves the payload.

## Surfaces

- **Notification**: channel `announcements`, id 45, from `SkyNotification` (never replaces a shower alert, digest, pass or eclipse alert).
  Held back 22:00–08:00 local and re-run at 08:00.
- **Widget**: banner row under the Sky Tonight header; refreshed by the worker.
- **Interstitial**: after the EULA / welcome / What's New gates clear, once per process.

## Controls

- `Experiment.ANNOUNCEMENTS` is the remote kill switch.
- `Settings.announcementsEnabled` (default on) is the user's opt-out, in Settings → Other.

## Publishing

Use the `skymap.push-announcement` skill (`.claude/skills/skymap.push-announcement/`, script
`scripts/push_announcement.py`). It edits the `announcements` parameter in the Remote Config
template of Firebase project `sky-map-1286`; gms only.

- `add <message.json>`, `remove <id>`, `prune`, `enable`, `disable`, `list`. Everything is a
  dry run unless `--publish` is passed.
- Messages are validated with the app parser's rules before anything is written. The script
  refuses a message the app would drop, and refuses to go past 3 live messages.
- Auth is `gcloud auth print-access-token`; the account needs Remote Config write access.
  The PUT is unconditional, so the script re-checks the template version just before it.
- Devices fetch about every 12 h and can't be recalled, so set `start` a day ahead and use a
  new `id` for any corrected text. `disable` is the emergency stop.
- There is no targeting in the app. Region-specific messages need a Remote Config condition
  added by hand in the console.

## Not built

- FCM topic push for same-hour alerts. It would plug in behind `AnnouncementSource` by
  triggering an out-of-band `AnnouncementWorker` run.
- Latitude-band targeting using `Settings.savedLocation`.

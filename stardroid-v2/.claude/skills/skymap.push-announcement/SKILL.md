---
name: skymap.push-announcement
description: Compose and publish a remote announcement (aurora tonight, bright planet, etc.) to Sky Map v2 users through Firebase Remote Config. Trigger on "push an announcement", "send a message to users", "tell users about the aurora", "publish announcement", "pull/withdraw an announcement", or similar.
dependencies: python>=3.8, gcloud
---

# Push a Sky Map announcement

Publishes to the Firebase project `sky-map-1286` (gms flavor only; fdroid never sees these).
Background and the exact schema: `docs/design/remote-announcements.md`. **Publishing reaches
up to ~5M devices and can't be recalled from phones that already fetched it**, so always
preview, then get an explicit go-ahead from the user before the real publish.

## Rules to apply before drafting

- **Timing:** devices fetch about every 12 h, so a message needs a `start` at least a day
  ahead of when it should matter, and an `end` after the event. Same-day urgency isn't
  supported — say so if the user asks for "right now".
- **`id`:** unique and never reused (e.g. `2026-10-aurora-1`). A corrected message needs a new
  id, otherwise devices that already saw the old text will not show it again.
- **Surfaces:** ask which of `notification`, `widget`, `interstitial` (default: all three).
  Notification and interstitial fire at most once between them; the widget carries it until `end`.
- **Text:** English is required (`en`) as the fallback. Add other locales the user supplies;
  offer to draft translations for the languages they want, and have them approve before
  publishing. Keep the title short (notification title) and the body to about one or two
  sentences.
- **Action:** `open_sky` (default) or `search` with an object name (e.g. `Saturn`).
- **Targeting:** none in the app. For region-specific messages (aurora is high-latitude), tell
  the user to add a Remote Config condition in the console, since this tool publishes the
  parameter unconditionally.
- Keep the active list small: at most 3 messages are used (newest `start` first).

## Workflow

1. Gather: surfaces, window (`start`/`end` in UTC ISO-8601), `en` title/body, other locales,
   action, optional `min_version` (versionCode floor).
2. Write the message JSON to the scratchpad directory (one message object; see below).
3. **Dry run** (default, changes nothing):
   ```bash
   python3 .claude/skills/skymap.push-announcement/scripts/push_announcement.py add message.json
   ```
   It validates the message with the same rules as the app's parser, fetches the live Remote
   Config template, and prints exactly what would change. Show this to the user.
4. Only after the user explicitly approves, publish:
   ```bash
   python3 .claude/skills/skymap.push-announcement/scripts/push_announcement.py add message.json --publish
   ```
5. Read it back with `list` and report what is now live.

Other commands:

```bash
... push_announcement.py list                      # what is currently published
... push_announcement.py remove <id> [--publish]   # withdraw one message
... push_announcement.py prune [--publish]         # drop messages whose end has passed
... push_announcement.py enable [--publish]        # set announcements_enabled=true (the kill switch)
... push_announcement.py disable [--publish]       # set announcements_enabled=false (stop everything)
```

`disable` is the emergency stop: devices pick it up on their next fetch (up to ~12 h) and all
three surfaces go quiet. Withdrawing a single message uses `remove`.

## Message format

```json
{
  "id": "2026-10-aurora-1",
  "start": "2026-10-03T16:00:00Z",
  "end": "2026-10-04T10:00:00Z",
  "surfaces": ["notification", "widget", "interstitial"],
  "min_version": 0,
  "text": {
    "en": {"title": "Strong aurora tonight", "body": "Kp 7 forecast — look north after dark."},
    "de": {"title": "...", "body": "..."}
  },
  "action": {"type": "open_sky"}
}
```

## Auth and safety

- Uses `gcloud auth print-access-token`. The active gcloud account needs Remote Config write
  access on `sky-map-1286` (check `gcloud auth list`; `skymapdevs@gmail.com` normally has it).
  If a call returns 401/403, tell the user to run `! gcloud auth login` and retry.
- The script reads the live template, edits only the `announcements` (and, for
  `enable`/`disable`, `announcements_enabled`) parameters, and PUTs it back with the `If-Match`
  ETag, so other parameters are untouched and a concurrent console edit makes the publish fail
  rather than get overwritten.
- It never publishes without `--publish`, and refuses any message the app's parser would
  drop (bad dates, no `en`, unknown surfaces, duplicate id).
- Remote Config keeps template version history, so a bad publish can be rolled back in the
  Firebase console (Remote Config → history) — but phones that already fetched it keep it
  until the next fetch.
- Do not commit message files or tokens to the repo.

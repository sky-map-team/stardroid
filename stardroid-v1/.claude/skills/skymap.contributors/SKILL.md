---
name: skymap.contributors
description: Fetches GitHub contributors for Sky Map and updates the app/src/main/res/values/notranslate-contributors.xml file. Trigger this when asked to "sync contributors", "update contributors", or "refresh credits".
---

# Sky Map Contributors Sync

This skill automates the update of the Sky Map contributors list from GitHub.

## Usage

1. Check if `tools/no-checkin-github_pat` exists in the repo root. If it does, read the token
   from it (strip any leading `1→` prefix) and pass it as `GITHUB_TOKEN`.
   - **This file must never be checked in** — it is a local secret.
   - If the file doesn't exist, check if `GITHUB_TOKEN` is already set in the environment.
   - Without a token the script still works but is limited to 60 API requests/hour, which may
     not be enough to fetch all contributor profiles. Warn the user if unauthenticated.
2. Run the script:
   ```
   GITHUB_TOKEN=<token> python3 tools/sync-contributors.py
   ```
3. After the script runs, verify the changes in
   `app/src/main/res/values/notranslate-contributors.xml`.

## What the script does

- Paginates through all commits on `sky-map-team/stardroid` via the GitHub API.
- Records contributors in reverse-chronological order of their most recent commit (first
  appearance in the commit stream).
- Skips bot accounts (logins ending in `[bot]`).
- For each unique login, fetches the GitHub profile to obtain the user's real name; falls back
  to the login handle if no name is set.
- Deduplicates by normalizing display names to lowercase alphanumeric — so "John Smith" and
  "john smith" are treated as the same person.

## Standards

- Output file: `app/src/main/res/values/notranslate-contributors.xml`
- Names are comma-separated, Android-XML-escaped.
- The XML must maintain the `notranslate-` naming convention to avoid localization issues.

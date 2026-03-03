---
name: sky-map-sponsors
description: Fetches Buy Me a Coffee supporters and updates the app/src/main/res/values/notranslate-sponsors.xml file. Trigger this when asked to "sync sponsors", "update donors", or "refresh credits".
---

# Sky Map Sponsor Sync
This skill automates the update of the Sky Map sponsor list.

## Usage
1. Check if `tools/no-checkin-bmac_pat` exists in the repo root. If it does, read the token from it (strip the `1→` prefix) and pass it as `BMC_TOKEN`.
   - **This file must never be checked in** — it is a local secret.
   - If the file doesn't exist, check if `BMC_TOKEN` is already set in the environment, or ask the user for the token.
2. Use the `bash` tool to execute `BMC_TOKEN=<token> python3 tools/sync-sponsors.py`.
3. After the script runs, verify the changes in `app/src/main/res/values/notranslate-sponsors.xml`.

## Standards
- The output file must be `app/src/main/res/values/notranslate-sponsors.xml`.
- Names must be comma-separated.
- The XML must maintain the `notranslate-` naming convention to avoid localization issues.

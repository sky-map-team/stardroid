---
name: sky-map-sponsors
description: Fetches Buy Me a Coffee supporters and updates the app/src/main/res/values/notranslate-sponsors.xml file. Trigger this when asked to "sync sponsors", "update donors", or "refresh credits".
---

# Sky Map Sponsor Sync
This skill automates the update of the Sky Map sponsor list.

## Usage
1. Use the `bash` tool to execute `python3 tools/sync-sponsors.py`.
2. Ensure the environment variable `BMC_TOKEN` is available, or ask the user for it if missing.
3. After the script runs, verify the changes in `app/src/main/res/values/notranslate-sponsors.xml`.

## Standards
- The output file must be `app/src/main/res/values/notranslate-sponsors.xml`.
- Names must be comma-separated.
- The XML must maintain the `notranslate-` naming convention to avoid localization issues.

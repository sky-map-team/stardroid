---
name: skymap.sync-credits
description: Sync the Buy Me a Coffee sponsor list and GitHub contributor list into Sky Map v2's contributors.xml. Trigger on "sync sponsors", "update donors", "sync contributors", "refresh credits" for stardroid-v2.
---

# Sky Map v2 Credits Sync

Updates `sponsors_text` and `contributors_text` in
`app/src/main/res/values/contributors.xml`, mirroring stardroid-v1's
`skymap.sponsors` / `skymap.contributors` skills but adapted for v2's merged
file and the current (MCP-based) Buy Me a Coffee integration.

Both strings are `translatable="false"` and are spliced into `credits_text`
(`app/src/main/res/values/credits.xml`) via `stringResource` format args in
`HelpScreen.kt` — see `AGENTS.md`.

## Contributors (GitHub) — fully scriptable

v1's `no-checkin-bmac_pat`-style GitHub token isn't needed; use `gh auth token`:

```bash
GITHUB_TOKEN="$(gh auth token)" python3 tools/sync-credits.py contributors
```

This fetches commit authors from `sky-map-team/stardroid` (shared repo, so it
picks up v1 contributors too), resolves each login to a display name, dedupes,
and rewrites `contributors_text` in place. Review the diff — GitHub logins
without a public display name fall back to the raw login string.

## Sponsors (Buy Me a Coffee) — requires the MCP server, not a script

Buy Me a Coffee retired the personal-access-token REST API that v1's
`tools/sync-sponsors.py` used (`developers.buymeacoffee.com/api/v1/supporters`
now redirects to a login flow and 502s). Sponsor data now comes from the
`buymeacoffee` MCP server, which only works inside a live Claude conversation
— there is no standalone script for the fetch step.

### Step 1 — Ensure the MCP server is connected

Check with `claude mcp list` for a `buymeacoffee` entry. If missing, ask the
user to run:

```
claude mcp add --transport http buymeacoffee https://mcp.buymeacoffee.com/connect
```

then complete the auth flow (e.g. via `/mcp`). Don't add the server yourself —
it needs the user's own OAuth approval. Once connected, load its tools:

```
ToolSearch("select:mcp__buymeacoffee__get-recent-supports,mcp__buymeacoffee__get-recent-memberships")
```

### Step 2 — Paginate every supporter source

There are four sources, each needing full pagination (`limit=20`, increment
`page` until a page returns fewer than 20 results):

- `get-recent-supports` with `support_type: "donation"`
- `get-recent-supports` with `support_type: "shop"`
- `get-recent-memberships` with `subscription_type: "membership"`
- `get-recent-memberships` with `subscription_type: "monthly_supporter"`

This is a lot of tool calls with output you don't need to keep around — run it
as a **forked subagent** (`Agent` tool, `subagent_type: "fork"`) rather than
inline, so the raw pagination noise doesn't fill the main conversation. Give
the fork the exact cleaning rules from Step 3 so it applies them consistently
and reports only the final list.

### Step 3 — Clean and dedupe (mirrors v1's `clean_name`/`escape_for_android`)

- Skip entries where `supporter_id` is null (anonymous).
- Skip refunded payments (`is_refunded: true`) for donation/shop supports.
- Strip a leading URL prefix from the name (`https://www.facebook.com/joe` →
  `joe`).
- Strip a trailing email-domain suffix (`joe@hotmail.com` → `joe`, tolerating
  spaces around `@`).
- Drop the literal name `"Someone"` (BMC's placeholder for a fully anonymous
  donor) — also watch for lookalike placeholder values (e.g.
  `defaults.someone`) that mean the same thing but won't match a literal
  string check; flag these to the user rather than silently keeping or
  dropping them.
- Dedupe case-insensitively across all four sources combined, keeping the
  first (i.e. most recent, since results are newest-first) occurrence.
- Escape for Android string resources: backslash-escape a leading `@` or `?`.

### Step 4 — Flag anomalies before writing anything

Don't silently "fix" surprising data. In particular:

- A name containing a literal comma will visually blend into the
  comma-separated list — ask the user whether to keep it verbatim or sanitize
  it.
- Placeholder-looking names (see Step 3) — ask whether to drop or keep.

Use `AskUserQuestion` for each distinct anomaly found. Only proceed to Step 5
once resolved.

### Step 5 — Write the final list and splice it in

Write the final, cleaned, one-name-per-line list to a scratch file, then:

```bash
python3 tools/sync-credits.py sponsors --sponsors-file /path/to/names.txt
```

This escapes each name and rewrites `sponsors_text` in
`app/src/main/res/values/contributors.xml`, leaving `contributors_text`
untouched.

## When run as part of skymap.release

`skymap.release` Step 2 links here instead of v1's separate
`skymap.sponsors`/`skymap.contributors` skills. Ask the user whether they want
sponsors, contributors, or both refreshed for the release — don't assume.

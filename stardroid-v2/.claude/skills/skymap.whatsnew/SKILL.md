---
name: skymap.whatsnew
description: Fully automated release note generator for Sky Map v2 (stardroid-v2). Just provide the last tag.
dependencies: python>=3.8
---

# Sky Map v2 Release Assistant

Same idea as v1's `skymap.whatsnew` skill (same `generate_changelog.py` tool, copied unmodified
into v2's `tools/` — the underlying repo and tags are shared across `stardroid-v1/` and
`stardroid-v2/`), but the **output targets differ**: v2 mirrors v1's split of large HTML string
content into per-topic files (see `AGENTS.md`), so What's New content lives in
`app/src/main/res/values/whatsnew.xml`, not `strings.xml`.

## Procedures

### 1. Data Retrieval

- When a user provides a tag (e.g., "v2.0.0"), execute (from `stardroid-v2/`):
  `python3 tools/generate_changelog.py [TAG]`
- This returns a JSON object with `release_date`, `commits`, `closed_issues`, and `merged_prs`.
- **Do not ask the user for a date.** Use the `release_date` provided by the tool.

### 2. Pre-flight: Read Existing Output Files

Before drafting, read both output files if they exist:
- `app/src/main/res/values/whatsnew.xml` — look for the `whats_new_content` string
- `fastlane/metadata/android/en-US/changelogs/default.txt`

This skill may be run multiple times before a release. Treat the existing `whats_new_content`
value as cumulative output from previous runs — preserve all existing items and only add changes
not yet covered.

### 3. Synthesis Logic

- Prefer `merged_prs` descriptions over raw commit messages — PR authors write summaries for review.
- Match commit messages to issue numbers where helpful.
- Prioritize issue titles over commit messages for clarity.
- Re-evaluate the best items across **all** changes since the tag (not just new ones) when updating `default.txt`.
- If the user provides additional instructions (e.g., "focus on UI"), prioritize those items.
- Sky Map v2 is a from-scratch Kotlin rewrite in active development — many commits are internal
  refactors, architecture work, or test additions with no user-visible effect. Filter these out
  aggressively; a v2 What's New entry should only ever describe things a user would notice.

### 4. Output Generation

#### Target A: `whats_new_content` string in `app/src/main/res/values/whatsnew.xml`

Edit the existing `<string name="whats_new_content" ...>` entry (a `CDATA` HTML block just above
`whats_new_support` in the same file — see the string's `translation_description` for markup
rules). This file also holds `whats_new_support` and `beta_user_help_text` — don't create a
separate `whatsnew_content.xml`, that's a v1-only split v2 doesn't mirror at that granularity.

- Use `<h2>` for each feature heading followed by a short descriptive paragraph.
- Only use `<ul><li>` to group multiple minor items under a single heading.
- Keep it concise. This content is shown both in the What's New dialog
  (`StartupDialogs.kt` → `WhatsNewDialog`) and appended to the Help screen — don't let it grow
  so long it dominates either.
- Do not touch `whats_new_support` (the "support the project" paragraph) or `beta_user_help_text`
  (the beta-feedback ask) in the same file, or `whats_new_dialog_title`/`whats_new_version_heading`
  in `strings.xml` — those are static, not regenerated per release.
- Preserve the exact `<![CDATA[ ... ]]>` wrapper and the `<` / `>` escaping rules noted in the
  string's `translation_description`.

#### Target B: Fastlane metadata `default.txt` under `fastlane/metadata/android/en-US/changelogs`

- **STRICT TOTAL LIMIT: 350 CHARACTERS** — verify with `wc -m` after writing.
- Use `<b><font color="#F67E81">Section</font></b>` headings to group items.
- Select the most impactful 2-3 changes from the full set since the tag.
- Provide the character count at the end.

#### Target C: Markdown for a release on GitHub

- The output should be prepended to `../CHANGELOG.md` (repo root, shared with v1 — check for an
  existing version heading pattern and keep entries clearly distinguishable as v2 work) and use
  GitHub markup.
- The release notes here can be more thorough and detailed than targets A and B.
- The target audience is developers/comfortable-with-alternate-install-sources users, same as v1.
- Since v2 currently ships as `2.0.0-alpha0X`-style versions predating any public release, make
  clear in the heading that this is a pre-release/development build, not a stable release, unless
  the user says otherwise.

## Instructions

- Always perform the Python tool call first before drafting.
- Always read existing output files before writing (see Pre-flight above).
- Never report internal refactors or chore-level commits to the user.
- **After writing all three targets, pause and show the user the English `whats_new_content`
  string and `default.txt` content for review. Ask explicitly: "Does this look good?"** Once
  approved, v2's translation pipeline (`.tmconfig.toml` + the `tm` CLI, same tool as v1) is now
  live — run `tm translate --all-primary --include-stale` so the new/changed English content is
  translated into all 28 core locales, then verify with `tm languages` (100% coverage, 0 stale
  for every `primary_languages` entry; `ca`/`hu`/`ru` are intentionally excluded and will show
  low coverage). See `stardroid-v2/AGENTS.md`'s Translations section.

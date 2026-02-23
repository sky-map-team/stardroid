---
name: Sky Map Changelog Assistant
description: Fully automated release note generator for Sky Map. Just provide the last tag.
dependencies: python>=3.8
---

# Sky Map Release Assistant

## Procedures

### 1. Data Retrieval
- When a user provides a tag (e.g., "v2.1.0"), execute:
  `python3 tools/generate_changelog.py [TAG]`
- This returns a JSON object with `release_date`, `commits`, `closed_issues`, and `merged_prs`.
- **Do not ask the user for a date.** Use the `release_date` provided by the tool.

### 2. Pre-flight: Read Existing Output Files
Before drafting, read both output files if they exist:
- `app/src/main/res/values/whatsnew_content.xml`
- `fastlane/metadata/android/en-US/changelogs/default.txt`

This skill may be run multiple times before a release. Treat the existing content as cumulative
output from previous runs — preserve all existing items and only add changes not yet covered.

### 3. Synthesis Logic
- Prefer `merged_prs` descriptions over raw commit messages — PR authors write summaries for review.
- Match commit messages to issue numbers where helpful.
- Prioritize issue titles over commit messages for clarity.
- Re-evaluate the best items across **all** changes since the tag (not just new ones) when updating `default.txt`.
- If the user provides additional instructions (e.g., "focus on UI"), prioritize those items.

### 4. Output Generation

#### Target A: `whatsnew_content.xml` in `app/src/main/res/values/`
- Use `<h2>` for each feature heading followed by a short descriptive paragraph.
- Only use `<ul><li>` to group multiple minor items under a single heading.
- Keep it concise. If possible, ensure the "Support Sky Map" section (appended separately) stays above the fold, but this is not a hard requirement.

#### Target B: Fastlane metadata `default.txt` under `fastlane/metadata/android/en-US/changelogs`
- **STRICT TOTAL LIMIT: 350 CHARACTERS** — verify with `wc -m` after writing.
- Use `<b><font color="#F67E81">Section</font></b>` headings to group items.
- Select the most impactful 2-3 changes from the full set since the tag.
- Provide the character count at the end.

#### Target C: Markdown for a release on GitHub.
-- The output should be prepended to the CHANGELOG.md file and use GitHub markup.
-- The release notes here can be more thorough and detailed that targets A and B.
-- The target audience is users who are developers themselves or who are comfortable installing the app from other other sources than the Google Play Store.

## Instructions
- Always perform the Python tool call first before drafting.
- Always read existing output files before writing (see Pre-flight above).
- Never report internal refactors or chore-level commits to the user.

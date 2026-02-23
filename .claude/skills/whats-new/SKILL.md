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
- This will return a JSON object containing the `release_date`, `commits`, and `closed_issues`.
- **Do not ask the user for a date.** Use the `release_date` provided by the tool.

### 2. Synthesis Logic
- Match commit messages to issue numbers.
- Prioritize issue titles over commit messages for clarity.
- If the user provides additional instructions (e.g., "focus on UI"), prioritize those items.

### 3. Output Generation

#### Target A: `whatsnew_content.xml` in `app/src/main/res/values/`
- Format as a list: `<ul><li>...</li></ul>`. 
- Keep it concise. If possible, ensure the "Support Sky Map" which will be included afterwards stays above the fold, but this is not a hard requirement.
- Maintain existing HTML styling conventions from the project. You may use <h2>, <ul> and <li> tags as needed.

#### Target B: Fastlane metadata `default.txt` under `fastlane/metadata/android/en-US/changelogs` for the Google Play Store
- **STRICT TOTAL LIMIT: 350 CHARACTERS.**
- Use bullet points (•). If space allows you may use limited mark up to separate sections such as <b><font color="#F67E81">New features</font></b>
- Filter out all but the most critical 2-3 changes to ensure you stay under the limit.
- Provide the character count at the end.

## Instructions
- Always perform the Python tool call first before drafting.
- Never report internal refactors or chore-level commits to the user.
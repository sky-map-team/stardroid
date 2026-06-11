---
name: skymap.respond-reviews
description: Use when asked to respond to, reply to, or draft replies for Google Play Store reviews for Sky Map. Triggers on "respond to reviews", "reply to reviews", "draft review replies", or any request to handle user feedback from the Play Store.
---

# Sky Map — Respond to Play Store Reviews

## Overview

This skill fetches unanswered Google Play reviews, drafts empathetic replies grounded in the
troubleshooting guide, presents them for approval, then posts the approved replies.

**Never post a reply without explicit user approval.**

Package name: `com.google.android.stardroid`

---

## Step 1 — Fetch reviews

### Live reviews (default)

Use `mcp__google-play-reviews__list_reviews` with `unansweredOnly: true`.

**Always use server-side filters to avoid fetching huge result sets.** Fetching without filters
can return thousands of reviews and hit token limits.

Apply any filters the user provides:
- **Star rating** — no server-side filter available; fetch with other filters first, then filter client-side by `review.starRating`
- **Keywords / topics** — **always pass as `searchText`** — use the server to narrow results before they reach the client. For multiple topics (e.g. "compass or location"), make two parallel calls with different `searchText` values.
- **Language** — pass as `language` (BCP-47 code)
- **Date range** — pass as `startDate`/`endDate` for historical reviews; filter client-side by `review.lastModified` for live reviews

### Historical reviews (CSV dumps)

If the user asks for reviews **older than what the Play API returns** (typically ~7 days), or
explicitly references "historical" / "old" reviews, **read the CSV files directly** — this is
faster and more flexible than the MCP server's `list_historical_reviews` (which is redundant).

CSV files live at `~/Code/production/stardroidreviews/reviews/` and are named
`reviews_com.google.android.stardroid_YYYYMM.csv`. Use `grep`, `find`, or direct file reads to
filter by date range, keyword, star rating, language, etc.

**Caveat:** The CSV is a static snapshot. Reply status reflects the export time — replies posted
after the CSV was generated will not appear, so some "unanswered" results may already have
replies. Treat results as candidates and note this limitation to the user.

---

## Step 2 — Classify each review

For each review, classify the issue before drafting. Use the most specific matching class —
the sub-classes below have different response framing.

| Class | Signals |
|---|---|
| **compass-inaccurate** | "wrong direction", "points wrong way", "inaccurate", "off by X degrees", "compass" |
| **compass-calibrated-still-wrong** | "I calibrated it but it's still wrong/inaccurate", "figure-8 doesn't help", "calibrated 50 times" |
| **compass-broke-after-update** | "worked before the update", "broke after update", "used to work fine", "last update ruined it" |
| **map-frozen** | "doesn't move", "stuck", "frozen", "won't track" |
| **jitter** | "jittery", "shaky", "jumpy", "wobbles", "stutters", "jerky" |
| **location** | "wrong location", "wrong city", "Polaris near horizon", "latitude", "permission" |
| **time/timezone** | "wrong time", "time zone", "off by hours", "clock" |
| **very-negative** | 1–2 stars with no specific technical complaint, "useless", "garbage" |
| **positive** | 4–5 stars with no complaint |
| **other** | anything else |

---

## Step 3 — Draft the reply

Read `@../troubleshooting-llm.md` before drafting any complaint reply — it contains response
framing, ordered steps, and phrases to avoid for each complaint class. Also read
`@../troubleshooting.md` for the full technical detail. You only need to read these if there are
complaints or issues — not needed for purely positive reviews.
Base all advice strictly on what those documents say — **do not invent steps, settings, or
explanations not found there**. DO NOT SPECULATE that changes in Android APIs or updates to Sky Map
might have been the cause. If in doubt, ask me. Replies need to be limited to 350 characters.

### Complaint replies

- Open with genuine empathy (1 sentence). Use "sorry to hear" phrasing — not "sorry" in a way
  that implies fault. We didn't cause the compass hardware issue.
- Give the relevant fix from troubleshooting.md, concisely.
- When the issue is hardware (compass bias, missing gyroscope) be **clear and firm**: Sky Map
  can only work with what the sensor provides. Don't soften this to the point of implying
  Sky Map might fix it.
- **Do not suggest toggling Magnetic Correction** — it only adjusts for magnetic vs. true north
  offset and is unlikely to help with typical compass accuracy complaints.
- **Do not suggest reinstalling, clearing data, or waiting for a Sky Map update** — none of these
  fix a hardware compass issue.
- **Do not blame Android or firmware updates** — don't speculate about what changed on the user's
  device.
- For users with a persistent/consistent compass offset: suggest the manual compass offset in
  Settings → Sensor Settings (Experts).
- Mention that some phones simply have bad compass hardware — this is worth noting when calibration
  repeatedly fails or the error is consistent across environments.
- When referencing Diagnostics: it shows the sensor's **calibration status**, not accuracy. A
  sensor showing "High" calibration can still point in the wrong direction.
- Close with an offer to help further (point to Diagnostics + email).
- Make sure you vary the wording of the replies, even when addressing similar complaints.

### Positive replies

- Warm and gracious, 2–3 sentences.
- *YOU MUST Vary the tone* — don't use the same template for every positive reply.
- A light sky/star pun is welcome but never forced.
- Thank them by name if possible.

### Language

- Write the reply in the **reviewer's native language** (use `review.reviewerLanguage`, or infer
  from the review text if absent). Use US English for English reviews unless you know it's another English locale.
- Always provide an **English translation** for the user's review as well as your response.
- If the reply contains a pun that only works in the native language, **explain the pun in
  English**.

---

## Step 4 — Present proposals for approval

Show each proposed reply in this format:

```
---
Review #N  ★★★☆☆  [language]  [date]
Reviewer: <name>
Original: "<review text>"
[English translation of review: "<translation>" — only if original is not English]

Proposed reply (<language name>):
"<reply in native language>"

English translation of reply:
"<English translation>"

[Pun explanation: <explanation> — only if applicable]
---
```

After showing all proposals, ask: **"Shall I post these replies? You can approve all, approve
specific ones by number, or ask me to revise any."**

---

## Step 5 — Post approved replies

Only after the user approves (all or specific ones), use `mcp__google-play-reviews__reply_to_review`
to post each approved reply.

Confirm how many replies were posted when done.

---

## Rules

- **Propose first, post after approval. No exceptions.**
- **Do not invent troubleshooting advice.** If a complaint doesn't match anything in
  troubleshooting.md, say so honestly and offer the Diagnostics + email fallback.
- **Do not translate the user's approved edits** — if the user rewrites a reply in English, post
  their version as-is.
- Historical CSV reviews can be replied to — the CSV contains the full review ID, which is all
  `reply_to_review` needs alongside the package name.

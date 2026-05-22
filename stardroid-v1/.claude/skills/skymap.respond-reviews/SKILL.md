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

Apply any filters the user provides:
- **Star rating** — fetch all, then filter client-side by `review.starRating`
- **Keywords** — pass as `searchText`
- **Language** — pass as `language` (BCP-47 code)
- **Date range** — fetch all unanswered, then filter client-side by `review.lastModified`

### Historical reviews (CSV dumps)

If the user asks for reviews **older than what the Play API returns** (typically ~3 months), or
explicitly references "historical" / "old" reviews, switch to
`mcp__google-play-reviews__list_historical_reviews`.

The CSV files live in `/tmp`. The MCP server reads them from `PLAY_REVIEWS_DIR`; if that env var
isn't set, tell the user to set it to `/tmp` (or wherever the CSVs are) before proceeding.

The same filters apply: `startDate`, `endDate`, `searchText`, `language`, `unansweredOnly`.

---

## Step 2 — Classify each review

For each review, classify the issue before drafting:

| Class | Signals |
|---|---|
| **compass** | "wrong direction", "points wrong way", "inaccurate", "off by X degrees", "compass" |
| **jitter** | "jittery", "shaky", "jumpy", "wobbles", "stutters", "jerky" |
| **location** | "wrong location", "wrong city", "Polaris near horizon", "latitude", "permission" |
| **time/timezone** | "wrong time", "time zone", "off by hours", "clock" |
| **positive** | 4–5 stars with no complaint |
| **other** | anything else |

---

## Step 3 — Draft the reply

Read `@../troubleshooting.md` before drafting any complaint reply. Base all advice strictly on
what that document says — **do not invent steps, settings, or explanations not found there**.

### Complaint replies

- Open with genuine empathy (1 sentence).
- Give the relevant fix from troubleshooting.md, concisely.
- When the issue is hardware (compass bias, missing gyroscope) be **clear and firm**: Sky Map
  can only work with what the sensor provides. Don't soften this to the point of implying
  Sky Map might fix it.
- Close with an offer to help further (point to Diagnostics + email).

### Positive replies

- Warm and gracious, 2–3 sentences.
- Vary the tone — don't use the same template for every positive reply.
- A light sky/star pun is welcome but never forced.

### Language

- Write the reply in the **reviewer's native language** (use `review.reviewerLanguage`, or infer
  from the review text if absent).
- Always provide an **English translation** for the user's review.
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

Proposed reply (<language name>):
"<reply in native language>"

English translation:
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

# Satellite download alerts

`Code.gs` is a Google Apps Script that reads GA4 every 4 hours and emails the maintainer when,
for today or any of the previous 3 days:

- `satellite_fetch_failed` is over 200 a day (split by `http_code`; `timeout` means a
  firewall block, reported once a timeout opens the circuit), or
- estimated downloads (`satellite_fetch_ok` × 100) are over `DAILY_BUDGET`. This check is off
  until a budget is agreed with CelesTrak (#1274).

It emails once per day of data, and again only if that day grows tenfold.

It is an Apps Script because GA4 custom insights can't filter on an event name, and because it
runs as the maintainer, so no service-account key has to be stored in this public repository.

## The GitHub Action is deferred

`.github/workflows/satellite-alerts.yml` and `../satellite_alerts.py` are an earlier solution to
the same problem, kept in case this one doesn't hold up. The Action has no credentials, so each
run skips and shows green: **a green run there does not mean anything was checked.** Before
configuring it, fix two problems:

1. `pip install` then `python3 -I` can't import `google.auth`, as `-I` ignores the user
   site-packages. Install into a venv instead.
2. Its 3x trailing-average rule absorbs an incident. Replayed on November 2026's data it missed
   4 of the 10 bad days, and stayed desensitized for about a week afterwards.

## Setup

1. Signed in as the account that should receive alerts (it needs Viewer on GA4 property
   242522567), create a project at [script.google.com](https://script.google.com).
2. Replace the contents of `Code.gs` with this directory's copy.
3. In the editor, next to **Services**, click **+** and add **Google Analytics Data API**,
   version `v1beta`, identifier `AnalyticsData`.
4. Run `sendTestEmail`. At the "Google hasn't verified this app" warning, click **Advanced** >
   **Go to** the project, and grant every permission listed. Check the email against GA4.
5. Run `checkSatelliteFailures` once. It should finish without errors.
6. Under **Triggers**, add `checkSatelliteFailures`: Time-driven, Hour timer, every 4 hours,
   failure notifications **Notify me immediately**.
7. A few hours later, check **Executions** for a completed Time-Driven run.

## Changing it

Merging does **not** deploy. Paste the new `Code.gs` into the project, keeping any edit made to
`email()` there, then run `sendTestEmail`. The trigger and alert history survive. Renaming
`checkSatelliteFailures` breaks the trigger.

Test from this directory with:

    cat Code.gs test_code.js | node

## What it cannot see

- A firewall block: timeouts send no event (#1348).
- F-Droid installs, and installs with analytics off or the terms not accepted.
- Events that arrive more than 3 days late.
- Analytics stopping altogether, which looks like a quiet day.

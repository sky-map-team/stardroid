#!/usr/bin/env python3
"""Checks Sky Map's satellite download health in GA4 and prints a Markdown alert, if any.

Alerts when, for today (so far) or yesterday:
  1. satellite_fetch_failed exceeds FACTOR x its trailing 7-day daily average AND a floor, or
  2. estimated downloads (sampled satellite_fetch_ok x sample size) exceed a daily budget.

Failures are split by the http_code parameter (register it as an event-scoped custom
dimension in GA4, or the split is empty). GA data for background work arrives hours late, so
thresholds are set against analytics' own baseline.

Needs GA_PROPERTY_ID and Google credentials (Application Default Credentials: a service
account key via GOOGLE_APPLICATION_CREDENTIALS, or Workload Identity Federation). With either
absent it exits 0 saying so. Exit status is 0 whether or not there is an alert; the alert is
the Markdown on stdout (empty when all is well). Run locally with --dry-run to print the
numbers instead.

Env: GA_PROPERTY_ID, SATELLITE_FAILURE_FLOOR (500), SATELLITE_FAILURE_FACTOR (3),
SATELLITE_DAILY_BUDGET (unset = volume check off), SATELLITE_SAMPLE_ONE_IN (100; must match
SATELLITE_FETCH_OK_SAMPLE_ONE_IN in AnalyticsEvents.kt).
"""

import json
import os
import sys
from datetime import date, timedelta

FAILED = "satellite_fetch_failed"
OK = "satellite_fetch_ok"
API = "https://analyticsdata.googleapis.com/v1beta/properties/{}:runReport"


def daily_counts(rows):
    """[(date_iso, event, http_code, count)] -> {event: {date_iso: n}} and failed-by-code."""
    totals, by_code = {}, {}
    for day, event, code, n in rows:
        totals.setdefault(event, {}).setdefault(day, 0)
        totals[event][day] += n
        if event == FAILED:
            by_code.setdefault(day, {}).setdefault(code or "(not set)", 0)
            by_code[day][code or "(not set)"] += n
    return totals, by_code


def evaluate(rows, today, floor=500, factor=3.0, budget=None, sample_one_in=100):
    """Returns (alerts, summary lines). Pure, so it can be tested without GA."""
    totals, by_code = daily_counts(rows)
    failed, ok = totals.get(FAILED, {}), totals.get(OK, {})
    alerts, summary = [], []
    for day in (today - timedelta(days=1), today):
        iso = day.isoformat()
        baseline = [failed.get((day - timedelta(days=i)).isoformat(), 0) for i in range(1, 8)]
        average = sum(baseline) / 7
        count = failed.get(iso, 0)
        label = f"{iso}{' (so far)' if day == today else ''}"
        summary.append(f"- {label}: {count} failures (7-day average {average:.0f})")
        if count > floor and count > factor * average:
            codes = ", ".join(
                f"{c}: {n}" for c, n in sorted(by_code.get(iso, {}).items(), key=lambda kv: -kv[1])
            )
            alerts.append(
                f"**{label}**: {count} `{FAILED}` events, over {factor:g}x the trailing 7-day "
                f"average ({average:.0f}) and the floor ({floor}). By http_code: {codes}."
            )
        estimate = ok.get(iso, 0) * sample_one_in
        summary.append(f"- {label}: ~{estimate} estimated downloads ({ok.get(iso, 0)} sampled)")
        if budget is not None and estimate > budget:
            alerts.append(
                f"**{label}**: ~{estimate} estimated downloads ({ok.get(iso, 0)} sampled x "
                f"{sample_one_in}), over the budget of {budget} a day."
            )
    return alerts, summary


def fetch_rows(property_id, today):
    import google.auth
    from google.auth.transport.requests import AuthorizedSession

    credentials, _ = google.auth.default(
        scopes=["https://www.googleapis.com/auth/analytics.readonly"]
    )
    body = {
        "dateRanges": [{"startDate": (today - timedelta(days=8)).isoformat(),
                        "endDate": today.isoformat()}],
        "dimensions": [{"name": "date"}, {"name": "eventName"},
                       {"name": "customEvent:http_code"}],
        "metrics": [{"name": "eventCount"}],
        "dimensionFilter": {"filter": {"fieldName": "eventName", "inListFilter": {
            "values": [FAILED, OK]}}},
        "limit": 10000,
    }
    response = AuthorizedSession(credentials).post(API.format(property_id), json=body)
    response.raise_for_status()
    rows = []
    for row in response.json().get("rows", []):
        d, event, code = (v["value"] for v in row["dimensionValues"])
        rows.append((f"{d[:4]}-{d[4:6]}-{d[6:]}", event,
                     "" if code == "(not set)" else code,
                     int(row["metricValues"][0]["value"])))
    return rows


def main(argv):
    property_id = os.environ.get("GA_PROPERTY_ID", "")
    if not property_id:
        print("GA_PROPERTY_ID not set; skipping.", file=sys.stderr)
        return 0
    if not (os.environ.get("GOOGLE_APPLICATION_CREDENTIALS") or "--adc" in argv):
        print("No Google credentials configured; skipping.", file=sys.stderr)
        return 0
    budget = os.environ.get("SATELLITE_DAILY_BUDGET")
    today = date.today()
    alerts, summary = evaluate(
        fetch_rows(property_id, today),
        today,
        floor=int(os.environ.get("SATELLITE_FAILURE_FLOOR", "500")),
        factor=float(os.environ.get("SATELLITE_FAILURE_FACTOR", "3")),
        budget=int(budget) if budget else None,
        sample_one_in=int(os.environ.get("SATELLITE_SAMPLE_ONE_IN", "100")),
    )
    if "--dry-run" in argv:
        print("\n".join(summary))
        print(json.dumps(alerts, indent=2))
    elif alerts:
        print("\n\n".join(alerts))
        print("\n\nRecent numbers:\n" + "\n".join(summary))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

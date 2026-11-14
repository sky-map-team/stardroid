#!/usr/bin/env python3
"""Prints a Markdown alert if satellite downloads look unhealthy in GA4 (nothing if healthy).

Alerts, for today so far or yesterday, when satellite_fetch_failed exceeds FACTOR x its
trailing 7-day average and a floor (split by http_code), or when estimated downloads
(sampled satellite_fetch_ok x SAMPLE_ONE_IN) exceed a budget. Background analytics arrive
hours late, so thresholds are set against GA's own baseline.

Auth is Application Default Credentials (a service account key or Workload Identity Federation).
Env: GA_PROPERTY_ID (required, else skips), SATELLITE_FAILURE_FLOOR (500),
SATELLITE_FAILURE_FACTOR (3), SATELLITE_DAILY_BUDGET (unset = volume check off).
Local run: add --dry-run to print the numbers.
"""

import json
import os
import re
import sys
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

FAILED = "satellite_fetch_failed"
OK = "satellite_fetch_ok"
# Must match SATELLITE_FETCH_OK_SAMPLE_ONE_IN in AnalyticsEvents.kt.
SAMPLE_ONE_IN = 100
API = "https://analyticsdata.googleapis.com/v1beta/properties/{}:runReport"


def daily_counts(rows):
    totals, by_code = {}, {}
    for day, event, code, n in rows:
        totals.setdefault(event, {}).setdefault(day, 0)
        totals[event][day] += n
        if event == FAILED:
            # http_code is client-supplied; only real status codes may reach the issue text.
            code = code if re.fullmatch(r"\d{3}", code or "") else "other"
            by_code.setdefault(day, {}).setdefault(code, 0)
            by_code[day][code] += n
    return totals, by_code


def evaluate(rows, today, floor=500, factor=3.0, budget=None, sample_one_in=SAMPLE_ONE_IN):
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
                f"{sample_one_in}), over the budget of {budget} a day. This is a lower bound: installs "
                f"with analytics off are not counted, and a sample this small is noisy."
            )
    return alerts, summary


def fetch_rows(property_id):
    """Returns (rows, property time zone). Dates are in the property's zone, not the runner's."""
    import google.auth
    from google.auth.transport.requests import AuthorizedSession

    credentials, _ = google.auth.default(
        scopes=["https://www.googleapis.com/auth/analytics.readonly"]
    )
    session = AuthorizedSession(credentials)

    def run(with_code):
        dimensions = [{"name": "date"}, {"name": "eventName"}]
        if with_code:
            dimensions.append({"name": "customEvent:http_code"})
        return session.post(API.format(property_id), json={
            "dateRanges": [{"startDate": "9daysAgo", "endDate": "today"}],
            "dimensions": dimensions,
            "metrics": [{"name": "eventCount"}],
            "dimensionFilter": {"filter": {"fieldName": "eventName", "inListFilter": {
                "values": [FAILED, OK]}}},
            "limit": 10000,
        })

    response = run(True)
    with_code = response.status_code != 400
    if not with_code:
        # An unregistered custom dimension is rejected; carry on without the split.
        print(f"400 with http_code, retrying without: {response.text[:200]}", file=sys.stderr)
        response = run(False)
    response.raise_for_status()
    data = response.json()
    rows = []
    for row in data.get("rows", []):
        values = [v["value"] for v in row["dimensionValues"]]
        d, event = values[0], values[1]
        code = values[2] if with_code else ""
        rows.append((f"{d[:4]}-{d[4:6]}-{d[6:]}", event,
                     "" if code == "(not set)" else code,
                     int(row["metricValues"][0]["value"])))
    return rows, data.get("metadata", {}).get("timeZone", "UTC")


def thresholds(env):
    """Env -> evaluate() kwargs. Empty counts as unset: unset Actions variables arrive as ''."""
    budget = env.get("SATELLITE_DAILY_BUDGET")
    return {
        "floor": int(env.get("SATELLITE_FAILURE_FLOOR") or 500),
        "factor": float(env.get("SATELLITE_FAILURE_FACTOR") or 3),
        "budget": int(budget) if budget else None,
    }


def main(argv):
    property_id = os.environ.get("GA_PROPERTY_ID", "")
    if not property_id:
        print("GA_PROPERTY_ID not set; skipping.", file=sys.stderr)
        return 0
    rows, zone = fetch_rows(property_id)
    alerts, summary = evaluate(
        rows,
        datetime.now(ZoneInfo(zone)).date(),
        **thresholds(os.environ),
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

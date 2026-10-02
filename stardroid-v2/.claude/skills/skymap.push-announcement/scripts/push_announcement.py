#!/usr/bin/env python3
"""Manage the Sky Map `announcements` Remote Config parameter.

Dry-run by default; pass --publish to write. See ../SKILL.md.
"""
import argparse
import json
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone

PROJECT = "sky-map-1286"
URL = f"https://firebaseremoteconfig.googleapis.com/v1/projects/{PROJECT}/remoteConfig"
KEY = "announcements"
FLAG = "announcements_enabled"
SURFACES = {"notification", "widget", "interstitial"}
MAX_MESSAGES = 3  # matches AnnouncementParser.MAX_MESSAGES
SCHEMA_VERSION = 1


def token():
    out = subprocess.run(
        ["gcloud", "auth", "print-access-token"], capture_output=True, text=True
    )
    if out.returncode != 0:
        sys.exit(f"gcloud auth failed: {out.stderr.strip()}\nRun: gcloud auth login")
    return out.stdout.strip()


def call(method, body=None, etag=None):
    headers = {"Authorization": f"Bearer {token()}", "Accept-Encoding": "identity"}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json; UTF8"
        headers["If-Match"] = etag or "*"
    req = urllib.request.Request(URL, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read() or b"{}"), resp.headers.get("ETag")
    except urllib.error.HTTPError as e:
        sys.exit(f"HTTP {e.code} from Remote Config: {e.read().decode()[:500]}")


def parse_instant(s):
    if not isinstance(s, str):
        raise ValueError("not a string")
    return datetime.fromisoformat(s.replace("Z", "+00:00")).astimezone(timezone.utc)


def validate(msg):
    """Same acceptance rules as AnnouncementParser; returns a list of problems."""
    problems = []
    if not isinstance(msg.get("id"), str) or not msg["id"].strip():
        problems.append("id must be a non-empty string")
    try:
        start, end = parse_instant(msg.get("start")), parse_instant(msg.get("end"))
        if end <= start:
            problems.append("end must be after start")
    except (ValueError, TypeError):
        problems.append("start/end must be ISO-8601 instants, e.g. 2026-10-03T16:00:00Z")
    surfaces = msg.get("surfaces")
    if not isinstance(surfaces, list) or not surfaces:
        problems.append("surfaces must be a non-empty list")
    else:
        unknown = [s for s in surfaces if s not in SURFACES]
        if unknown:
            problems.append(f"unknown surfaces {unknown}; allowed: {sorted(SURFACES)}")
    text = msg.get("text")
    if not isinstance(text, dict) or not text:
        problems.append("text must map language tags to {title, body}")
    else:
        for tag, t in text.items():
            if not isinstance(t, dict) or not str(t.get("title", "")).strip():
                problems.append(f"text[{tag}] needs a non-empty title")
        if "en" not in {k.lower() for k in text}:
            problems.append("text must include an 'en' fallback")
    action = msg.get("action")
    if action is not None:
        if not isinstance(action, dict) or action.get("type") not in ("open_sky", "search"):
            problems.append("action.type must be open_sky or search")
        elif action["type"] == "search" and not str(action.get("arg", "")).strip():
            problems.append("a search action needs an arg")
    return problems


def load(template):
    param = template.get("parameters", {}).get(KEY)
    raw = (param or {}).get("defaultValue", {}).get("value", "")
    if not raw.strip():
        return []
    doc = json.loads(raw)
    if doc.get("v") != SCHEMA_VERSION:
        sys.exit(f"Live payload has schema v={doc.get('v')}; this tool writes v={SCHEMA_VERSION}")
    return doc.get("messages", [])


def store(template, messages):
    # Newest start first and capped, like the app, so what we publish is what devices keep.
    messages = sorted(messages, key=lambda m: m["start"], reverse=True)
    if len(messages) > MAX_MESSAGES:
        sys.exit(
            f"{len(messages)} messages would be live but devices use only {MAX_MESSAGES}. "
            "Remove or prune one first."
        )
    value = json.dumps({"v": SCHEMA_VERSION, "messages": messages}, ensure_ascii=False,
                       separators=(",", ":"))
    params = template.setdefault("parameters", {})
    params[KEY] = {
        **params.get(KEY, {}),
        "defaultValue": {"value": value},
        "valueType": "STRING",
        "description": "Remote announcements (see docs/design/remote-announcements.md)",
    }
    return value


def set_flag(template, on):
    params = template.setdefault("parameters", {})
    params[FLAG] = {
        **params.get(FLAG, {}),
        "defaultValue": {"value": "true" if on else "false"},
        "valueType": "BOOLEAN",
    }


def summarize(messages):
    if not messages:
        print("  (no messages)")
    for m in messages:
        print(f"  {m['id']}  {m['start']} -> {m['end']}  surfaces={m['surfaces']}")
        for tag, t in m["text"].items():
            print(f"      [{tag}] {t['title']} — {t.get('body', '')}")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("command", choices=["add", "list", "remove", "prune", "enable", "disable"])
    ap.add_argument("arg", nargs="?", help="message.json for add, message id for remove")
    ap.add_argument("--publish", action="store_true", help="actually write (default: dry run)")
    args = ap.parse_args()

    template, etag = call("GET")
    messages = load(template)
    cmd = args.command

    if cmd == "list":
        print(f"Flag {FLAG} = {template.get('parameters', {}).get(FLAG, {}).get('defaultValue', {}).get('value', '(unset)')}")
        summarize(messages)
        return

    before = list(messages)
    if cmd == "add":
        if not args.arg:
            sys.exit("add needs a message.json path")
        with open(args.arg) as f:
            msg = json.load(f)
        problems = validate(msg)
        if problems:
            sys.exit("Message rejected:\n  - " + "\n  - ".join(problems))
        if any(m["id"] == msg["id"] for m in messages):
            sys.exit(f"id {msg['id']!r} is already live. Ids are never reused; pick a new one "
                     "or `remove` it first.")
        messages.append(msg)
    elif cmd == "remove":
        if not args.arg or not any(m["id"] == args.arg for m in messages):
            sys.exit(f"No live message with id {args.arg!r}")
        messages = [m for m in messages if m["id"] != args.arg]
    elif cmd == "prune":
        now = datetime.now(timezone.utc)
        messages = [m for m in messages if parse_instant(m["end"]) > now]
    elif cmd in ("enable", "disable"):
        set_flag(template, cmd == "enable")

    if cmd not in ("enable", "disable"):
        value = store(template, messages)
        print(f"Payload size: {len(value)} chars")
        print("Before:")
        summarize(before)
        print("After:")
        summarize(messages)
    else:
        print(f"{FLAG} -> {'true' if cmd == 'enable' else 'false'}")

    if not args.publish:
        print("\nDRY RUN — nothing was published. Re-run with --publish to apply.")
        return
    call("PUT", template, etag)
    print("\nPublished. Devices pick it up on their next Remote Config fetch (up to ~12 h).")


if __name__ == "__main__":
    main()

"""
Syncs the sponsors_text and contributors_text strings in
app/src/main/res/values/contributors.xml, mirroring stardroid-v1's
tools/sync-sponsors.py and tools/sync-contributors.py.

Unlike v1 (which writes two separate notranslate-*.xml files), v2 keeps both
strings in one file, so this script updates whichever string(s) it has data
for and leaves the rest of the file untouched.

Contributors are fetched live from the GitHub API (unchanged from v1's
approach). Sponsors can no longer be fetched by this script directly — Buy Me
a Coffee's old personal-access-token API endpoint is dead; sponsor data now
comes from the `buymeacoffee` MCP server, which is only callable from inside
a Claude conversation. See the skymap.sync-credits skill for the full
sponsor-fetching workflow (pagination, name cleanup, anomaly review). Once
that workflow has produced a final, cleaned, one-name-per-line list, feed it
to this script with --sponsors-file to splice it into contributors.xml.

Usage:
    GITHUB_TOKEN=... python3 tools/sync-credits.py contributors
    python3 tools/sync-credits.py sponsors --sponsors-file /path/to/names.txt

    With no arguments, syncs contributors only (sponsors requires
    --sponsors-file since there is no direct fetch anymore).
"""

import argparse
import os
import re
import sys

import requests

REPO = "sky-map-team/stardroid"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_PATH = os.path.join(SCRIPT_DIR, "../app/src/main/res/values/contributors.xml")
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN")


def escape_for_android(name):
    name = name.replace("\\", "\\\\")
    name = name.replace("'", "\\'")
    if name.startswith(("@", "?")):
        name = "\\" + name
    return name


def dedupe(names):
    return list(dict.fromkeys(names))


def load_sponsors_file(path):
    with open(path, encoding="utf-8") as f:
        lines = [line.strip() for line in f if line.strip()]
    return dedupe(lines)


def github_headers():
    headers = {"Accept": "application/vnd.github+json"}
    if GITHUB_TOKEN:
        headers["Authorization"] = f"Bearer {GITHUB_TOKEN}"
    return headers


def fetch_commits_ordered():
    """Returns contributor logins in order of most recent commit (first appearance), bots excluded."""
    url = f"https://api.github.com/repos/{REPO}/commits"
    seen = []
    seen_set = set()
    page = 1
    print("Fetching commits from GitHub...")
    while True:
        resp = requests.get(url, headers=github_headers(), params={"per_page": 100, "page": page})
        if resp.status_code == 409:  # empty repo
            break
        resp.raise_for_status()
        data = resp.json()
        if not data:
            break
        for commit in data:
            author = commit.get("author")
            if not author:
                continue
            login = author.get("login", "")
            if not login or login.endswith("[bot]"):
                continue
            key = login.lower()
            if key not in seen_set:
                seen.append(login)
                seen_set.add(key)
        if "next" not in resp.links:
            break
        page += 1
    return seen


def fetch_user_name(login):
    try:
        resp = requests.get(f"https://api.github.com/users/{login}", headers=github_headers())
        if resp.status_code != 200:
            return None
        data = resp.json()
        if not data:
            return None
        name = (data.get("name") or "").strip()
        return name if name else None
    except requests.exceptions.RequestException:
        return None


def fetch_contributors():
    if not GITHUB_TOKEN:
        print("Warning: GITHUB_TOKEN not set — rate limit is 60 req/hr unauthenticated.")

    logins_ordered = fetch_commits_ordered()
    print(f"Found {len(logins_ordered)} unique contributor logins.")

    display_names = []
    seen_normalized = set()
    for i, login in enumerate(logins_ordered, 1):
        print(f"  [{i}/{len(logins_ordered)}] Fetching profile for {login}...")
        name = fetch_user_name(login) or login
        normalized = re.sub(r"[\s\W]+", "", name).lower()
        if normalized not in seen_normalized:
            display_names.append(name)
            seen_normalized.add(normalized)
        else:
            print(f"    Skipping duplicate display name: {name!r}")
    return display_names


def replace_string_element(xml_text, string_name, names):
    joined = ", ".join(escape_for_android(n) for n in names)
    pattern = re.compile(
        rf'(<string name="{string_name}" translatable="false">).*?(</string>)',
        re.DOTALL,
    )
    if not pattern.search(xml_text):
        raise ValueError(f'Could not find <string name="{string_name}"> in {OUTPUT_PATH}')
    return pattern.sub(lambda m: f"{m.group(1)}{joined}{m.group(2)}", xml_text)


def update_xml(sponsors=None, contributors=None):
    with open(OUTPUT_PATH, encoding="utf-8") as f:
        xml_text = f.read()

    if sponsors is not None:
        xml_text = replace_string_element(xml_text, "sponsors_text", sponsors)
        print(f"Updated sponsors_text with {len(sponsors)} names.")

    if contributors is not None:
        xml_text = replace_string_element(xml_text, "contributors_text", contributors)
        print(f"Updated contributors_text with {len(contributors)} names.")

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        f.write(xml_text)

    print(f"Wrote changes to {OUTPUT_PATH}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "targets",
        nargs="*",
        default=["contributors"],
        choices=["sponsors", "contributors"],
        help="Which string(s) to sync. Defaults to contributors only.",
    )
    parser.add_argument(
        "--sponsors-file",
        help="Path to a file with one already-cleaned sponsor name per line "
        "(required when 'sponsors' is a target).",
    )
    args = parser.parse_args()

    if "sponsors" in args.targets and not args.sponsors_file:
        parser.error("'sponsors' target requires --sponsors-file (see skymap.sync-credits skill)")

    sponsors = load_sponsors_file(args.sponsors_file) if "sponsors" in args.targets else None
    contributors = fetch_contributors() if "contributors" in args.targets else None

    update_xml(sponsors=sponsors, contributors=contributors)

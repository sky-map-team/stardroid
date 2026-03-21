import os
import re
import sys
import requests
import xml.etree.ElementTree as ET
from xml.dom import minidom

REPO = "sky-map-team/stardroid"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_PATH = os.path.join(SCRIPT_DIR, "../app/src/main/res/values/notranslate-contributors.xml")
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN")


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
    """Returns the real name for a GitHub user, or None if unavailable."""
    resp = requests.get(f"https://api.github.com/users/{login}", headers=github_headers())
    if resp.status_code != 200:
        return None
    name = resp.json().get("name", "").strip()
    return name if name else None


def escape_for_android(name):
    name = name.replace("\\", "\\\\")
    name = name.replace("'", "\\'")
    if name.startswith(("@", "?")):
        name = "\\" + name
    return name


def update_xml(contributors):
    contributors_str = ", ".join(escape_for_android(c) for c in contributors)
    resources = ET.Element("resources")
    resources.append(ET.Comment(" A comma-separated list of contributors "))
    string_elem = ET.SubElement(resources, "string", name="contributors_text")
    string_elem.text = f"\n        {contributors_str}\n    "
    xml_bytes = ET.tostring(resources, encoding="utf-8")
    pretty_xml = minidom.parseString(xml_bytes).toprettyxml(indent="    ", encoding="utf-8")
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, "wb") as f:
        f.write(pretty_xml)
    print(f"Wrote {len(contributors)} contributors to {OUTPUT_PATH}")


if __name__ == "__main__":
    if not GITHUB_TOKEN:
        print("Warning: GITHUB_TOKEN not set — rate limit is 60 req/hr unauthenticated.")

    logins_ordered = fetch_commits_ordered()
    print(f"Found {len(logins_ordered)} unique contributor logins.")

    display_names = []
    seen_normalized = set()
    for i, login in enumerate(logins_ordered, 1):
        print(f"  [{i}/{len(logins_ordered)}] Fetching profile for {login}...")
        name = fetch_user_name(login) or login
        # Dedupe: normalize to lowercase alphanum so "John Smith" == "john smith" == "johnsmith"
        normalized = re.sub(r"[\s\W]+", "", name).lower()
        if normalized not in seen_normalized:
            display_names.append(name)
            seen_normalized.add(normalized)
        else:
            print(f"    Skipping duplicate display name: {name!r}")

    if display_names:
        update_xml(display_names)
    else:
        print("No contributors found.")

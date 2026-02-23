import subprocess
import sys
import os
import json
import urllib.request

# Configuration for Sky Map
REPO = "sky-map-team/stardroid"

def get_git_log(base_ref, head_ref="HEAD"):
    try:
        cmd = ["git", "log", f"{base_ref}..{head_ref}", "--oneline"]
        return subprocess.check_output(cmd, stderr=subprocess.STDOUT).decode("utf-8")
    except Exception as e:
        return f"Error fetching git log: {str(e)}"

def get_closed_issues(since_date):
    """Fetches titles of issues closed since a specific date via GitHub API."""
    # Token is optional, but we might get throttled if we don't use one.
    token = os.environ.get("GITHUB_TOKEN")
    url = f"https://api.github.com/repos/{REPO}/issues?state=closed&since={since_date}"

    req = urllib.request.Request(url)
    if token:
        req.add_header("Authorization", f"token {token}")
    req.add_header("Accept", "application/vnd.github.v3+json")

    try:
        with urllib.request.urlopen(req) as response:
            issues = json.loads(response.read().decode())
            # We only want actual issues, not Pull Requests (GitHub API treats PRs as issues)
            titles = [f"#{i['number']}: {i['title']}" for i in issues if 'pull_request' not in i]
            return "\n".join(titles) if titles else "No issues found."
    except Exception as e:
        return f"API Error: {str(e)}"

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 generate_changelog.py [log|issues] [arg]")
        sys.exit(1)

    cmd = sys.argv[1]
    if cmd == "log":
        ref = sys.argv[2] if len(sys.argv) > 2 else "HEAD"
        print(get_git_log(ref))
    elif cmd == "issues":
        date = sys.argv[2] # Expects YYYY-MM-DD
        print(get_closed_issues(date))
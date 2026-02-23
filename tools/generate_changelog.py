import subprocess
import sys
import os
import json
import urllib.request

REPO = "sky-map-team/stardroid"

def first_paragraph(text, max_chars=300):
    """Extract the first non-empty paragraph from text, up to max_chars."""
    if not text:
        return ""
    for para in text.split('\n\n'):
        para = para.strip()
        if para:
            return para[:max_chars]
    return text.strip()[:max_chars]

def get_release_data(base_tag):
    """Gathers date, logs, issues, and merged PRs in a single structured response."""
    try:
        # 1. Get the date of the base tag
        date_cmd = ["git", "log", "-1", "--format=%ai", base_tag]
        tag_date_raw = subprocess.check_output(date_cmd, stderr=subprocess.STDOUT).decode("utf-8")
        tag_date = tag_date_raw.split(" ")[0]

        # 2. Get the git log
        log_cmd = ["git", "log", f"{base_tag}..HEAD", "--oneline"]
        git_log = subprocess.check_output(log_cmd, stderr=subprocess.STDOUT).decode("utf-8")

        # 3. Get closed issues and merged PRs from GitHub.
        # Token is optional but we might get throttled without it.
        token = os.environ.get("GITHUB_TOKEN")
        url = f"https://api.github.com/repos/{REPO}/issues?state=closed&since={tag_date}T00:00:00Z"
        req = urllib.request.Request(url)
        if token: req.add_header("Authorization", f"token {token}")
        req.add_header("User-Agent", "Claude-Skill-Assistant")

        with urllib.request.urlopen(req) as response:
            items = json.loads(response.read().decode())

            issues = [f"#{i['number']}: {i['title']}" for i in items if 'pull_request' not in i]

            # Include merged PRs with their descriptions for richer changelog context.
            # pull_request.merged_at is non-null only for merged (not just closed) PRs.
            merged_prs = [
                {
                    "number": i['number'],
                    "title": i['title'],
                    "body": first_paragraph(i.get('body') or '')
                }
                for i in items
                if 'pull_request' in i and i['pull_request'].get('merged_at')
            ]

        return {
            "base_tag": base_tag,
            "release_date": tag_date,
            "commits": git_log.strip().split('\n'),
            "closed_issues": issues,
            "merged_prs": merged_prs
        }
    except Exception as e:
        return {"error": str(e)}

if __name__ == "__main__":
    if len(sys.argv) > 1:
        # If the skill provides a tag, get everything
        print(json.dumps(get_release_data(sys.argv[1]), indent=2))

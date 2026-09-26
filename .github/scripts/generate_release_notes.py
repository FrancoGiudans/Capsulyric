"""Group release commits and credit their GitHub authors / merged pull requests."""

import argparse
import json
import re
import subprocess
from pathlib import Path


def github_api(endpoint):
    result = subprocess.run(
        ["gh", "api", "--paginate", "--slurp", endpoint],
        check=True, capture_output=True, encoding="utf-8",
    )
    pages = json.loads(result.stdout)
    if pages and isinstance(pages[0], list):
        return [item for page in pages for item in page]
    return pages[0]


def escape(value):
    return re.sub(r"([\\`*_\[\]<>])", r"\\\1", " ".join(value.split()))


def render_notes(commits, repository, api=github_api):
    groups = {name: [] for name in ("New Features", "Fixes", "Improvements", "Chores", "Other")}
    seen_prs = set()

    def credit(user, fallback):
        if user and user.get("login"):
            login = user["login"]
            # Native @mentions let GitHub render the release's contributor avatars.
            # Explicit Markdown profile links do not create mention nodes.
            return f"@{login}"
        return escape(fallback or "Unknown author")

    for sha, subject, author in commits:
        if re.match(r"(?i)^\s*\[release\]", subject):
            continue
        prs = api(f"repos/{repository}/commits/{sha}/pulls?per_page=100")
        prs = [pr for pr in prs if pr.get("merged_at") and
               pr.get("base", {}).get("repo", {}).get("full_name") == repository]
        if prs:
            # Prefer the PR that produced this commit over later backports.
            pr = next((pr for pr in prs if pr.get("merge_commit_sha") == sha), prs[0])
            if pr["number"] in seen_prs:
                continue
            seen_prs.add(pr["number"])
            title = pr["title"]
            if re.match(r"(?i)^\s*\[release\]", title):
                continue
            by = credit(pr.get("user"), author)
            link = f"[#{pr['number']}]({pr['html_url']})"
        else:
            commit = api(f"repos/{repository}/commits/{sha}")
            title = subject
            by = credit(commit.get("author"), author)
            link = f"[{sha[:7]}]({commit['html_url']})"
        match = re.match(r"(?i)^\s*([a-z]+)(?:\([^)]*\))?!?\s*[:：]\s*(.+)$", title)
        prefix = match.group(1).lower() if match else ""
        group = {"feat": "New Features", "fix": "Fixes", "ui": "Improvements",
                 "opt": "Improvements", "ci": "Chores", "build": "Chores",
                 "chore": "Chores", "docs": "Chores"}.get(prefix, "Other")
        groups[group].append(f"- {escape(title)} by {by} in {link}")

    lines = ["## What's Changed", ""]
    for group, items in groups.items():
        if items:
            lines.extend([f"### {group}", "", *items, ""])
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--previous-tag", default="")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    revision = [f"{args.previous_tag}..HEAD"] if args.previous_tag else ["-30", "HEAD"]
    result = subprocess.run(
        ["git", "log", *revision, "--no-merges", "--format=%H%x09%s%x09%an"],
        check=True, capture_output=True, encoding="utf-8",
    )
    commits = [line.split("\t", 2) for line in result.stdout.splitlines() if line]
    Path(args.output).write_text(render_notes(commits, args.repository), encoding="utf-8")


if __name__ == "__main__":
    main()

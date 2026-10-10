#!/usr/bin/env python3
"""Sorts the pull requests of GitHub's generated release notes by kind, from the conventional prefix of their
title: "### Nouveautés" (feat), "### Correctifs" (fix, perf, security) and "### Autres changements" (docs, ci,
chore, refactor, test, dependency updates, titles without a prefix). A release made of several merges then
reads as what it brings, not in merge order.

The bullets stay as GitHub writes them ("* feat: ... by @user in https://github.com/.../pull/N"), under
"## What's Changed": the app reads them there (AppUpdates.changes), and skips the "###" headings. The other
sections (new contributors, full changelog) are kept as they are; notes without a "What's Changed" section
(no pull request merged) are printed unchanged.

Usage: tools/group_release_notes.py < generated-notes.md > notes.md
"""

import re
import sys

GROUPS = (
    ("Nouveautés", {"feat"}),
    ("Correctifs", {"fix", "perf", "security"}),
)
OTHERS = "Autres changements"
PREFIX = re.compile(r"^\*\s+(\w+)(?:\([^)]*\))?!?:")


def kind(bullet):
    match = PREFIX.match(bullet)
    prefix = match.group(1).lower() if match else ""
    return next((title for title, prefixes in GROUPS if prefix in prefixes), OTHERS)


def group(notes):
    lines = notes.splitlines()
    start = next((i for i, line in enumerate(lines) if line.strip().lower() == "## what's changed"), None)
    if start is None:
        return notes
    end = next(
        (i for i in range(start + 1, len(lines))
         if lines[i].startswith("## ") or lines[i].startswith("**Full Changelog**")),
        len(lines),
    )
    section = lines[start + 1:end]
    bullets = [line for line in section if line.startswith("* ")]
    # Anything else GitHub may add there, except headings: ours replace them.
    rest = [line for line in section if line.strip() and not line.startswith("* ") and not line.startswith("#")]

    grouped = [lines[start]]
    for title in [title for title, _ in GROUPS] + [OTHERS]:
        items = [bullet for bullet in bullets if kind(bullet) == title]
        if items:
            grouped += ["", f"### {title}"] + items
    if rest:
        grouped += [""] + rest
    grouped.append("")
    result = lines[:start] + grouped + lines[end:]
    return "\n".join(result) + ("\n" if notes.endswith("\n") else "")


if __name__ == "__main__":
    sys.stdout.write(group(sys.stdin.read()))

#!/usr/bin/env python3
"""Prints the slowest tasks of Gradle --profile reports as Markdown tables.

Usage: tools/gradle_profile_summary.py [REPORT.html ...] [--top N]
Without reports, reads every build/reports/profile/*.html, oldest first. CI appends the output to the
job summary ($GITHUB_STEP_SUMMARY), so task timings can be read without downloading the artifact.
"""
import glob
import html
import os
import re
import sys

PHASES = ("Total Build Time", "Startup", "Settings and buildSrc", "Loading Projects",
          "Configuring Projects", "Artifact Transforms", "Task Execution")


def seconds(duration):
    match = re.fullmatch(r"(?:(\d+)h)?(?:(\d+)m)?([\d.]+)s", duration)
    if not match:
        return 0.0
    hours, minutes, secs = match.groups()
    return int(hours or 0) * 3600 + int(minutes or 0) * 60 + float(secs)


def summarize(path, top):
    content = open(path, encoding="utf-8").read()
    rows = [(html.unescape(name).strip(), duration.strip()) for name, duration in
            re.findall(r"<tr>\s*<td[^>]*>([^<]*)</td>\s*<td[^>]*>([^<]*)</td>", content)]
    build = re.search(r"Profiled build: ([^<]*)", content)
    title = f"gradle {html.unescape(build.group(1)).strip()}" if build else os.path.basename(path)
    lines = [f"### {title}", "",
             "| Phase / task | Duration |", "|---|---:|"]
    lines += [f"| {name} | {duration} |" for name, duration in rows if name in PHASES]
    tasks = {}
    for name, duration in rows:
        # Keep task rows (:app:assembleDebug), not project rows (:, :app) or dependency resolution
        # rows, which are named after configurations (…Classpath).
        if re.fullmatch(r"(:[\w.-]+){2,}", name) and "lasspath" not in name and name not in tasks:
            tasks[name] = duration
    for name, duration in sorted(tasks.items(), key=lambda item: -seconds(item[1]))[:top]:
        lines.append(f"| `{name}` | {duration} |")
    return "\n".join(lines) + "\n"


def main(argv):
    top = 12
    if "--top" in argv:
        index = argv.index("--top")
        top = int(argv[index + 1])
        del argv[index:index + 2]
    reports = argv or sorted(glob.glob("build/reports/profile/*.html"), key=os.path.getmtime)
    if not reports:
        print("No Gradle profile report found.")
        return
    print("\n".join(summarize(path, top) for path in reports))


if __name__ == "__main__":
    main(sys.argv[1:])

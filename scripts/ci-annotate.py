#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Turns CI failures into GitHub annotations (shown on the run summary page).

Reports every failed JUnit test case under */build/test-results and */build/outputs, and
the "What went wrong" sections of a Gradle log given as the first argument.
"""
import glob
import sys
import xml.etree.ElementTree as ElementTree


def escape(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def annotate(title: str, message: str) -> None:
    print(f"::error title={escape(title)}::{escape(message[:4000])}")


def failed_tests() -> int:
    count = 0
    patterns = ["**/build/test-results/**/*.xml", "**/build/outputs/androidTest-results/**/*.xml"]
    for path in sorted({p for pattern in patterns for p in glob.glob(pattern, recursive=True)}):
        try:
            root = ElementTree.parse(path).getroot()
        except ElementTree.ParseError:
            continue
        for case in root.iter("testcase"):
            for problem in list(case.findall("failure")) + list(case.findall("error")):
                details = (problem.get("message") or "") + "\n" + (problem.text or "")
                annotate(f"Test failed: {case.get('classname')}.{case.get('name')}", details.strip()[:2000])
                count += 1
    return count


def gradle_errors(log_path: str) -> int:
    count = 0
    lines = open(log_path, encoding="utf-8", errors="replace").read().splitlines()
    for index, line in enumerate(lines):
        if line.startswith("* What went wrong:"):
            section = []
            for following in lines[index + 1:index + 40]:
                if following.startswith("* Try:"):
                    break
                section.append(following)
            annotate("Gradle", "\n".join(section).strip())
            count += 1
    return count


if __name__ == "__main__":
    total = failed_tests()
    if len(sys.argv) > 1:
        total += gradle_errors(sys.argv[1])
    print(f"{total} failure(s) reported")

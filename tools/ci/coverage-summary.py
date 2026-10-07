#!/usr/bin/env python3
"""Summarizes the JaCoCo reports of the modules: the line coverage of each module and the packages with the most lines
that no test reaches.

Usage: tools/ci/coverage-summary.py [--root DIR] [--top N]

It reads target/site/jacoco/jacoco.csv of every module under DIR and prints Markdown. It never fails on a low value:
the numbers decide where tests are added, they are not a gate.
"""
import argparse
import csv
import glob
import os
from collections import defaultdict


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=".")
    parser.add_argument("--top", type=int, default=25)
    args = parser.parse_args()

    modules = {}
    packages = defaultdict(lambda: [0, 0])
    for path in sorted(glob.glob(os.path.join(args.root, "*", "target", "site", "jacoco", "jacoco.csv"))):
        module = path.split(os.sep)[-5]
        missed = covered = 0
        with open(path, newline="") as handle:
            for row in csv.DictReader(handle):
                m, c = int(row["LINE_MISSED"]), int(row["LINE_COVERED"])
                missed += m
                covered += c
                key = (module, row["PACKAGE"])
                packages[key][0] += m
                packages[key][1] += c
        modules[module] = (missed, covered)

    print("| Module | Lines | Covered | Line coverage |")
    print("|---|---:|---:|---:|")
    total_missed = total_covered = 0
    for module, (missed, covered) in modules.items():
        total = missed + covered
        total_missed += missed
        total_covered += covered
        print("| %s | %d | %d | %.1f %% |" % (module, total, covered, 100.0 * covered / total if total else 0))
    total = total_missed + total_covered
    print("| **all** | %d | %d | %.1f %% |" % (total, total_covered, 100.0 * total_covered / total if total else 0))
    print()
    print("Packages with the most lines that no test reaches:")
    print()
    print("| Module | Package | Lines not covered | Line coverage |")
    print("|---|---|---:|---:|")
    worst = sorted(packages.items(), key=lambda item: -item[1][0])[: args.top]
    for (module, package), (missed, covered) in worst:
        total = missed + covered
        print("| %s | %s | %d | %.1f %% |" % (module, package, missed, 100.0 * covered / total if total else 0))


if __name__ == "__main__":
    main()

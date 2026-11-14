#!/usr/bin/env python3
"""Check the application's actual image tree, including files inside JARs."""

import io
from pathlib import Path, PurePosixPath
import posixpath
import sys
import zipfile


# Split literals keep the forbidden names out of this repository's history.
FORBIDDEN_WORDS = (b"ala" b"tyr", b"bu" b"yan", b"oko" b"litsa", b"oko" b"lica")
ARCHIVE_SUFFIXES = (".jar", ".zip", ".war", ".ear")
REQUIRED_FILES = (
    "l2jfree-platform.jar", "config/server.properties", "config/loginserver.properties",
    "data/scripts.cfg", "catalog/npc_template.csv",
)


def forbidden_path(name):
    path = posixpath.normpath(name.replace("\\", "/")).lower()
    parts = PurePosixPath(path).parts
    if any(word.decode() in path for word in FORBIDDEN_WORDS):
        return True
    if path == "world" or path.startswith("world/"):
        return True
    if parts and (parts[-1].endswith(".go") or parts[-1] in ("go.mod", "go.sum", "go.work")):
        return True
    return any(pair in (("engine", "go"), ("tools", "world"),
                        ("gameserver", "bridge"), ("network", "bridge"))
               for pair in zip(parts, parts[1:])) or path.startswith("engine/java/bridge/") or path == "engine/java/bridge"


def inspect_file(name, data, findings):
    if forbidden_path(name):
        findings.append(f"forbidden path: {name}")
    for word in FORBIDDEN_WORDS:
        if word in data.lower():
            findings.append(f"forbidden content ({word.decode()}): {name}")
    stream = io.BytesIO(data)
    if zipfile.is_zipfile(stream):
        try:
            with zipfile.ZipFile(stream) as archive:
                for entry in archive.infolist():
                    label = f"{name}!/{entry.filename}"
                    if entry.is_dir():
                        if forbidden_path(entry.filename):
                            findings.append(f"forbidden path: {label}")
                    else:
                        nested = []
                        inspect_file(entry.filename, archive.read(entry), nested)
                        findings.extend(f"{name}!/{finding}" for finding in nested)
        except (OSError, RuntimeError, ValueError, zipfile.BadZipFile) as error:
            # A file can carry a zip signature without being an archive we ship, as the
            # Jython standalone jar does for its Windows installer stubs. Those bytes were
            # already scanned. A named archive that cannot be opened is still a failure.
            if Path(name).suffix.lower() in ARCHIVE_SUFFIXES:
                findings.append(f"unreadable archive: {name}: {error}")
    elif Path(name).suffix.lower() in ARCHIVE_SUFFIXES:
        findings.append(f"unreadable archive: {name}")


def check(root):
    findings = []
    for name in REQUIRED_FILES:
        path = root / name
        if not path.is_file() or path.stat().st_size == 0:
            findings.append(f"missing datapack or application file: {name}")
    if not any((root / "data/scripts").rglob("*.class")):
        findings.append("missing compiled datapack scripts")
    for path in sorted(root.rglob("*")):
        name = path.relative_to(root).as_posix()
        if path.is_symlink():
            findings.append(f"unexpected link in application tree: {name}")
        elif path.is_dir():
            if forbidden_path(name):
                findings.append(f"forbidden path: {name}")
        elif path.is_file():
            inspect_file(name, path.read_bytes(), findings)
    return findings


def main():
    if len(sys.argv) != 2 or not Path(sys.argv[1]).is_dir():
        sys.exit("usage: check_distribution.py APPLICATION_DIRECTORY")
    findings = check(Path(sys.argv[1]))
    for finding in findings:
        print(finding, file=sys.stderr)
    if findings:
        return 1
    print("distribution purity passed: datapack present, application files and archive entries checked")
    return 0


if __name__ == "__main__":
    sys.exit(main())

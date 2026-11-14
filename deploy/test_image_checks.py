"""Check command failures and cleanup without Docker or a running server."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import check_distribution as checker
from test_check_distribution import archive


class ImageCheckTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.fixture = self.root / "application"
        for name in checker.REQUIRED_FILES:
            target = self.fixture / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(b"defaults")
        (self.fixture / "l2jfree-platform.jar").write_bytes(archive({"Entry.class": b"bytecode"}))
        (self.fixture / "data/scripts").mkdir()
        (self.fixture / "data/scripts/Quest.class").write_bytes(b"bytecode")
        self.log = self.root / "docker.jsonl"
        docker = self.root / "docker"
        docker.write_text('''#!/usr/bin/env python3
import json, os, shutil, sys
from pathlib import Path
args = sys.argv[1:]
with open(os.environ["MOCK_DOCKER_LOG"], "a") as log:
    log.write(json.dumps(args) + "\\n")
operation = "manifest" if args[0] == "--config" else args[0]
if operation == os.environ.get("MOCK_DOCKER_FAIL"):
    sys.exit(1)
if operation == "create":
    print("fixture-container")
elif operation == "cp":
    shutil.copytree(os.environ["MOCK_APPLICATION"], args[-1], dirs_exist_ok=True)
elif operation == "manifest":
    assert not list(Path(args[1]).iterdir()), "anonymous check received credentials"
elif operation != "rm":
    sys.exit(99)
''')
        docker.chmod(0o755)
        self.env = dict(os.environ, PATH=str(self.root) + os.pathsep + os.environ["PATH"],
                        MOCK_DOCKER_LOG=str(self.log), MOCK_APPLICATION=str(self.fixture),
                        PYTHONDONTWRITEBYTECODE="1")

    def run_script(self, name, fail=""):
        self.log.unlink(missing_ok=True)
        return subprocess.run(["bash", str(Path(__file__).parent / name), "example:ci"],
                              env=dict(self.env, MOCK_DOCKER_FAIL=fail), capture_output=True, text=True)

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()]

    def test_copies_the_image_tree_without_starting_a_server_and_cleans_up(self):
        result = self.run_script("check-image-distribution.sh")
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.calls()
        self.assertEqual(calls[0], ["create", "--entrypoint", "/bin/true", "example:ci"])
        self.assertEqual(calls[-1], ["rm", "fixture-container"])
        self.assertFalse(Path(calls[1][-1]).exists())

    def test_create_copy_and_purity_failures_block_publication(self):
        for failure in ("create", "cp", "purity"):
            with self.subTest(failure=failure):
                if failure == "purity":
                    (self.fixture / "data/note.txt").write_bytes(checker.FORBIDDEN_WORDS[0])
                result = self.run_script("check-image-distribution.sh", failure)
                self.assertNotEqual(result.returncode, 0)
                calls = self.calls()
                if failure != "create":
                    self.assertEqual(calls[-1], ["rm", "fixture-container"])
                    self.assertFalse(Path(calls[1][-1]).exists())

    def test_public_access_uses_empty_credentials_and_propagates_failure(self):
        for failure in ("", "manifest"):
            with self.subTest(failure=failure):
                result = self.run_script("release/check-public-image.sh", failure)
                self.assertEqual(result.returncode, 0 if not failure else 1, result.stderr)
                call = self.calls()[0]
                self.assertEqual(call[0], "--config")
                self.assertEqual(call[2:], ["manifest", "inspect", "example:ci"])
                self.assertFalse(Path(call[1]).exists())


if __name__ == "__main__":
    unittest.main()

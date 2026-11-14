"""Exercise publication checks with plain files and nested, compressed JARs."""

import io
from pathlib import Path
import tempfile
import unittest
import zipfile

import check_distribution as checker


def unreadable_zip():
    payload = bytearray(archive({"A.class": b"ok"}))
    at = payload.find(b"PK\x01\x02")
    payload[at:at + 4] = b"PK\x00\x00"
    return bytes(payload)


def archive(entries):
    data = io.BytesIO()
    with zipfile.ZipFile(data, "w", compression=zipfile.ZIP_DEFLATED) as output:
        for name, content in entries.items():
            output.writestr(name, content)
    return data.getvalue()


class DistributionTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        for name in checker.REQUIRED_FILES:
            self.write(name, b"defaults")
        self.write("l2jfree-platform.jar", archive({"com/example/Server.class": b"bytecode"}))
        self.write("data/scripts/Quest.class", b"bytecode")

    def write(self, name, data):
        target = self.root / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)

    def test_accepts_datapack_and_legacy_world_packages(self):
        self.write("libs/server.jar", archive({"com/example/model/world/World.class": b"bytecode"}))
        self.assertEqual(checker.check(self.root), [])

    def test_requires_datapack_and_compiled_scripts(self):
        for name in ("catalog/npc_template.csv", "data/scripts.cfg", "data/scripts/Quest.class"):
            with self.subTest(name=name):
                saved = (self.root / name).read_bytes()
                (self.root / name).unlink()
                self.assertTrue(checker.check(self.root))
                self.write(name, saved)

    def test_rejects_each_forbidden_word_in_files_and_compressed_classes(self):
        for word in checker.FORBIDDEN_WORDS:
            for name, content in (("data/note.txt", word.upper()),
                                  ("libs/server.jar", archive({"Nested.jar": archive({"Entry.class": word.upper()})}))):
                with self.subTest(word=word, name=name):
                    self.write(name, content)
                    self.assertTrue(checker.check(self.root))
                    (self.root / name).unlink()

    def test_rejects_forbidden_names_at_each_packaging_level(self):
        names = ["CODE.GO", "go.mod", "go.sum", "go.work", "world/data.txt", "engine/go/config",
                 "tools/world/config", "engine/java/bridge/Entry.class", "com/gameserver/bridge/Entry.class",
                 "com/network/bridge/Entry.class"]
        names.extend(word.decode().upper() + ".txt" for word in checker.FORBIDDEN_WORDS)
        for name in names:
            with self.subTest(name=name):
                self.assertTrue(checker.forbidden_path(name))
                self.write("libs/server.jar", archive({name: b"bytecode"}))
                self.assertTrue(checker.check(self.root))

    def test_rejects_broken_archives_and_links(self):
        self.write("libs/broken.jar", b"not a jar")
        self.assertTrue(checker.check(self.root))
        (self.root / "libs/broken.jar").unlink()
        (self.root / "data/link").symlink_to(self.root / "data/scripts.cfg")
        self.assertTrue(checker.check(self.root))

    def test_scans_a_zip_like_binary_without_calling_it_an_archive(self):
        blob = unreadable_zip() + checker.FORBIDDEN_WORDS[0]
        self.assertTrue(zipfile.is_zipfile(io.BytesIO(blob)))
        self.write("libs/server.jar", archive({"Lib/distutils/command/wininst-6.0.exe": blob}))
        findings = checker.check(self.root)
        self.assertTrue(any("forbidden content" in finding for finding in findings))
        self.assertFalse(any("unreadable archive" in finding for finding in findings))

    def test_rejects_a_named_archive_whose_directory_cannot_be_read(self):
        self.write("libs/server.jar", archive({"Inner.jar": unreadable_zip()}))
        self.assertTrue(any("unreadable archive" in finding for finding in checker.check(self.root)))


if __name__ == "__main__":
    unittest.main()

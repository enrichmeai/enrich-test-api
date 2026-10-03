"""Table tests for scripts/release/gate.py: python3 -m unittest scripts/release/test_gate.py"""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import gate  # noqa: E402

POM = """<project xmlns="http://maven.apache.org/POM/4.0.0"><version>{v}</version></project>"""
CHILD = """<project xmlns="http://maven.apache.org/POM/4.0.0"><parent><version>{v}</version></parent></project>"""
SNAPSHOT = "0.3.0-alpha1-SNAPSHOT"
RELEASE = "0.3.0-alpha1"


def central(status=404, versions=()):
    """Maven Central's maven-metadata.xml for test-core: 404 until the first publish."""
    def get(url):
        if status != 200:
            return status, ""
        return 200, "".join(f"<version>{v}</version>" for v in versions)
    return get


class GateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.git("init", "-q")
        self.write(SNAPSHOT, changelog="## [Unreleased]\n- x\n")
        self.before = self.commit()

    def tearDown(self):
        self.tmp.cleanup()

    def git(self, *args):
        return subprocess.run(["git", "-C", str(self.root), *args], check=True,
                              capture_output=True, text=True).stdout.strip()

    def commit(self):
        self.git("add", "-A")
        self.git("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "c")
        return self.git("rev-parse", "HEAD")

    def write(self, v, changelog=None, child=None, module="test-cloud-aws"):
        files = {"pom.xml": POM.format(v=v)}
        for m in gate.MODULES:
            files[f"{m}/pom.xml"] = CHILD.format(v=child if (child and m == module) else v)
        if changelog is not None:
            files["CHANGELOG.md"] = changelog
        for name, text in files.items():
            (self.root / name).parent.mkdir(parents=True, exist_ok=True)
            (self.root / name).write_text(text)

    def release_pr(self, version=RELEASE, **kw):
        kw.setdefault("changelog", f"## [Unreleased]\n\n## [{version}] - 2026-10-03\n- x\n")
        self.write(version, **kw)
        self.commit()

    def decide(self, get, event="push", confirm="", ref="refs/heads/main"):
        return gate.decide(event, self.root, confirm=confirm, before=self.before,
                           ref=ref, get=get, log=lambda *_: None)

    def test_first_release_pr_releases_while_central_has_nothing(self):
        self.release_pr()
        self.assertEqual(self.decide(central(404)), (RELEASE, True))

    def test_later_release_pr_releases(self):
        self.release_pr(version="0.3.0")
        self.assertEqual(self.decide(central(200, [RELEASE])), ("0.3.0", True))

    def test_snapshot_is_not_a_release(self):
        self.write("0.3.0-alpha2-SNAPSHOT")
        self.commit()
        self.assertEqual(self.decide(central()), ("0.3.0-alpha2-SNAPSHOT", False))

    def test_unchanged_version_is_a_noop_even_while_central_awaits_publish(self):
        self.release_pr()
        head = self.git("rev-parse", "HEAD")
        (self.root / "pom.xml").write_text(POM.format(v=RELEASE) + "\n")
        self.commit()
        self.before = head
        # The bundle sits in the Portal's validation stage: not yet in maven-metadata.xml.
        self.assertEqual(self.decide(central(404)), (RELEASE, False))

    def test_already_on_central_skips(self):
        self.release_pr()
        self.assertEqual(self.decide(central(200, [RELEASE])), (RELEASE, False))

    def test_missing_changelog_section_fails(self):
        self.release_pr(changelog="## [Unreleased]\n- x\n")
        with self.assertRaisesRegex(gate.GateError, "CHANGELOG"):
            self.decide(central())

    def test_changelog_heading_is_matched_literally(self):
        self.release_pr(changelog="## [0x3y0-alpha1]\n")
        with self.assertRaisesRegex(gate.GateError, "CHANGELOG"):
            self.decide(central())

    def test_module_left_behind_fails(self):
        for m in gate.MODULES:
            with self.subTest(module=m):
                self.release_pr(child=SNAPSHOT, module=m)
                with self.assertRaisesRegex(gate.GateError, f"{m}/pom.xml={SNAPSHOT}"):
                    self.decide(central())

    def test_central_errors_fail_loudly(self):
        self.release_pr()
        for status in (500, 429, 403, None):
            with self.subTest(status=status):
                with self.assertRaisesRegex(gate.GateError, "Maven Central metadata"):
                    self.decide(central(status))
        with self.assertRaisesRegex(gate.GateError, "Maven Central metadata"):
            self.decide(lambda url: (200, ""))

    def test_dispatch_needs_the_confirm_phrase(self):
        self.release_pr()
        self.assertEqual(self.decide(central(), event="workflow_dispatch", confirm="yes"), (RELEASE, False))
        self.assertEqual(self.decide(central(), event="workflow_dispatch", confirm="publish-maven"),
                         (RELEASE, True))

    def test_dispatch_refuses_a_snapshot(self):
        with self.assertRaisesRegex(gate.GateError, "not a release version"):
            self.decide(central(), event="workflow_dispatch", confirm="publish-maven")

    def test_dispatch_from_another_branch_fails(self):
        with self.assertRaisesRegex(gate.GateError, "main only"):
            self.decide(central(), event="workflow_dispatch", confirm="publish-maven",
                        ref="refs/tags/v0.3.0-alpha1")

    def test_unknown_before_sha_counts_as_changed(self):
        self.release_pr()
        self.before = "0" * 40
        self.assertEqual(self.decide(central()), (RELEASE, True))

    def test_unreadable_version_file_fails_with_a_message(self):
        (self.root / "test-core/pom.xml").write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"/>')
        with self.assertRaisesRegex(gate.GateError, "test-core/pom.xml: no readable parent/version"):
            self.decide(central())

    def test_unreadable_before_version_fails_with_a_message(self):
        (self.root / "pom.xml").write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"/>')
        self.before = self.commit()
        self.release_pr()
        with self.assertRaisesRegex(gate.GateError, "at [0-9a-f]{12}: no readable"):
            self.decide(central())

    def test_release_pattern(self):
        for v in ("0.3.0", "0.3.0-alpha1", "1.0.0-rc.2"):
            self.assertTrue(gate.is_release(v), v)
        for v in ("0.3.0-alpha1-SNAPSHOT", "0.3.0-snapshot", "0.3", "v0.3.0", "0.3.0-"):
            self.assertFalse(gate.is_release(v), v)


class RealRepoTest(unittest.TestCase):
    def test_repo_versions_agree(self):
        found = gate.versions(Path(__file__).resolve().parents[2])
        self.assertEqual(len(set(found.values())), 1, found)


if __name__ == "__main__":
    unittest.main()

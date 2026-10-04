"""Decide whether a push to main (or a manual run) releases enrich-test-api to Maven Central.

Ported from enrichmeai/culvert's scripts/release/gate.py (ADR 0010). Used by
.github/workflows/release.yml. Prints its reasoning and writes `version` and
`release` to $GITHUB_OUTPUT. Exits 1 when a release PR is malformed or Maven
Central cannot be read, so a release never fails silently.

  python3 scripts/release/gate.py --event push --before <sha>
  python3 scripts/release/gate.py --event workflow_dispatch --confirm <phrase> --ref <ref>
"""
import argparse
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
PARENT = "pom.xml"
MODULES = ["test-core", "test-cloud-aws", "test-feature"]
CONFIRM = "publish-maven"
# X.Y.Z or X.Y.Z-qualifier (ADR 0009: the first version is 0.3.0-alpha1). Never a SNAPSHOT:
# central-publishing-maven-plugin sends a SNAPSHOT straight to the snapshot repository.
RELEASE = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$")
METADATA = "https://repo1.maven.org/maven2/com/enrichmeai/test-core/maven-metadata.xml"


class GateError(Exception):
    pass


def _pom_text(text, path, where):
    try:
        return ET.fromstring(text).find(path, POM_NS).text
    except (ET.ParseError, AttributeError) as e:
        raise GateError(f"{where}: no readable {path.replace('m:', '')} ({e})") from e


def pom_version(text, where):
    return _pom_text(text, "m:version", where)


def pom_parent_version(text, where):
    return _pom_text(text, "m:parent/m:version", where)


def is_release(version):
    return bool(RELEASE.match(version)) and "SNAPSHOT" not in version.upper()


def versions(root):
    """Every version a release must set, keyed by file."""
    found = {PARENT: pom_version((root / PARENT).read_text(), PARENT)}
    for m in MODULES:
        name = f"{m}/pom.xml"
        found[name] = pom_parent_version((root / name).read_text(), name)
    return found


def changelog_has(root, version):
    heading = f"## [{version}]"
    return any(line.startswith(heading)
               for line in (root / "CHANGELOG.md").read_text().splitlines())


def http_get(url):
    """(status, body). Retries transient failures."""
    last = None
    for _ in range(3):
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                return r.status, r.read().decode()
        except urllib.error.HTTPError as e:
            if e.code < 500 and e.code != 429:
                return e.code, ""
            last = e.code
        except OSError as e:
            last = e
    return last, ""


def published(version, get=http_get):
    status, body = get(METADATA)
    if status == 404:
        # Nothing has ever been published under test-core: the first release.
        return False
    if status != 200 or not body:
        raise GateError(f"Maven Central metadata answered {status!r}; not deciding blind")
    return f"<version>{version}</version>" in body


def version_before(root, before):
    """The parent POM's version at `before`, or None if it cannot be read."""
    if not before or set(before) == {"0"}:
        return None
    try:
        text = subprocess.run(["git", "-C", str(root), "show", f"{before}:{PARENT}"],
                              check=True, capture_output=True, text=True).stdout
    except subprocess.CalledProcessError:
        return None
    return pom_version(text, f"{PARENT} at {before[:12]}")


def decide(event, root, confirm="", before="", ref="refs/heads/main", get=http_get, log=print):
    """Return (version, release)."""
    if ref != "refs/heads/main":
        raise GateError(f"releases run from main only, not {ref}")
    found = versions(root)
    version = found[PARENT]
    log(f"maven: version {version}")

    if event == "workflow_dispatch":
        if confirm != CONFIRM:
            log(f"confirm phrase is not {CONFIRM!r}: nothing to do")
            return version, False
        if not is_release(version):
            raise GateError(f"{version} on main is not a release version; merge a release PR first")
    else:
        if not is_release(version):
            log(f"not a release version: {version}")
            return version, False
        previous = version_before(root, before)
        if previous == version:
            log(f"{PARENT} changed but its version is still {version}: not a release")
            return version, False
        log(f"version changed: {previous} -> {version}")

    if published(version, get):
        log(f"{version} is already on Maven Central")
        return version, False

    if event != "workflow_dispatch":
        if not changelog_has(root, version):
            raise GateError(f"CHANGELOG.md has no '## [{version}]' section; the release PR must write it")
        odd = {f: v for f, v in found.items() if v != version}
        if odd:
            raise GateError("a release carries one version, but these differ from "
                            f"{version}: " + ", ".join(f"{f}={v}" for f, v in odd.items()))
    return version, True


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--event", required=True)
    ap.add_argument("--confirm", default="")
    ap.add_argument("--before", default="")
    ap.add_argument("--ref", default="refs/heads/main")
    ap.add_argument("--root", default=".")
    a = ap.parse_args(argv)
    try:
        version, release = decide(a.event, Path(a.root), a.confirm, a.before, a.ref)
    except GateError as e:
        print(f"::error::{e}")
        return 1
    out = f"version={version}\nrelease={str(release).lower()}\n"
    print(out, end="")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as f:
            f.write(out)
    return 0


if __name__ == "__main__":
    sys.exit(main())

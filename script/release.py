#!/usr/bin/env python3
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE_PROPERTIES = ROOT / 'gradle.properties'

VERSION_RE = re.compile(r'^(ev)(\d+)\.(\d+)\.(\d+)$')


def read_version() -> str:
    if not GRADLE_PROPERTIES.exists():
        print(f"gradle.properties not found at {GRADLE_PROPERTIES}", file=sys.stderr)
        sys.exit(1)
    for line in GRADLE_PROPERTIES.read_text(encoding='utf-8').splitlines():
        if line.startswith('version='):
            return line.split('=', 1)[1].strip()
    print("version property not found in gradle.properties", file=sys.stderr)
    sys.exit(1)


def write_version(new_version: str):
    lines = GRADLE_PROPERTIES.read_text(encoding='utf-8').splitlines()
    with GRADLE_PROPERTIES.open('w', encoding='utf-8', newline='') as f:
        for line in lines:
            if line.startswith('version='):
                f.write(f'version={new_version}\n')
            else:
                f.write(line + '\n')


def bump(kind: str, current: str) -> str:
    m = VERSION_RE.match(current)
    if not m:
        print(f"Unsupported version format: {current}. Expected ev<MAJOR>.<MINOR>.<PATCH>", file=sys.stderr)
        sys.exit(1)
    prefix, major, minor, patch = m.group(1), int(m.group(2)), int(m.group(3)), int(m.group(4))
    if kind == 'major':
        major += 1; minor = 0; patch = 0
    elif kind == 'minor':
        minor += 1; patch = 0
    elif kind == 'patch':
        patch += 1
    else:
        print(f"Unknown bump kind: {kind}. Use one of: major, minor, patch, or provide an explicit version.", file=sys.stderr)
        sys.exit(1)
    return f"{prefix}{major}.{minor}.{patch}"


def run(cmd: list[str]):
    print(f"$ {' '.join(cmd)}")
    subprocess.check_call(cmd, cwd=ROOT)


def main():
    if len(sys.argv) < 2:
        print("Usage: release.py [major|minor|patch|evX.Y.Z]", file=sys.stderr)
        sys.exit(2)
    arg = sys.argv[1].strip()

    current = read_version()
    if arg.startswith('ev'):
        new_version = arg
        if not VERSION_RE.match(new_version):
            print(f"Explicit version must match ev<MAJOR>.<MINOR>.<PATCH>, got: {new_version}", file=sys.stderr)
            sys.exit(1)
    else:
        new_version = bump(arg, current)

    if new_version == current:
        print(f"Version unchanged ({current}). Nothing to do.", file=sys.stderr)
        sys.exit(0)

    # Update version
    write_version(new_version)

    # Commit and tag
    run(["git", "add", str(GRADLE_PROPERTIES.relative_to(ROOT))])
    run(["git", "commit", "-m", f"chore(release): bump version to {new_version}"])
    # Annotated tag with the exact version string (keep ev prefix)
    run(["git", "tag", "-a", new_version, "-m", f"Release {new_version}"])
    run(["git", "push"])  # push commit
    run(["git", "push", "--tags"])  # push tag

    print(f"Released {new_version}. You can now publish artifacts via Gradle (e.g., ./gradlew publish)")


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""
Generate dependency license reports for all Gradle modules and the GUI (npm),
aggregate them into a single JSON and THIRD-PARTY-NOTICES.md, and verify
licenses against an allowlist.

Usage:
  python script/licenses.py [--skip-gui] [--no-verify]

Outputs:
  - build/licenses/combined.json (Gradle modules)
  - build/licenses/combined-with-gui.json (if GUI collected)
  - THIRD-PARTY-NOTICES.md at repo root
  - Non-zero exit on disallowed licenses unless --no-verify
"""
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD_DIR = ROOT / 'build' / 'licenses'
GRADLEW = 'gradlew.bat' if os.name == 'nt' else './gradlew'


def run(cmd, cwd=None, check=True):
    print(f"$ {' '.join(cmd)}", flush=True)
    return subprocess.run(cmd, cwd=cwd or ROOT, check=check)


def gradle_generate():
    # Ensure Gradle tasks exist and run: generateAllLicenseReports + aggregateLicenses + generateThirdPartyNotices
    cmd = [GRADLEW, '--no-daemon', '--console=plain', 'generateAllLicenseReports', 'aggregateLicenses', 'generateThirdPartyNotices']
    run(cmd)
    combined = BUILD_DIR / 'combined.json'
    if not combined.exists():
        raise SystemExit(f"Combined licenses not found at {combined}. Check Gradle configuration.")
    return combined


def collect_gui_licenses():
    gui_dir = ROOT / 'evento-gui'
    pkg = gui_dir / 'package.json'
    if not pkg.exists():
        print('[INFO] No evento-gui/package.json found, skipping GUI license collection.')
        return {}

    if shutil.which('npx') is None:
        print('[WARN] npx not found in PATH; skipping GUI license collection.')
        return {}

    # Use license-checker; prefer the maintained fork if available via npx fallback
    try_cmds = [
        ['npx', '--yes', 'license-checker@25', '--json', '--production'],
        ['npx', '--yes', 'license-checker', '--json', '--production']
    ]
    output = None
    for cmd in try_cmds:
        try:
            print(f"[INFO] Running: {' '.join(cmd)} (in evento-gui)")
            res = subprocess.run(cmd, cwd=gui_dir, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, check=True)
            output = res.stdout
            break
        except (subprocess.CalledProcessError, FileNotFoundError) as e:
            msg = getattr(e, 'stdout', None) or str(e)
            print(f"[WARN] GUI license command failed: {e}; output follows:\n{msg}\nTrying fallback if available...")

    if not output:
        print('[WARN] Could not collect GUI licenses.')
        return {}

    try:
        data = json.loads(output)
    except json.JSONDecodeError:
        print('[WARN] Unexpected output from license-checker; skipping GUI aggregation.')
        return {}

    deps = []
    for k, v in sorted(data.items()):
        # key format: name@version
        if '@' in k:
            name, version = k.rsplit('@', 1)
        else:
            name, version = k, v.get('version') or ''
        license_name = v.get('licenses') or ''
        # normalize to list of licenses
        if isinstance(license_name, list):
            lic_list = [{'license': str(x)} for x in license_name]
        else:
            lic_list = [{'license': str(license_name)}] if license_name else []
        deps.append({
            'artifact': name,
            'moduleName': name,
            'moduleVersion': version,
            'moduleUrl': v.get('repository') or v.get('repositoryUrl') or '',
            'licenses': lic_list
        })

    return {
        'modules': [{
            'module': ':evento-gui',
            'moduleName': 'evento-gui',
            'dependencies': deps
        }]
    }


ALLOWED_PATTERNS = [
    re.compile(r'.*apache.*2(\.0)?.*', re.I),
    re.compile(r'mit', re.I),
    re.compile(r'.*bsd.*(2|3).*', re.I),
    re.compile(r'.*isc.*', re.I),  # common in npm
    re.compile(r'.*eclipse public license.*2.0.*', re.I),
    re.compile(r'.*mozilla public license.*2.0.*', re.I),
    re.compile(r'unlicense', re.I),
    re.compile(r'cc-?0', re.I),
]


def verify_licenses(modules):
    offenders = []
    for mod in modules:
        for dep in mod.get('dependencies', []):
            licenses = [
                (lic.get('license') or lic.get('licenseName') or '').strip()
                for lic in dep.get('licenses', [])
            ]
            licenses = [l for l in licenses if l]
            ok = False
            if licenses:
                for l in licenses:
                    if any(p.search(l) for p in ALLOWED_PATTERNS):
                        ok = True
                        break
            if not ok:
                offenders.append({
                    'module': mod.get('moduleName'),
                    'artifact': dep.get('moduleName') or dep.get('artifact'),
                    'licenses': ', '.join(licenses) if licenses else 'UNKNOWN'
                })
    return offenders


def write_notices(modules, out_file):
    lines = []
    lines.append('# Third-Party Notices')
    lines.append('')
    lines.append('This file lists third‑party dependencies and their licenses for all modules.')
    lines.append('')
    for mod in modules:
        lines.append(f"## {mod.get('moduleName')} ({mod.get('module')})")
        lines.append('')
        for dep in mod.get('dependencies', []):
            name = dep.get('moduleName') or dep.get('artifact') or 'unknown'
            ver = dep.get('moduleVersion') or ''
            url = dep.get('moduleUrl') or ''
            lic_names = ', '.join([
                (l.get('license') or l.get('licenseName') or '').strip()
                for l in dep.get('licenses', []) if (l.get('license') or l.get('licenseName'))
            ])
            lic_urls = ', '.join(sorted(set([
                (l.get('licenseUrl') or '').strip() for l in dep.get('licenses', []) if l.get('licenseUrl')
            ])))
            bullet = f"- {name}{(':'+ver) if ver else ''} — {lic_names}{(' ('+lic_urls+')') if lic_urls else ''}{(' — '+url) if url else ''}"
            lines.append(bullet)
        lines.append('')

    out_file.write_text('\n'.join(lines), encoding='utf-8')
    print(f"Wrote {out_file}")


def main():
    skip_gui = '--skip-gui' in sys.argv
    no_verify = '--no-verify' in sys.argv

    combined_gradle = gradle_generate()
    with combined_gradle.open('r', encoding='utf-8') as f:
        gradle_data = json.load(f)

    modules = list(gradle_data.get('modules', []))

    if not skip_gui:
        gui = collect_gui_licenses()
        if gui:
            modules.extend(gui['modules'])
            # Save combined-with-gui json
            BUILD_DIR.mkdir(parents=True, exist_ok=True)
            out_json = BUILD_DIR / 'combined-with-gui.json'
            out_json.write_text(json.dumps({'modules': modules}, indent=2), encoding='utf-8')
            print(f"Wrote {out_json}")

    # Write/overwrite THIRD-PARTY-NOTICES.md at root
    write_notices(modules, ROOT / 'THIRD-PARTY-NOTICES.md')

    if not no_verify:
        offenders = verify_licenses(modules)
        if offenders:
            print('\n[ERROR] Found dependencies with disallowed or unknown licenses:')
            for off in offenders:
                print(f" - [{off['module']}] {off['artifact']} -> {off['licenses']}")
            sys.exit(2)
        else:
            print('[OK] All dependency licenses are allowed.')


if __name__ == '__main__':
    main()

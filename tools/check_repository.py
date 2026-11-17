"""Host-free checks for the source distribution and Launcher manifest."""
from pathlib import Path
import ast
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MOD_ID = 'video-preview'
VERSION_RE = r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.]+)?'
REQUIRES = {
    'action-menu-composition-framework': '0.8.0-dev.1',
    'panel-management-framework': '0.2.0-dev.2',
    'preferences-composition-framework': '1.0.0',
}
GENERATED = {'.git', 'build', 'out', 'vendor', 'runtime', 'releases', '__pycache__',
             'results', 'media', 'projects', 'runs', 'native-agent-build', 'native-agent-java',
             'pristine-bin', 'bml-check', 'patchbay-src'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_manifest():
    data = json.loads((ROOT / MOD_ID / 'mod.json').read_text(encoding='utf-8'))
    require(data['manifest_version'] == 2, 'Expected manifest version 2')
    mod = data['mod']
    require(mod['id'] == MOD_ID, 'Stable MOD ID changed')
    require(mod['name'] == 'Native Video Track' and mod['author'] == 'Amo Nova', 'Incorrect display metadata')
    require(re.fullmatch(VERSION_RE, mod['version']), 'Version cannot be discovered by Launcher')
    require(data['built_against'] == {'bitwig_version': '6.1.7', 'platform': 'windows', 'architecture': 'x64'}, 'Unsupported compatibility declaration')
    require({x['id']: x['min_version'] for x in data['requires']} == REQUIRES, 'Framework set differs from tested baseline')
    require(len(data['requires']) == len(REQUIRES), 'Duplicate dependency IDs')
    require(mod['documentation'] == 'readme', 'Missing Launcher documentation reference')
    require(data['resources']['readme'] == {'kind': 'file', 'path': 'README.md', 'media_type': 'text/markdown'}, 'Missing README resource')
    require((ROOT / MOD_ID / 'README.md').is_file(), 'Missing MOD readme')
    require(not any(x in data for x in ('hooks', 'targets', 'helper')), 'Generated hooks do not belong in the source manifest')
    return data


def main():
    manifest = read_manifest()
    runtime = (ROOT / MOD_ID / 'src/local/bitwig/video/PlayerRuntime.java').read_text(encoding='utf-8')
    lock = json.loads((ROOT / 'dependencies.lock.json').read_text(encoding='utf-8'))
    for item in lock['files']:
        if item['root'] == 'mpv_root':
            require('"'+item['path']+'", "'+item['sha256']+'"' in runtime, 'Runtime player pin differs from dependency lock')
    require((ROOT / MOD_ID / 'package/player/README.txt').is_file(), 'First-use download instructions missing')
    license_text = (ROOT / 'LICENSE').read_text(encoding='utf-8')
    require('MIT License' in license_text and 'Copyright (c) 2026 Amo Nova' in license_text, 'Project license mismatch')
    for name in ('Patchbay-LICENSE.txt', 'Patchbay-UPSTREAM-NOTICES.md'):
        require((ROOT / 'licenses' / name).is_file(), 'Missing upstream notice: ' + name)
    # Deliberately scan source inputs, not developer-local dependency/build directories.
    count = 0
    for path in ROOT.rglob('*'):
        rel = path.relative_to(ROOT)
        if not path.is_file() or any(p in GENERATED for p in rel.parts) or rel.parts[:2] == ('integration', 'package') or rel.parts[:3] == ('macos', 'persistent', 'package'):
            continue
        if rel.name == 'build.config.local.json':
            continue
        require(path.suffix.lower() not in ('.jar', '.exe', '.dll', '.zip', '.7z', '.bwproject', '.wav', '.mp4'), 'Binary/private input in source: ' + str(rel))
        if path.suffix == '.py':
            ast.parse(path.read_text(encoding='utf-8-sig'), filename=str(rel), feature_version=(3, 11))
        if path.suffix == '.json':
            json.loads(path.read_text(encoding='utf-8-sig'))
        count += 1
    from check_macos import check_source
    check_source(ROOT / 'macos')
    print(f"REPOSITORY_OK files={count} id={MOD_ID} version={manifest['mod']['version']} license=MIT author=Amo-Nova")


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError) as error:
        sys.exit(str(error))

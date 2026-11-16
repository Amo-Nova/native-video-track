"""Build/validate a local Launcher candidate. Public binary release gates are in RELEASING.md."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import io
import json
import os
import re
import stat
import sys
import zipfile

from check_repository import ROOT, MOD_ID, VERSION_RE, read_manifest, require

ROOT_FILES = {'mod.json', 'helper.jar', 'actions.properties', 'video.svg', 'build.json',
              'README.md', 'THIRD_PARTY.md', 'LICENSE', 'LICENSE_STATUS.md', 'CREDITS.txt',
              'THIRD_PARTY_NOTICES.md', 'RELEASING.md'}


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def allowed(path):
    if path == 'logs/.keep':
        return True
    parts = PurePosixPath(path).parts
    if len(parts) == 1:
        return path in ROOT_FILES
    if parts[0] == 'src':
        return path.endswith('.java')
    if parts[0] == 'licenses':
        return len(parts) == 2 and path.endswith(('.txt', '.md'))
    if parts[0] == 'player':
        return path in ('player/mpv.exe', 'player/mpv.com', 'player/d3dcompiler_43.dll',
                        'player/doc/manual.pdf', 'player/doc/mpbindings.png')
    return False


def validate(archive):
    source = read_manifest()
    version = source['mod']['version']
    require(archive.name == f'{MOD_ID}-{version}.zip', 'Asset filename/version mismatch')
    with zipfile.ZipFile(archive) as z:
        names = set()
        size = 0
        for entry in z.infolist():
            name = entry.filename
            require('\\' not in name and ':' not in name and not name.startswith('/'), 'Unsafe ZIP path')
            require(all(p not in ('', '.', '..') for p in name.split('/')), 'Unsafe ZIP component')
            require(name.lower() not in names, 'Case-insensitive duplicate ZIP entry')
            names.add(name.lower())
            require(not stat.S_ISLNK(entry.external_attr >> 16), 'ZIP symlinks are not supported')
            require(name.startswith(MOD_ID + '/'), 'ZIP must contain only one MOD folder')
            require(allowed(name[len(MOD_ID)+1:]), 'Unapproved package file: ' + name)
            if name == MOD_ID + '/logs/.keep':
                require(entry.file_size <= 2 and not z.read(entry).strip(), 'Only an empty log placeholder may be shipped')
            size += entry.file_size
        require(size <= 2_000_000_000, 'Launcher uncompressed size limit exceeded')
        require(z.testzip() is None, 'ZIP CRC validation failed')
        for filename in ROOT_FILES:
            require(f'{MOD_ID}/{filename}'.lower() in names, 'Missing resource: ' + filename)
        built = json.loads(z.read(MOD_ID + '/mod.json'))
        for field in source:
            require(built.get(field) == source[field], 'Source/package metadata mismatch: ' + field)
        require(len(built.get('hooks', [])) == 10, 'Expected ten generated video hooks')
        require(built.get('targets') and built.get('helper', {}).get('jar') == 'helper.jar', 'Unfinished MOD manifest')
        for resource in built['resources'].values():
            if resource['kind'] == 'file':
                require((MOD_ID + '/' + resource['path']).lower() in names, 'Missing declared resource')
        with zipfile.ZipFile(io.BytesIO(z.read(MOD_ID + '/helper.jar'))) as helper:
            require('local/bitwig/video/VideoHooks.class' in helper.namelist(), 'Video hooks absent from helper JAR')
            require(not any(n.startswith(('com/bitwig/', 'com/bitwigre/')) for n in helper.namelist()), 'Host classes must not be distributed')
        lock = json.loads((ROOT / 'dependencies.lock.json').read_text(encoding='utf-8'))
        for item in lock['files']:
            if item['root'] == 'mpv_root':
                payload = z.read(MOD_ID + '/player/' + item['path'])
                require(hashlib.sha256(payload).hexdigest() == item['sha256'], 'Player differs from pinned build')
        require(z.read(MOD_ID + '/LICENSE') == (ROOT / 'LICENSE').read_bytes(), 'Project license missing or changed')
    return {'asset': archive.name, 'version': version, 'bytes': archive.stat().st_size,
            'sha256': sha(archive), 'files': len(names), 'uncompressedBytes': size,
            'publicBinaryReady': False, 'releaseGates': 'See RELEASING.md; packaging checks do not complete runtime or redistribution gates.'}


def package(output):
    source = read_manifest()
    folder = ROOT / 'out' / MOD_ID
    require((folder / 'helper.jar').is_file(), 'Run python tools/build_source.py mod first')
    output.mkdir(parents=True, exist_ok=True)
    target = output / f"{MOD_ID}-{source['mod']['version']}.zip"
    require(not target.exists(), 'Refusing to replace an existing release candidate; use a fresh --output directory')
    temporary = output / (target.name + '.partial')
    require(not temporary.exists(), 'Existing partial output; review it before retrying')
    try:
        with zipfile.ZipFile(temporary, 'x', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
            for path in sorted(folder.rglob('*')):
                if not path.is_file():
                    continue
                rel = path.relative_to(folder).as_posix()
                require(not path.is_symlink() and allowed(rel), 'Unexpected build output: ' + rel)
                entry = zipfile.ZipInfo(f'{MOD_ID}/{rel}', (2026, 11, 16, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.external_attr = 0o100644 << 16
                with path.open('rb') as src, z.open(entry, 'w') as dest:
                    while chunk := src.read(1024 * 1024):
                        dest.write(chunk)
        os.replace(temporary, target)
        report = validate(target)
    except Exception:
        temporary.unlink(missing_ok=True)
        target.unlink(missing_ok=True)
        raise
    (output / (target.name + '.sha256')).write_text(f"{report['sha256']}  {target.name}\n", encoding='ascii')
    (output / 'candidate.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'releases/launcher')
    parser.add_argument('--check', type=Path, help='Validate an existing candidate without changing it')
    args = parser.parse_args()
    report = validate(args.check) if args.check else package(args.output.resolve())
    print('LAUNCHER_PACKAGE_OK ' + json.dumps(report))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        sys.exit(str(error))

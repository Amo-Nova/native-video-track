"""Host-free integrity checks for the contributed macOS source and MOD package."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import io
import json
import re
import stat
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MAC = ROOT / 'macos'
VERSION = '0.5.0-dev.14'
MOD_ASSET = f'video-preview-{VERSION}-macos.zip'
SOURCE_ASSET = f'NativeVideoTrack-Mac-Source-{VERSION}-macos.zip'
SOURCE_PREFIX = f'NativeVideoTrack-Mac-Source-{VERSION}-macos/'
CATALOG_PATTERN = re.compile(r'(.+?)-(\d+\.\d+\.\d+(?:-[0-9A-Za-z.]+)?)\.zip')
GENERATED = {'build', 'out', 'pristine-bin', 'bml-check', '__pycache__'}


def require(value, message):
    if not value:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def source_files(root=MAC):
    files = {}
    for file in sorted(root.rglob('*')):
        relative = file.relative_to(root)
        if any(part in GENERATED for part in relative.parts) or relative.parts[:2] == ('persistent', 'package'):
            continue
        if file.is_file() and relative.as_posix() != 'SOURCE_MANIFEST.json':
            require(not file.is_symlink(), 'Source symlink: ' + str(relative))
            require(file.suffix.lower() not in ('.jar', '.exe', '.dll', '.dylib', '.zip', '.7z', '.bwproject', '.mp4', '.wav'), 'Private/binary source: ' + str(relative))
            files[relative.as_posix()] = file.read_bytes()
    return files


def refresh_manifest(root=MAC):
    manifest = {'format': 1, 'videoVersion': VERSION, 'bitwigVersion': '6.1.7', 'platform': 'macos',
                'architecture': 'arm64', 'files': [{'path': name, 'sha256': digest(data)} for name, data in source_files(root).items()]}
    (root / 'SOURCE_MANIFEST.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8', newline='\n')


def check_source(root=MAC):
    manifest = json.loads((root / 'video-preview/mod.json').read_text(encoding='utf-8'))
    require(manifest['mod']['id'] == 'video-preview' and manifest['mod']['version'] == VERSION, 'Mac identity/version changed')
    require(manifest['built_against'] == {'bitwig_version': '6.1.7', 'platform': 'macos', 'architecture': 'universal'}, 'Mac host declaration changed')
    provenance = json.loads((root / 'IMPORT_PROVENANCE.json').read_text(encoding='utf-8'))
    for name, expected in provenance['preservedSourceFiles'].items():
        require(digest((root / name).read_bytes()) == expected, 'Imported code/resource changed: ' + name)
    actual = {name: digest(data) for name, data in source_files(root).items()}
    listed = json.loads((root / 'SOURCE_MANIFEST.json').read_text(encoding='utf-8'))['files']
    require(len({row['path'] for row in listed}) == len(listed), 'Duplicate source-manifest entries')
    require(actual == {row['path']: row['sha256'] for row in listed}, 'Mac source manifest is stale; review changes and regenerate it')
    java = list((root / 'video-preview/src').rglob('*.java'))
    require(len(java) == 15, 'Unexpected contributed runtime source count')
    require((root / 'LICENSE').read_text(encoding='utf-8') == (ROOT / 'LICENSE').read_text(encoding='utf-8'), 'Mac MIT license differs')
    for path in (ROOT / 'README.md', ROOT / 'CREDITS.txt', root / 'README.md', root / 'CREDITS.txt', root / 'video-preview/README.md'):
        require('https://github.com/AUPhui' in path.read_text(encoding='utf-8'), 'Missing contributor credit: ' + str(path))
    require(not CATALOG_PATTERN.fullmatch(MOD_ASSET) and not CATALOG_PATTERN.fullmatch(SOURCE_ASSET), 'Mac assets would enter the shared automatic catalog')
    return {'version': VERSION, 'sourceFiles': len(actual)+1, 'runtimeJavaFiles': len(java)}


def archive_files(path, prefix):
    with zipfile.ZipFile(path) as archive:
        names = set(); total = 0; files = {}
        for entry in archive.infolist():
            name = entry.filename
            require(not entry.is_dir(), 'Publication archive uses file entries only')
            require(not name.startswith('/') and '\\' not in name and ':' not in name, 'Unsafe archive path')
            require(all(part not in ('', '.', '..') for part in name.split('/')), 'Unsafe archive component')
            require(not stat.S_ISLNK(entry.external_attr >> 16), 'Archive symlink')
            require(name.lower() not in names, 'Duplicate archive entry')
            names.add(name.lower()); total += entry.file_size
            require(total <= 20_000_000, 'Unexpected large publication archive')
            require(name.startswith(prefix), 'Wrong archive root')
            files[name[len(prefix):]] = archive.read(entry)
        require(archive.testzip() is None, 'Archive CRC failure')
        return files


def check_mod(path):
    check_source()
    require(path.name == MOD_ASSET, 'Keep the supplied Mac asset suffix')
    files = archive_files(path, 'video-preview/')
    provenance = json.loads((MAC / 'IMPORT_PROVENANCE.json').read_text(encoding='utf-8'))
    for name, expected in provenance['preservedModFiles'].items():
        require(name in files and digest(files[name]) == expected, 'Supplied runtime payload changed: ' + name)
    mapping = {'README.md': 'video-preview/README.md', 'THIRD_PARTY.md': 'video-preview/package/THIRD_PARTY.md',
               'player/README.txt': 'video-preview/player/README.txt', 'LICENSE': 'LICENSE', 'CREDITS.txt': 'CREDITS.txt'}
    require(set(files) == set(provenance['preservedModFiles']) | set(mapping) | {'SHA256SUMS.txt'}, 'Unexpected MOD files')
    for name, source in mapping.items():
        require(files[name] == (MAC / source).read_bytes(), 'Stale packaged document: ' + name)
    expected = {name: digest(data) for name, data in files.items() if name != 'SHA256SUMS.txt'}
    rows = [line.split('  ', 1) for line in files['SHA256SUMS.txt'].decode('ascii').splitlines()]
    require(len(rows) == len(expected) and {name: hash_ for hash_, name in rows} == expected, 'Incomplete/incorrect package checksums')
    for file in (MAC / 'video-preview/src').rglob('*.java'):
        require(files[file.relative_to(MAC / 'video-preview').as_posix()] == file.read_bytes(), 'Embedded source differs')
    built = json.loads(files['mod.json']); declared = json.loads((MAC / 'video-preview/mod.json').read_text(encoding='utf-8'))
    require(all(built.get(k) == v for k, v in declared.items()), 'Source/built metadata differs')
    require(len(built['hooks']) == 10 and len(built['opens']) == 2, 'Mac hook/access count differs')
    with zipfile.ZipFile(io.BytesIO(files['helper.jar'])) as helper:
        require(helper.testzip() is None, 'Helper CRC failure')
        classes = [n for n in helper.namelist() if n.endswith('.class')]
        require(classes and all(n.startswith('local/bitwig/video/') for n in classes), 'Foreign classes in Mac helper')
        require(all(struct.unpack('>H', helper.read(n)[6:8])[0] == 65 for n in classes), 'Unexpected Java class target')
    return {'asset': path.name, 'bytes': path.stat().st_size, 'sha256': digest(path.read_bytes()),
            'files': len(files), 'helperSha256': digest(files['helper.jar']), 'runtimePayloadPreserved': True}


def check_source_archive(path):
    require(path.name == SOURCE_ASSET, 'Source asset must stay outside automatic MOD catalog')
    files = archive_files(path, SOURCE_PREFIX)
    expected = source_files(); expected['SOURCE_MANIFEST.json'] = (MAC / 'SOURCE_MANIFEST.json').read_bytes()
    require(files == expected, 'Source ZIP differs from reviewed source tree')
    return {'asset': path.name, 'bytes': path.stat().st_size, 'sha256': digest(path.read_bytes()), 'files': len(files)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--refresh-source-manifest', action='store_true')
    parser.add_argument('--mod-zip', type=Path)
    parser.add_argument('--source-zip', type=Path)
    args = parser.parse_args()
    if args.refresh_source_manifest:
        refresh_manifest()
    print('MAC_SOURCE_OK', json.dumps(check_source()))
    if args.mod_zip:
        print('MAC_PACKAGE_OK', json.dumps(check_mod(args.mod_zip)))
    if args.source_zip:
        print('MAC_SOURCE_ARCHIVE_OK', json.dumps(check_source_archive(args.source_zip)))

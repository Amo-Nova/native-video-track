"""Package the contributed Mac runtime unchanged, adding reviewed docs/license/credits."""
from pathlib import Path
import argparse
import json
import zipfile
from check_macos import (MAC, MOD_ASSET, SOURCE_ASSET, SOURCE_PREFIX, archive_files, check_mod,
                         check_source, check_source_archive, digest, source_files)


def write_zip(path, prefix, files):
    with zipfile.ZipFile(path, 'x', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(prefix+name, (2026, 11, 17, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input-zip', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    check_source()
    provenance = json.loads((MAC / 'IMPORT_PROVENANCE.json').read_text(encoding='utf-8'))
    if digest(args.input_zip.read_bytes()) != provenance['inputArchives']['mod']['sha256']:
        raise ValueError('Expected the exact reviewed contributor MOD archive')
    # Input archive paths were checked during import; no extracted executable is run here.
    files = archive_files(args.input_zip, 'video-preview/')
    for name, source in {'README.md': 'video-preview/README.md', 'THIRD_PARTY.md': 'video-preview/package/THIRD_PARTY.md',
                         'player/README.txt': 'video-preview/player/README.txt', 'LICENSE': 'LICENSE', 'CREDITS.txt': 'CREDITS.txt'}.items():
        files[name] = (MAC / source).read_bytes()
    files.pop('SHA256SUMS.txt')
    files['SHA256SUMS.txt'] = ''.join(f'{digest(data)}  {name}\n' for name, data in sorted(files.items())).encode('ascii')
    output = args.output.resolve()
    if output.exists():
        raise ValueError('Use a new output directory; publication candidates are never overwritten')
    output.mkdir(parents=True)
    write_zip(output / MOD_ASSET, 'video-preview/', files)
    source = source_files(); source['SOURCE_MANIFEST.json'] = (MAC / 'SOURCE_MANIFEST.json').read_bytes()
    write_zip(output / SOURCE_ASSET, SOURCE_PREFIX, source)
    reports = [check_mod(output / MOD_ASSET), check_source_archive(output / SOURCE_ASSET)]
    (output / 'SHA256SUMS-macos-0.5.0-dev.14.txt').write_text(''.join(f"{row['sha256']}  {row['asset']}\n" for row in reports), encoding='ascii', newline='\n')
    (output / 'candidate.json').write_text(json.dumps(reports, indent=2)+'\n', encoding='utf-8', newline='\n')
    print('MAC_PUBLICATION_CANDIDATE_OK', json.dumps(reports))


if __name__ == '__main__':
    main()

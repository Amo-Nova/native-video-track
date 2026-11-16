"""Compile the actual Launcher 0.9.2 sources and exercise the candidate offline."""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import subprocess
import zipfile
from build_source import ROOT, FRAMEWORKS, config
from package_launcher import validate


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--launcher-source', type=Path, required=True, help='Official patchbay-launcher v0.9.2 checkout')
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--config', type=Path, default=ROOT / 'build.config.local.json')
    args = parser.parse_args()
    validate(args.candidate)
    paths = config(args.config.resolve())
    upstream = args.launcher_source.resolve()
    if 'VERSION = "0.9.2"' not in (upstream / 'src/PatchbayLauncher.java').read_text(encoding='utf-8'):
        raise RuntimeError('This acceptance harness is pinned to official Launcher 0.9.2')
    build = ROOT / 'build' / ('launcher-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    classes = build / 'classes'; classes.mkdir(parents=True)
    sources = sorted((upstream / 'src').glob('*.java'))
    provenance = {f.name: hashlib.sha256(f.read_bytes()).hexdigest() for f in sources}
    (build / 'upstream-source-sha256.json').write_text(json.dumps(provenance, indent=2)+'\n', encoding='utf-8')
    subprocess.run([str(paths['java_home'] / 'bin/javac.exe'), '--release', '21', '-encoding', 'UTF-8', '-d', str(classes),
                    *map(str, sources), str(ROOT / 'tests/LauncherPackageTest.java')], check=True)
    deps = []
    for name in FRAMEWORKS:
        folder = paths['patchbay_root'] / 'mods' / name
        archive = build / (name + '.zip')
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
            for path in sorted(folder.rglob('*')):
                if path.is_file():
                    z.write(path, name + '/' + path.relative_to(folder).as_posix())
        deps.append(archive)
    result = subprocess.run([str(paths['java_home'] / 'bin/java.exe'), '-Dfile.encoding=UTF-8', '-Djava.awt.headless=true',
        '-cp', str(classes), 'LauncherPackageTest', str(args.candidate.resolve()), str(build / 'acceptance'), *map(str, deps)],
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
    (build / 'acceptance.log').write_text(result.stdout, encoding='utf-8')
    print(result.stdout)
    result.check_returncode()
    print('LAUNCHER_ACCEPTANCE_RECORD', build)


if __name__ == '__main__':
    main()

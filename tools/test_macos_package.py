"""Check the contributed Mac ZIP using official Launcher code without running macOS."""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import os
import subprocess
from check_macos import ROOT, check_mod
from build_source import config


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--launcher-source', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--catalog-json', type=Path, help='Optional actual GitHub releases response for post-publication verification')
    parser.add_argument('--config', type=Path, default=ROOT / 'build.config.local.json')
    args = parser.parse_args()
    check_mod(args.candidate)
    upstream = args.launcher_source.resolve()
    label = (upstream / 'src/PatchbayLauncher.java').read_text(encoding='utf-8')
    if not any(f'VERSION = "{version}"' in label for version in ('0.9.2', '0.9.4')):
        raise ValueError('Use the checked official Launcher 0.9.2 or 0.9.4 sources')
    sources = sorted((upstream / 'src').glob('*.java'))
    build = ROOT / 'build' / ('mac-package-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    classes = build / 'classes'; classes.mkdir(parents=True)
    (build / 'upstream-source-sha256.json').write_text(json.dumps({p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}, indent=2)+'\n', encoding='utf-8')
    jdk = config(args.config.resolve())['java_home'] / 'bin'
    env = os.environ.copy(); env.pop('JAVA_TOOL_OPTIONS', None)
    subprocess.run([str(jdk / 'javac.exe'), '--release', '21', '-encoding', 'UTF-8', '-d', str(classes),
                    *map(str, sources), str(ROOT / 'tests/MacPackageTest.java')], check=True, env=env)
    command = [str(jdk / 'java.exe'), '-Dfile.encoding=UTF-8', '-cp', str(classes), 'MacPackageTest',
               str(args.candidate.resolve()), str(build / 'acceptance')]
    if args.catalog_json:
        command.append(str(args.catalog_json.resolve()))
    result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8', errors='replace', env=env)
    (build / 'acceptance.log').write_text(result.stdout+result.stderr, encoding='utf-8')
    print(result.stdout+result.stderr)
    result.check_returncode()
    print('MAC_PACKAGE_ACCEPTANCE_RECORD', build)


if __name__ == '__main__':
    main()

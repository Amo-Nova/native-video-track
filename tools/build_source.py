"""Portable, build-only entry point. No installation or running-host automation."""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import os
import platform
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
STOCK = 'a5b303466cf66a80e0fd7df00e49fb14eb41c23e59d30b300c0554b6769b3367'
FRAMEWORKS = ('action-menu-composition-framework', 'panel-management-framework', 'preferences-composition-framework')
STATE = ROOT / 'build/source-build-state.json'

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def config(path):
    if not path.is_file():
        raise RuntimeError('Copy build.config.example.json to build.config.local.json and set your dependency paths. See BUILDING.md.')
    raw = json.loads(path.read_text(encoding='utf-8-sig'))
    if raw.get('format') != 1:
        raise RuntimeError('Unsupported build configuration format.')
    result = {}
    for key in ('java_home', 'bitwig_bin', 'patchbay_root', 'mpv_root'):
        value = raw.get(key)
        if not isinstance(value, str) or not value.strip():
            raise RuntimeError('Missing configuration: ' + key)
        item = Path(os.path.expandvars(value)).expanduser()
        result[key] = (path.parent / item).resolve() if not item.is_absolute() else item.resolve()
    return result

def check(paths):
    if os.name != 'nt' or platform.machine().lower() not in ('amd64', 'x86_64'):
        raise RuntimeError('This source snapshot currently builds the Windows x64 backend only. See MAC_COMPATIBILITY.md.')
    if sys.version_info < (3, 11):
        raise RuntimeError('Python 3.11 or newer is required.')
    required = [paths['java_home'] / 'bin' / name for name in ('java.exe', 'javac.exe', 'jar.exe')]
    required += [paths['bitwig_bin'] / name for name in ('bitwig.jar', 'libs.jar')]
    required += [paths['bitwig_bin'].parent / 'jre/bin/java.exe']
    required += [paths['patchbay_root'] / name for name in ('modkit/bml-modkit.jar', 'modkit/bml-api.jar', 'loader/app/bml-control.jar', 'LICENSE')]
    required += [paths['patchbay_root'] / 'mods' / name / 'helper.jar' for name in FRAMEWORKS]
    required += [paths['mpv_root'] / name for name in ('mpv.exe', 'mpv.com', 'd3dcompiler_43.dll')]
    for item in required:
        if not item.is_file():
            raise RuntimeError('Missing build dependency: ' + str(item))
    if not (paths['mpv_root'] / 'doc').is_dir():
        raise RuntimeError('The mpv documentation folder must be preserved (it is not a complete redistribution source/license bundle).')
    lock = ROOT / 'dependencies.lock.json'
    if lock.exists():
        for item in json.loads(lock.read_text(encoding='utf-8'))['files']:
            file = paths[item['root']] / item['path']
            if not file.is_file() or sha(file) != item['sha256']:
                raise RuntimeError('Pinned dependency differs: ' + str(file) + '. Use the versions listed in DEPENDENCIES.md.')
    actual = sha(paths['bitwig_bin'] / 'bitwig.jar')
    if actual != STOCK:
        raise RuntimeError('Expected an UNMODIFIED Windows Bitwig 6.1.7 SDK jar; found ' + actual + '. Do not point this at the already-integrated installation. See BUILDING.md.')
    maps = paths['patchbay_root'] / 'maps/windows-6.1.7-map1/map.json'
    if not maps.is_file() or json.loads(maps.read_text(encoding='utf-8-sig'))['target_jar_sha256'] != STOCK:
        raise RuntimeError('Matching Patchbay Windows 6.1.7 map is missing.')
    if not (Path(os.environ['WINDIR']) / 'Microsoft.NET/Framework64/v4.0.30319/csc.exe').is_file():
        raise RuntimeError('Windows .NET Framework C# compiler is required for the EXE stage.')
    print('BUILD_DEPENDENCIES_OK Windows x64 / original Bitwig 6.1.7', flush=True)

def load(relative, name):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

def record(**values):
    current = json.loads(STATE.read_text(encoding='utf-8')) if STATE.exists() else {}
    current.update({key: str(value) for key, value in values.items()})
    STATE.parent.mkdir(parents=True, exist_ok=True)
    STATE.write_text(json.dumps(current, indent=2) + '\n', encoding='utf-8')

def state():
    if not STATE.is_file():
        raise RuntimeError('No source build state. Run the mod/all stage first.')
    return json.loads(STATE.read_text(encoding='utf-8'))

def mod(paths):
    builder = load('build_video_mod.py', 'nvt_mod_builder')
    builder.JDK = paths['java_home'] / 'bin'
    builder.BW = paths['bitwig_bin']
    builder.BML = paths['patchbay_root']
    builder.MPV = paths['mpv_root']
    builder.VENDOR.mkdir(parents=True, exist_ok=True)
    # Validate the cache belongs to this exact host; clean source exports contain no cache.
    cache = builder.VENDOR / 'bitwig617-compile.jar'
    stamp = cache.with_suffix('.source-sha256')
    if cache.exists() and (not stamp.exists() or stamp.read_text().strip() != STOCK):
        raise RuntimeError('Unverified local compile cache. Move vendor/bitwig617-compile.jar aside and rebuild.')
    old_args = sys.argv
    try:
        sys.argv = [str(ROOT / 'build_video_mod.py'), '--build-only']
        builder.main()
    finally:
        sys.argv = old_args
    stamp.write_text(STOCK + '\n', encoding='ascii')
    record(build=builder.BUILD)

def prepare(paths, mod_zip=None):
    build = Path(state()['build'])
    candidate = build / 'prepared-source'
    if candidate.exists():
        raise RuntimeError('Candidate already exists. Run mod again to create a fresh isolated build.')
    for name in ('loader', 'maps'):
        shutil.copytree(paths['patchbay_root'] / name, candidate / name)
    for name in FRAMEWORKS:
        shutil.copytree(paths['patchbay_root'] / 'mods' / name, candidate / 'mods' / name)
    if mod_zip is None:
        shutil.copytree(ROOT / 'out/video-preview', candidate / 'mods/video-preview')
    else:
        import zipfile
        from package_launcher import validate
        validate(mod_zip)
        with zipfile.ZipFile(mod_zip) as archive:
            archive.extractall(candidate / 'mods')  # Entire entry list was checked before extraction.
    (candidate / 'config').mkdir()
    (candidate / 'config/portable').write_text('Isolated source build only.\n', encoding='utf-8')
    args = [str(paths['java_home'] / 'bin/java.exe'), '-Dfile.encoding=UTF-8', '-Duser.language=en',
            '-cp', str(candidate / 'loader/app/bml-control.jar'), 'dev.bitwigre.bml.JavaPreparer',
            '--loader-root', str(candidate / 'loader'), '--bitwig-bin', str(paths['bitwig_bin']),
            '--mods-root', str(candidate / 'mods')]
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
    (build / 'prepare-source.log').write_text(result.stdout, encoding='utf-8')
    print(result.stdout, flush=True)
    result.check_returncode()
    if 'PREPARE_OK' not in result.stdout:
        raise RuntimeError('No PREPARE_OK evidence in preparation output.')
    record(candidate=candidate)

def package(paths):
    candidate = Path(state()['candidate'])
    builder = load('integration/build_package.py', 'nvt_integration_builder')
    builder.RUNTIME = candidate
    builder.JDK = paths['java_home'] / 'bin'
    builder.STOCK = paths['bitwig_bin'] / 'bitwig.jar'
    builder.BML_ROOT = paths['patchbay_root']
    builder.main()

def exe(paths):
    builder = load('integration/exe/build_exe.py', 'nvt_exe_builder')
    builder.main()

def verify(paths):
    build = Path(state()['build'])
    cp = os.pathsep.join((str(build / 'test-classes'), str(paths['bitwig_bin'] / 'libs.jar')))
    jdk = paths['java_home'] / 'bin'
    subprocess.run([str(jdk / 'javac.exe'), '--release', '21', '-encoding', 'UTF-8', '-cp', cp,
                    '-d', str(build / 'test-classes'), str(ROOT / 'tests/TransportTimeTest.java')], check=True)
    subprocess.run([str(jdk / 'java.exe'), '-cp', cp, 'local.bitwig.video.TransportTimeTest'], check=True)
    installer = load('integration/exe/build_exe.py', 'nvt_exe_verifier')
    output = installer.RELEASE / f'Native-Video-Track-Setup-{installer.VERSION}-Windows-x64.exe'
    report = build / 'exe-payload-verification.json'
    subprocess.run([str(output), '--headless', '--action', 'Verify', '--data-root', str(build / 'test-data'), '--report', str(report)], check=True)
    if not json.loads(report.read_text(encoding='utf-8'))['ok']:
        raise RuntimeError('Built EXE payload verification failed.')
    print('SOURCE_BUILD_VERIFY_OK transport rules and EXE payload; no host launch or installation.', flush=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=('check', 'mod', 'prepare', 'package', 'exe', 'verify', 'all'))
    parser.add_argument('--config', type=Path, default=ROOT / 'build.config.local.json')
    parser.add_argument('--mod-zip', type=Path, help='For prepare: use the validated public Launcher ZIP instead of local bundled output')
    args = parser.parse_args()
    if args.mod_zip is not None and args.stage != 'prepare':
        parser.error('--mod-zip is only supported by the prepare stage')
    paths = config(args.config.resolve())
    check(paths)
    stages = ('mod', 'prepare', 'package', 'exe', 'verify') if args.stage == 'all' else (args.stage,)
    for name in stages:
        if name != 'check':
            if name == 'prepare':
                prepare(paths,args.mod_zip)
            else:
                globals()[name](paths)

if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    try:
        main()
    except (RuntimeError, FileNotFoundError, subprocess.CalledProcessError) as error:
        print('BUILD_FAILED: ' + str(error), file=sys.stderr)
        raise SystemExit(1)

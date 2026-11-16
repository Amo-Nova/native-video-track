"""Build the single-file Windows installer from the verified local integration package."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
INTEGRATION = ROOT.parent
PROJECT = INTEGRATION.parent
VERSION = '1.0.0-dev.3'
BUILD = ROOT / 'build'
RELEASE = PROJECT / 'releases' / ('setup-' + VERSION)

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def main():
    BUILD.mkdir(parents=True, exist_ok=True)
    RELEASE.mkdir(parents=True, exist_ok=True)
    package = INTEGRATION / 'package'
    # Refresh only installer scripts. The already-verified media/overlay bytes stay intact.
    for name in ['Install-BitwigVideo.ps1', 'ProjectAssociation.ps1', 'Run-Installer.ps1',
                 'Install.cmd', 'Restore.cmd', 'Status.cmd', 'README.txt']:
        shutil.copy2(INTEGRATION / name, package / name)
    manifest = json.loads((package / 'manifest.json').read_text(encoding='utf-8-sig'))
    assert manifest['integrationVersion'] == '1.0.0-dev.1'
    assert manifest['videoVersion'] == json.loads((PROJECT / 'video-preview/mod.json').read_text(encoding='utf-8'))['mod']['version']
    assert manifest['overlaySha256'] == sha(package / 'jar-overlay.zip')
    assert manifest['componentSha256'] == sha(package / 'component.zip')
    files = {p.name: p for p in package.iterdir() if p.is_file()}
    assert set(files) == {'Install-BitwigVideo.ps1', 'ProjectAssociation.ps1', 'Run-Installer.ps1',
                         'Install.cmd', 'Restore.cmd', 'Status.cmd', 'README.txt',
                         'manifest.json', 'jar-overlay.zip', 'component.zip'}, sorted(files)
    files['Complete-UserAssociation.ps1'] = ROOT / 'Complete-UserAssociation.ps1'
    files['CREDITS.txt'] = ROOT / 'CREDITS.txt'
    payload = BUILD / 'payload.zip'
    with zipfile.ZipFile(payload, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=4) as archive:
        for name, path in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (2026, 11, 16, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, path.read_bytes())
    (BUILD / 'BuildInfo.cs').write_text(
        'namespace NativeVideoTrack { internal static class BuildInfo {\n'
        f' public const string Version="{VERSION}";\n'
        f' public const string PayloadSha256="{sha(payload)}";\n'
        '} }\n', encoding='utf-8')
    compiler = Path(os.environ['WINDIR']) / 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
    assert compiler.is_file(), compiler
    target = RELEASE / f'Native-Video-Track-Setup-{VERSION}-Windows-x64.exe'
    args = [str(compiler), '/nologo', '/target:winexe', '/platform:x64', '/optimize+',
            '/codepage:65001', '/out:' + str(target), '/win32manifest:' + str(ROOT / 'app.manifest'),
            '/reference:System.dll,System.Core.dll,System.Drawing.dll,System.Windows.Forms.dll,'
            'System.IO.Compression.dll,System.IO.Compression.FileSystem.dll,System.Web.Extensions.dll',
            '/resource:' + str(payload) + ',NativeVideoTrack.Payload.zip',
            '/resource:' + str(ROOT / 'CREDITS.txt') + ',NativeVideoTrack.Credits.txt',
            str(ROOT / 'Setup.cs'), str(BUILD / 'BuildInfo.cs')]
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    text = result.stdout.decode('utf-8', errors='replace')
    (BUILD / 'compile.log').write_text(text, encoding='utf-8')
    if result.returncode:
        print(text)
        raise SystemExit(result.returncode)
    record = {'installerVersion': VERSION, 'integrationVersion': manifest['integrationVersion'],
              'videoVersion': manifest['videoVersion'], 'platform': 'Windows x64', 'bitwig': '6.1.7',
              'exe': target.name, 'bytes': target.stat().st_size, 'sha256': sha(target),
              'payloadSha256': sha(payload), 'overlaySha256': manifest['overlaySha256'],
              'componentSha256': manifest['componentSha256'], 'signed': False,
              'payloadFiles': {name: sha(path) for name, path in sorted(files.items())}}
    (RELEASE / 'release.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    (RELEASE / (target.name + '.sha256')).write_text(f'{sha(target)}  {target.name}\n', encoding='ascii')
    shutil.copy2(ROOT / 'CREDITS.txt', RELEASE / 'CREDITS.txt')
    shutil.copy2(ROOT / 'README.txt', RELEASE / 'README.txt')
    print(json.dumps({'exe': str(target), 'bytes': target.stat().st_size, 'sha256': sha(target)}, indent=2))

if __name__ == '__main__':
    main()

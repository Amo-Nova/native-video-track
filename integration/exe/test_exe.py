"""Exercise the delivered EXE against an isolated host; no production app changes."""
from pathlib import Path
import datetime
import hashlib
import json
import os
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parents[1]
LIVE = Path('C:/Program Files/Bitwig Studio/bin/bitwig.jar')
STOCK_SHA = 'a5b303466cf66a80e0fd7df00e49fb14eb41c23e59d30b300c0554b6769b3367'
STOCK = LIVE.parent / ('bitwig-video/backups/' + STOCK_SHA + '.jar')
EXE = PROJECT / 'releases/setup-1.0.0-dev.2/Native-Video-Track-Setup-1.0.0-dev.2-Windows-x64.exe'
WORK = ROOT / 'tests' / datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
HOST = WORK / "Bitwig 'quoted' $host"
DATA = WORK / '用户 设置'
evidence = []

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def run(action, ok=True, exe=EXE, host=HOST):
    report = WORK / f'{len(evidence):02}-{action}.json'
    command = [str(exe), '--headless', '--action', action, '--root', str(host),
               '--data-root', str(DATA), '--report', str(report), '--skip-association']
    result = subprocess.run(command, timeout=100)
    receipt = json.loads(report.read_text(encoding='utf-8'))
    assert receipt['ok'] == ok and (result.returncode == 0) == ok, receipt
    assert receipt['action'] == action and receipt['root'] == str(host), receipt
    evidence.append({'action': action, 'ok': ok, 'receipt': report.name})
    print(action, 'expected-success' if ok else 'expected-refusal', flush=True)
    return receipt

def passed(name):
    evidence.append({'passed': name})
    (WORK / 'results.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS', name, flush=True)

def main():
    global EXE
    WORK.mkdir(parents=True)
    live_before = sha(LIVE)
    assert sha(STOCK) == STOCK_SHA
    (HOST / 'bin').mkdir(parents=True)
    shutil.copy2(STOCK, HOST / 'bin/bitwig.jar')
    (HOST / 'Bitwig Studio.exe').write_bytes(b'isolated installer fixture; never launched')
    relocated = WORK / "中文 Installer's $path" / EXE.name
    relocated.parent.mkdir()
    shutil.copy2(EXE, relocated)
    EXE = relocated
    jar = HOST / 'bin/bitwig.jar'
    component = HOST / 'bin/bitwig-video'
    # The local default on run() remains the release EXE; explicitly use relocation once.
    run('Verify', exe=relocated)
    run('Status', exe=relocated)
    passed('single EXE runs from a different Unicode directory without sibling package files')

    source = WORK / 'ClosedGuard.cs'
    source.write_text('class ClosedGuard { static void Main() { System.Threading.Thread.Sleep(90000); } }', encoding='utf-8')
    marker = HOST / 'BitwigStudioApp.exe'
    compiler = Path(os.environ['WINDIR']) / 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
    subprocess.run([str(compiler), '/nologo', '/target:winexe', '/out:' + str(marker), str(source)], check=True)
    process = subprocess.Popen([str(marker)])
    try:
        time.sleep(.25)
        receipt = run('Install', False, exe=relocated)
        assert 'close Bitwig normally' in receipt['error']
        assert process.poll() is None and sha(jar) == STOCK_SHA
    finally:
        process.terminate()
        process.wait(timeout=5)
    passed('running host refused without stopping the process or changing the jar')

    run('Install', exe=relocated)
    installed = sha(jar)
    assert installed != STOCK_SHA and sha(component / ('backups/' + STOCK_SHA + '.jar')) == STOCK_SHA
    status = run('Status')['status']
    assert status['status'] == 'Installed' and status['healthy'] and status['backupVerified']
    passed('fresh install, verified original backup, installed health')
    run('Install')
    assert sha(jar) == installed
    passed('repeat install preserves installed jar')
    release = component / 'releases/1.0.0-dev.1'
    icon = release / 'mods/video-preview/video.svg'
    old_icon = sha(icon)
    icon.unlink()
    assert not run('Status')['status']['healthy']
    run('Install')
    assert sha(icon) == old_icon and sha(jar) == installed
    passed('missing component repaired without replacing the active jar')
    state = component / 'installation.json'
    state.rename(component / 'pending.json')
    assert run('Status')['status']['pending']
    run('Install')
    assert state.exists() and not (component / 'pending.json').exists()
    passed('interrupted installation resumes through the EXE')

    DATA.mkdir(parents=True, exist_ok=True)
    sentinel = DATA / 'user-settings-preserved.txt'
    sentinel.write_text('keep user settings', encoding='utf-8')
    run('Restore')
    assert sha(jar) == STOCK_SHA and not release.exists() and sentinel.exists()
    run('Restore')
    passed('restore and repeat restore preserve user data and recover the exact original jar')

    jar.write_bytes(jar.read_bytes() + b'unknown-version-fixture')
    unknown = sha(jar)
    run('Install', False)
    run('Restore', False)
    assert sha(jar) == unknown
    passed('unknown or updated host rejected by both install and restore')
    shutil.copy2(STOCK, jar)
    run('Status', False, host=WORK / 'missing-host')
    passed('missing host yields a readable failure receipt')

    payload = (ROOT / 'build/payload.zip').read_bytes()
    damaged = bytearray(relocated.read_bytes())
    offset = damaged.find(payload[:96])
    assert offset >= 0
    damaged[offset + 200] ^= 1
    corrupt = WORK / 'corrupt-payload.exe'
    corrupt.write_bytes(damaged)
    receipt = run('Install', False, exe=corrupt)
    assert 'checksum failed' in receipt['error'] and sha(jar) == STOCK_SHA
    passed('corrupted embedded payload rejected before host mutation')
    assert sha(LIVE) == live_before
    passed('production Bitwig jar untouched')
    (ROOT / 'tests/last-run.txt').write_text(str(WORK), encoding='utf-8')
    print('RESULTS', WORK, flush=True)

if __name__ == '__main__':
    main()

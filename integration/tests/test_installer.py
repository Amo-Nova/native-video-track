"""Real installer acceptance on an owned, isolated application copy; never modifies live Bitwig."""
from pathlib import Path
import datetime, hashlib, json, os, shutil, subprocess, sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
ROOT=Path(__file__).resolve().parents[1]
PACKAGE=ROOT/'package'
STAMP=datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
WORK=ROOT/'tests/runs'/STAMP
HOST=WORK/'Bitwig Test Host'
STOCK='a5b303466cf66a80e0fd7df00e49fb14eb41c23e59d30b300c0554b6769b3367'
SOURCE=Path('C:/Program Files/Bitwig Studio')
JDK=Path('C:/Program Files/Microsoft/jdk-25.0.2.10-hotspot/bin')
evidence=[]
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def run(action,success=True, package=PACKAGE):
    p=subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(package/'Install-BitwigVideo.ps1'),'-Action',action,'-BitwigRoot',str(HOST)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    text=p.stdout.decode('utf-8',errors='replace')
    (WORK/f'{len(evidence):02}-{action}.log').write_text(text,encoding='utf-8')
    evidence.append({'action':action,'exit':p.returncode,'output':text})
    print(action, p.returncode, text[-500:],flush=True)
    assert (p.returncode==0)==success,text
    return text
def record(case):
    print('PASS',case,flush=True)
    evidence.append({'passed':case})
    (WORK/'results.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
def main():
    WORK.mkdir(parents=True)
    assert sha(SOURCE/'bin/bitwig.jar')==STOCK
    print('Copying isolated application',HOST,flush=True)
    shutil.copytree(SOURCE,HOST)
    jar=HOST/'bin/bitwig.jar'; owned=HOST/'bin/bitwig-video'
    run('Install'); installed=sha(jar);assert installed!=STOCK
    assert sha(owned/f'backups/{STOCK}.jar')==STOCK
    record('install and original backup')
    env=dict(os.environ)
    for key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS']:env.pop(key,None)
    env['BITWIG_VIDEO_DATA']=str(WORK/'用户设置')
    env['BML_MOD_CONFIG']='Z:/not-a-real-launcher/bindings.properties'
    p=subprocess.run([str(JDK/'java.exe'),'-Xverify:all','-Dfile.encoding=UTF-8','-Dsun.stdout.encoding=UTF-8','-Dstdout.encoding=UTF-8','-cp',str(jar)+';'+str(HOST/'bin/libs.jar'),'local.bitwig.video.install.InstalledRuntime'],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    text=p.stdout.decode('utf-8',errors='replace');(WORK/'runtime-diagnosis.log').write_text(text,encoding='utf-8')
    print(text,flush=True);assert p.returncode==0
    bindings=list((WORK/'用户设置/sessions').rglob('bindings.properties'))
    assert len(bindings)==1
    raw=bindings[0].read_text(encoding='ascii')
    assert 'runtime/Patchbay-Stage2' not in raw and '${' not in raw and 'not-a-real-launcher' not in raw
    record('runtime without launcher; spaces, Unicode paths and reflection bridges')
    run('Install');assert sha(jar)==installed
    record('idempotent install')
    release=owned/'releases/1.0.0-dev.1'
    icon=release/'mods/video-preview/video.svg';held=WORK/'held-video.svg'
    icon.rename(held)
    run('Status');run('Install');assert sha(icon)==sha(held) and sha(jar)==installed
    record('repair missing resource without replacing jar')
    # Model a power loss after File.Replace but before installation.json publication.
    state=owned/'installation.json';pending=owned/'pending.json'
    state.rename(pending)
    run('Status');run('Install');assert state.exists() and not pending.exists() and sha(jar)==installed
    record('resume interrupted publication')
    # Recovery must work with no package zip files available.
    run('Restore',package=owned/'Recovery');assert sha(jar)==STOCK and not release.exists()
    record('standalone recovery and complete component removal')
    run('Restore',package=owned/'Recovery');assert sha(jar)==STOCK
    record('repeat restore is harmless')
    # Model an official update/third-party edit. Neither install nor restore may overwrite it.
    jar.write_bytes(jar.read_bytes()+b'fixture-unknown-update')
    unknown=sha(jar)
    run('Install',False);run('Restore',False);assert sha(jar)==unknown
    record('refuse unknown upgraded jar')
    shutil.copy2(owned/f'backups/{STOCK}.jar',jar)
    run('Install');assert sha(jar)!=STOCK
    # Restore must refuse a damaged backup even while the active integration is recognized.
    backup=owned/f'backups/{STOCK}.jar';safe=WORK/'held-original.jar'
    backup.rename(safe);backup.write_bytes(b'damaged backup fixture')
    before=sha(jar);run('Restore',False);assert sha(jar)==before
    backup.unlink();safe.rename(backup)
    record('refuse damaged original backup')
    run('Status')
    assert sha(SOURCE/'bin/bitwig.jar')==STOCK
    (ROOT/'tests/last-isolated-host.txt').write_text(str(HOST),encoding='utf-8')
    record('live installation untouched; isolated host ready for ordinary launch')

if __name__=='__main__':main()

"""Build and prepare an isolated Patchbay install. Never modifies installed Bitwig."""
from pathlib import Path
import datetime, hashlib, json, re, shutil, subprocess, sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = Path(__file__).resolve().parent
VENDOR = ROOT / 'vendor'
MPV = VENDOR / 'mpv'
BML = VENDOR / 'Patchbay-0.36.0-dev.14/BML-0.36.0-dev.14-windows'
JDK = Path('C:/Program Files/Microsoft/jdk-25.0.2.10-hotspot/bin')
BW = Path('C:/Program Files/Bitwig Studio/bin')
SRC = ROOT / 'video-preview'
OUT = ROOT / 'out/video-preview'
RUN = ROOT / 'runtime/Patchbay-Stage2'
BUILD = ROOT / 'build' / datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
EXPECTED = 'a5b303466cf66a80e0fd7df00e49fb14eb41c23e59d30b300c0554b6769b3367'

def run(*args):
    argv=[str(a) for a in args]
    vmargs=['-Dfile.encoding=UTF-8','-Dsun.stdout.encoding=UTF-8','-Dsun.stderr.encoding=UTF-8','-Duser.language=en']
    if Path(args[0]).name=='java.exe':argv[1:1]=vmargs
    elif Path(args[0]).name in ['javac.exe','jar.exe']:argv[1:1]=['-J'+a for a in vmargs]
    p = subprocess.run(argv, text=True, encoding='utf-8', errors='replace', stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    print(p.stdout, end='', flush=True)
    with (BUILD / 'build.log').open('a', encoding='utf-8') as f: f.write(p.stdout)
    if p.returncode: raise RuntimeError(f'{Path(args[0]).name} failed ({p.returncode})')
    if 'An exception has occurred in the compiler' in p.stdout or 'AccessDeniedException' in p.stdout:
        raise RuntimeError('Compiler reported an internal/access error despite exit status; retry in normal build permissions.')

def main():
    if '--build-only' not in sys.argv:
        running=subprocess.check_output(['powershell','-NoProfile','-Command',"[bool](Get-Process BitwigStudioApp -ErrorAction SilentlyContinue)"],text=True).strip()
        if running.lower()=='true':
            raise RuntimeError('Close Bitwig before deployment, or use --build-only. The running MOD was not changed.')
    actual = hashlib.file_digest((BW/'bitwig.jar').open('rb'), 'sha256').hexdigest()
    if actual != EXPECTED: raise RuntimeError(f'Bitwig 6.1.7 exact hash required, found {actual}')
    BUILD.mkdir(parents=True); OUT.mkdir(parents=True, exist_ok=True)
    compile_jar = VENDOR / 'bitwig617-compile.jar'
    kit = BML / 'modkit/bml-modkit.jar'
    framework = BML / 'mods/action-menu-composition-framework/helper.jar'
    panels = BML / 'mods/panel-management-framework/helper.jar'
    if not compile_jar.exists(): run(JDK/'java.exe','-jar',kit,'compile-jar',BW/'bitwig.jar',compile_jar)
    classes=BUILD/'classes';classes.mkdir()
    cp=';'.join(map(str,[compile_jar,BW/'libs.jar',BML/'modkit/bml-api.jar',framework,panels]))
    run(JDK/'javac.exe','--release','21','-proc:none','-encoding','UTF-8','-implicit:none','-sourcepath',SRC/'src','-cp',cp,'-d',classes,*sorted((SRC/'src').rglob('*.java')))
    # Keep pre-finish classes for integration tests (MpvConnection has no native Bitwig imports).
    shutil.copytree(classes,BUILD/'test-classes')
    run(JDK/'java.exe','-jar',kit,'finish',classes,BUILD/'hooks.json',BW/'bitwig.jar',BW/'libs.jar',framework,panels)
    run(JDK/'jar.exe','--create','--file',OUT/'helper.jar','-C',classes,'.')
    generated=json.loads((BUILD/'hooks.json').read_text(encoding='utf-8'))
    manifest=json.loads((SRC/'mod.json').read_text(encoding='utf-8'));manifest['targets']={};manifest['hooks']=[]
    def target_key(item):
        for key,value in manifest['targets'].items():
            if value==item:return key
        stem=item.get('class') if item['kind']=='class' else item['owner']+'.'+item['name']
        stem=re.sub(r'[^A-Za-z0-9_.-]','',stem.replace('/','.').replace('$','_'))
        key=stem;count=2
        while key in manifest['targets']:
            key=stem+'-'+str(count);count+=1
        manifest['targets'][key]=item
        return key
    manifest['opens']=[target_key(item) for item in generated.get('access',[])]
    if generated.get('hooks'):
        manifest['helper']={'jar':'helper.jar','class':'local.bitwig.video.VideoHooks'}
        for hook in generated['hooks']:
            hook.pop('helper_class',None)
            hook['target']=target_key(hook['target'])
            if 'match' in hook:
                match=hook['match'];match['target']=target_key(match['target'])
                for key in ['owner','name','descriptor']:match.pop(key,None)
            manifest['hooks'].append(hook)
    (OUT/'mod.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    shutil.copytree(SRC/'package',OUT,dirs_exist_ok=True)
    shutil.copytree(SRC/'src',OUT/'src',dirs_exist_ok=True)
    # Obsolete bottom-tab registration was replaced by the native workspace wrapper.
    for obsolete in ['panels.properties','src/local/bitwig/video/VideoPanels.java']:
        (OUT/obsolete).unlink(missing_ok=True)
    shutil.copy2(SRC/'build.json',OUT/'build.json')
    if (SRC/'README.md').exists():shutil.copy2(SRC/'README.md',OUT/'README.md')
    (OUT/'player').mkdir(exist_ok=True)
    for filename in ['mpv.exe','mpv.com','d3dcompiler_43.dll']:shutil.copy2(MPV/filename,OUT/'player'/filename)
    shutil.copytree(MPV/'doc',OUT/'player/doc',dirs_exist_ok=True)
    for name in ['LICENSE', 'LICENSE_STATUS.md', 'CREDITS.txt', 'THIRD_PARTY_NOTICES.md', 'RELEASING.md']:
        shutil.copy2(ROOT/name,OUT/name)
    shutil.copytree(ROOT/'licenses',OUT/'licenses',dirs_exist_ok=True)
    (ROOT/'last-build.txt').write_text(str(BUILD),encoding='utf-8')
    if '--build-only' in sys.argv:
        print('VIDEO_MOD_BUILT',OUT,flush=True)
        return
    if not (RUN/'loader/app/bml-control.jar').exists():
        shutil.copytree(BML/'loader',RUN/'loader',dirs_exist_ok=True)
        shutil.copytree(BML/'maps',RUN/'maps',dirs_exist_ok=True)
        shutil.copy2(BML/'launch-bitwig.cmd',RUN/'launch-bitwig.cmd')
        (RUN/'config').mkdir(exist_ok=True)
        (RUN/'config/portable').write_text('Isolated local video preview test install\n',encoding='utf-8')
    shutil.copytree(BML/'mods/action-menu-composition-framework',RUN/'mods/action-menu-composition-framework',dirs_exist_ok=True)
    for dependency in ['panel-management-framework','preferences-composition-framework']:
        shutil.copytree(BML/'mods'/dependency,RUN/'mods'/dependency,dirs_exist_ok=True)
    shutil.copytree(OUT,RUN/'mods/video-preview',dirs_exist_ok=True)
    for obsolete in ['panels.properties','src/local/bitwig/video/VideoPanels.java']:
        (RUN/'mods/video-preview'/obsolete).unlink(missing_ok=True)
    (ROOT/'last-build.txt').write_text(str(BUILD),encoding='utf-8')
    if '--prepare' in sys.argv:
        run(JDK/'java.exe','-cp',RUN/'loader/app/bml-control.jar','dev.bitwigre.bml.JavaPreparer','--loader-root',RUN/'loader','--bitwig-bin',BW,'--mods-root',RUN/'mods')
    print('VIDEO_MOD_BUILT',OUT,flush=True)

if __name__=='__main__': main()

"""Build a local, version-bound installation delta. Never edits the installed application."""
from pathlib import Path
import hashlib, json, shutil, struct, subprocess, zipfile

ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parent
RUNTIME = PROJECT / 'runtime/Patchbay-Stage2'
BML_ROOT = PROJECT / 'vendor/Patchbay-0.36.0-dev.14/BML-0.36.0-dev.14-windows'
JDK = Path('C:/Program Files/Microsoft/jdk-25.0.2.10-hotspot/bin')
STOCK = PROJECT / 'vendor/bitwig-6.1.7-inspection/bin/bitwig.jar'
EXPECTED = 'a5b303466cf66a80e0fd7df00e49fb14eb41c23e59d30b300c0554b6769b3367'
VERSION = '1.0.0-dev.1'
VIDEO_VERSION = json.loads((PROJECT / 'video-preview/mod.json').read_text(encoding='utf-8'))['mod']['version']
OUT = ROOT / 'package'

def sha(data): return hashlib.sha256(data).hexdigest()
def file_sha(path):
    with path.open('rb') as f: return hashlib.file_digest(f, 'sha256').hexdigest()

def redirect_getenv(data):
    """Change only System.getenv(String)'s Methodref owner; no code or stack-map rewriting."""
    if data[:4] != b'\xca\xfe\xba\xbe': raise ValueError('Not a class')
    count = struct.unpack_from('>H', data, 8)[0]
    at = 10; i = 1; cp = {}; positions = {}
    while i < count:
        tag = data[at]; positions[i] = at; at += 1
        if tag == 1:
            n = struct.unpack_from('>H', data, at)[0]; at += 2
            cp[i] = (tag, data[at:at+n].decode('utf-8', errors='replace')); at += n
        elif tag in (7, 8, 16, 19, 20): cp[i] = (tag, struct.unpack_from('>H', data, at)[0]); at += 2
        elif tag in (9, 10, 11, 12, 17, 18): cp[i] = (tag, *struct.unpack_from('>HH', data, at)); at += 4
        elif tag in (3, 4): at += 4
        elif tag in (5, 6): at += 8; i += 1
        elif tag == 15: at += 3
        else: raise ValueError(f'Unknown constant pool tag {tag}')
        i += 1
    refs=[]
    for index, item in cp.items():
        if item[0] == 10 and cp[cp[item[1]][1]][1] == 'java/lang/System':
            nt = cp[item[2]]
            if cp[nt[1]][1] == 'getenv' and cp[nt[2]][1] == '(Ljava/lang/String;)Ljava/lang/String;': refs.append(index)
    if not refs: return data, 0
    owner=b'local/bitwig/video/install/InstalledRuntime'
    prefix=bytearray(data[:at]);struct.pack_into('>H', prefix, 8, count+2)
    for index in refs: struct.pack_into('>H', prefix, positions[index]+1, count+1)
    suffix=b'\x01'+struct.pack('>H',len(owner))+owner+b'\x07'+struct.pack('>H',count)
    return bytes(prefix)+suffix+data[at:],len(refs)

def main():
    assert file_sha(STOCK) == EXPECTED
    OUT.mkdir(parents=True, exist_ok=True)
    classes=ROOT/'build/classes';classes.mkdir(parents=True,exist_ok=True)
    subprocess.run([str(JDK/'javac.exe'),'--release','21','-encoding','UTF-8','-d',str(classes),str(ROOT/'src/local/bitwig/video/install/InstalledRuntime.java')],check=True)
    overlay={}; replaced=[]; redirected={}
    with zipfile.ZipFile(STOCK) as original:
        names=set(original.namelist())
        assert not any(n.upper().endswith(('.SF','.RSA','.DSA','.EC')) and n.upper().startswith('META-INF/') for n in names)
        for line in (RUNTIME/'loader/mod-runtime/replacement-map.tsv').read_text().splitlines():
            name,path=line.split('\t'); name=name.replace('.','/')+'.class'
            assert name in names and name not in overlay
            overlay[name]=Path(path).read_bytes()
            replaced.append({'path':name,'originalSha256':sha(original.read(name))})
        assert len(replaced)==49
        with zipfile.ZipFile(RUNTIME/'loader/mod-runtime/helper.jar') as helper:
            for item in helper.infolist():
                if item.is_dir() or item.filename.upper()=='META-INF/MANIFEST.MF':continue
                assert item.filename not in names and item.filename not in overlay,item.filename
                data=helper.read(item)
                if item.filename.endswith('.class'):
                    data,count=redirect_getenv(data)
                    if count:redirected[item.filename]=count
                overlay[item.filename]=data
    assert len(redirected)==5, redirected # Bml, Bml$Mod and three generated bridges.
    for f in classes.rglob('*.class'):overlay[f.relative_to(classes).as_posix()]=f.read_bytes()
    bindings=(RUNTIME/'loader/mod-runtime/mod-bindings.properties').read_text(encoding='utf-8')
    base=RUNTIME.as_posix()
    bindings=bindings.replace(base+'/config','${user.data}').replace(base+'/mods','${component.root}/mods').replace(base+'/components','${user.data}/components')
    bindings=bindings.replace(RUNTIME.as_uri()+'/config','${user.uri}').replace(RUNTIME.as_uri()+'/mods','${component.uri}/mods').replace(RUNTIME.as_uri()+'/components','${user.uri}/components')
    assert base not in bindings and 'C:/Users/' not in bindings
    overlay['bitwig-video-install/bindings.properties']=bindings.encode('ascii')
    overlay['bitwig-video-install/version.txt']=(VERSION+'\n').encode()
    overlay_items=[]
    original_hashes={x['path']:x['originalSha256'] for x in replaced}
    with zipfile.ZipFile(OUT/'jar-overlay.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for name,data in sorted(overlay.items()):
            z.writestr(name,data)
            overlay_items.append({'path':name,'sha256':sha(data),'originalSha256':original_hashes.get(name)})
    component_files={}
    for f in (RUNTIME/'mods').rglob('*'):
        if not f.is_file():continue
        # Only shipped resources, no source copies or development traces.
        relative=f.relative_to(RUNTIME)
        if 'src' in relative.parts:continue
        component_files[relative.as_posix()]=f.read_bytes()
    vendor=BML_ROOT
    for name in ['LICENSE','THIRD_PARTY_NOTICES.md']:
        component_files['licenses/Patchbay-'+name]=(vendor/name).read_bytes()
    component_files['component.json']=json.dumps({'integrationVersion':VERSION,'videoVersion':VIDEO_VERSION,'bitwigVersion':'6.1.7'},indent=2).encode()
    component_items=[]
    with zipfile.ZipFile(OUT/'component.zip','w',zipfile.ZIP_DEFLATED,compresslevel=3) as z:
        for name,data in sorted(component_files.items()):
            z.writestr(name,data);component_items.append({'path':name,'sha256':sha(data)})
    manifest={'format':1,'integrationVersion':VERSION,'videoVersion':VIDEO_VERSION,'bitwigVersion':'6.1.7','originalSha256':EXPECTED,
              'overlaySha256':file_sha(OUT/'jar-overlay.zip'),'componentSha256':file_sha(OUT/'component.zip'),
              'overlay':overlay_items,'components':component_items,'redirectedClasses':redirected}
    (OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    for name in ['Install-BitwigVideo.ps1','ProjectAssociation.ps1','Run-Installer.ps1','Install.cmd','Restore.cmd','Status.cmd','README.txt']:
        shutil.copy2(ROOT/name,OUT/name)
    print(json.dumps({'package':str(OUT),'replacements':len(replaced),'overlayEntries':len(overlay),'redirected':redirected,'componentFiles':len(component_items)},indent=2))

if __name__=='__main__':main()

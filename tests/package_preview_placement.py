"""Archive the already verified MOD, without altering a running binary or previous releases."""
from pathlib import Path
import difflib, hashlib, json, shutil
ROOT=Path(__file__).resolve().parents[1]
version='0.5.0-dev.1'
source=ROOT/'video-preview'
out=ROOT/'out/video-preview'
runtime=ROOT/'runtime/Patchbay-Stage2/mods/video-preview'
release=ROOT/'releases'/version
previous=ROOT/'releases/0.4.0-dev.1/video-preview'
def digest(path):return hashlib.file_digest(path.open('rb'),'sha256').hexdigest()
for target in (out,runtime):
    expected={p.relative_to(source/'src') for p in (source/'src').rglob('*.java')}
    assert expected=={p.relative_to(target/'src') for p in (target/'src').rglob('*.java')}
    assert all((source/'src'/p).read_bytes()==(target/'src'/p).read_bytes() for p in expected)
    shutil.copy2(source/'README.md',target/'README.md')
assert digest(out/'helper.jar')==digest(runtime/'helper.jar')
assert json.loads((runtime/'mod.json').read_text())['mod']['version']==version
release.mkdir(exist_ok=True)
shutil.copytree(out,release/'video-preview',dirs_exist_ok=True)
for name in ('DESIGN_PREVIEW_PLACEMENT.md','VALIDATION_PREVIEW_PLACEMENT.md'):
    shutil.copy2(ROOT/name,release/name)
extensions={'.java','.md','.json','.properties','.svg'}
old={p.relative_to(previous):p for p in previous.rglob('*') if p.is_file() and p.suffix in extensions}
new={p.relative_to(out):p for p in out.rglob('*') if p.is_file() and p.suffix in extensions}
patch=[]
for relative in sorted(old.keys()|new.keys()):
    a=old[relative].read_text(encoding='utf-8').splitlines(True) if relative in old else []
    b=new[relative].read_text(encoding='utf-8').splitlines(True) if relative in new else []
    patch.extend(difflib.unified_diff(a,b,fromfile='0.4.0-dev.1/'+relative.as_posix(),tofile=version+'/'+relative.as_posix()))
(release/'source-changes.patch').write_text(''.join(patch),encoding='utf-8')
hashes={str(folder.relative_to(ROOT)/'helper.jar'):digest(folder/'helper.jar') for folder in (out,runtime,release/'video-preview')}
assert len(set(hashes.values()))==1
metadata={'version':version,'date':'2026-11-16','bitwig':'6.1.7 Windows x64','helper_hashes':hashes,'validation':'VALIDATION_PREVIEW_PLACEMENT.md','previous_release':'../0.4.0-dev.1','entry_point':'../../Start-Video-Preview-Stage2.cmd','package_scope':'MOD backup with source and mpv; use existing isolated Patchbay launcher.'}
(release/'release.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
results=ROOT/'tests/results'
validation={'placement':json.loads((results/'placement-050-20261116-055558/summary.json').read_text(encoding='utf-8')),'codec_editing':json.loads((results/'placement-codec-regression.json').read_text(encoding='utf-8')),'reopen':json.loads((results/'placement-reopen-verification.json').read_text(encoding='utf-8')),'visual_limitations':['No new desktop screenshot capture due prior automatic privacy rejection','Multi-monitor and mixed DPI not exercised on physical displays']}
(release/'validation.json').write_text(json.dumps(validation,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
shutil.copy2(ROOT/'source_hashes_after_stage2.json',ROOT/'source_hashes_after_placement.json')
print('RELEASE_READY',release)
print('HELPER_SHA256',next(iter(hashes.values())))

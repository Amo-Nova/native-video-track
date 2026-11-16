"""Additional interruption and settings checks on the previously owned acceptance copy."""
from pathlib import Path
import hashlib, json, shutil, subprocess

ROOT=Path(__file__).resolve().parents[1]
HOST=Path((ROOT/'tests/last-isolated-host.txt').read_text(encoding='utf-8')).resolve()
assert HOST.is_relative_to((ROOT/'tests/runs').resolve())
work=HOST.parent
owned=HOST/'bin/bitwig-video';jar=HOST/'bin/bitwig.jar'
state=owned/'installation.json';pending=owned/'pending.json'
record=json.loads(state.read_text(encoding='utf-8'))
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def install(*extra):
    p=subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'package/Install-BitwigVideo.ps1'),'-BitwigRoot',str(HOST),*map(str,extra)],capture_output=True)
    assert p.returncode==0,(p.stdout+p.stderr).decode(errors='replace')
    return p.stdout.decode(errors='replace').strip()
assert sha(jar)==record['installedSha256']
shutil.copy2(jar,work/'pre-interruption-integration.jar')
shutil.copy2(owned/f'backups/{record["originalSha256"]}.jar',jar)
state.rename(pending)
first=install()
assert state.exists() and not pending.exists() and sha(jar)!=record['originalSha256']
source=work/'settings-migration-source';dest=work/'settings-migration-dest'
(source/'video-preview').mkdir(parents=True,exist_ok=True)
(source/'video-preview/settings.properties').write_text('preview.position=right\n',encoding='utf-8')
second=install('-SettingsSource',source,'-DataRoot',dest)
setting=dest/'mods/video-preview/settings.properties'
assert setting.read_text(encoding='utf-8')=='preview.position=right\n'
setting.write_text('preview.position=floating\n',encoding='utf-8')
third=install('-SettingsSource',source,'-DataRoot',dest)
assert setting.read_text(encoding='utf-8')=='preview.position=floating\n'
(work/'recovery-edges.json').write_text(json.dumps({'result':'PASS','checks':['interruption before jar publication','migrate old layout settings','preserve already migrated user preferences'],'outputs':[first,second,third]},indent=2),encoding='utf-8')
print('PASS interruption before publication; settings migration; existing preferences preserved')

"""Exercise existing Windows launch entries; does not change shortcuts or associations."""
from pathlib import Path
import argparse, json, os, subprocess, sys
sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('entry',choices=['start-menu','taskbar','project']);p.add_argument('--project');a=p.parse_args()
count=subprocess.check_output(['powershell.exe','-NoProfile','-Command','@(Get-Process BitwigStudioApp -ErrorAction SilentlyContinue).Count'],text=True).strip()
if count!='0':raise RuntimeError('Ordinary cold-start acceptance requires the previous test host to exit normally')
paths={'start-menu':Path(os.environ['PROGRAMDATA'])/'Microsoft/Windows/Start Menu/Programs/Bitwig Studio.lnk',
       'taskbar':Path(os.environ['APPDATA'])/'Microsoft/Internet Explorer/Quick Launch/User Pinned/TaskBar/Bitwig Studio.lnk'}
if a.entry=='project':
    path=Path(a.project).resolve()
    if not path.is_relative_to(ROOT/'tests') or path.suffix!='.bwproject':raise RuntimeError('Acceptance projects only')
else:path=paths[a.entry]
if not path.is_file():raise FileNotFoundError(path)
for name in list(os.environ):
    if name.startswith(('BML_','BITWIG_RE_')) or name in ('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','BITWIG_VIDEO_DATA'):os.environ.pop(name)
os.startfile(str(path))
record={'entry':a.entry,'path':str(path),'method':'Windows ShellExecute existing entry'}
(ROOT/'integration/tests'/('launch-'+a.entry+'.json')).write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(record,ensure_ascii=False),flush=True)

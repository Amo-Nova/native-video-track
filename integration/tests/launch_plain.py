"""Launch only a closed host through its original executable, without Patchbay environment."""
from pathlib import Path
import argparse, json, os, subprocess

ROOT=Path(__file__).resolve().parents[1]
import sys
sys.stdout.reconfigure(encoding='utf-8')
p=argparse.ArgumentParser();p.add_argument('--installed',action='store_true');p.add_argument('--project');a=p.parse_args()
processes=subprocess.check_output(['powershell.exe','-NoProfile','-Command','@(Get-Process BitwigStudioApp -ErrorAction SilentlyContinue).Count'],text=True).strip()
if processes!='0':raise RuntimeError('Close Bitwig normally first; no process was changed')
host=Path('C:/Program Files/Bitwig Studio') if a.installed else Path((ROOT/'tests/last-isolated-host.txt').read_text(encoding='utf-8'))
env=dict(os.environ)
cleared=[]
for key in list(env):
    if key.startswith(('BML_','BITWIG_RE_')) or key in ('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','BITWIG_VIDEO_DATA'):
        env.pop(key);cleared.append(key)
if not a.installed:env['BITWIG_VIDEO_DATA']=str(host.parent/'plain-user-data')
args=[str(host/'Bitwig Studio.exe')]
if a.project:
    project=Path(a.project).resolve()
    allowed=(ROOT.parent/'tests').resolve()
    if not project.is_relative_to(allowed) or project.suffix!='.bwproject':raise RuntimeError('Acceptance project only')
    args.append(str(project))
process=subprocess.Popen(args,cwd=host,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
print(json.dumps({'launcherPid':process.pid,'executable':args[0],'project':a.project,'clearedVariableNames':cleared,'isolated':not a.installed},ensure_ascii=False),flush=True)
code=process.wait()
print('ORDINARY_LAUNCHER_EXIT',code,flush=True)

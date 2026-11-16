"""Run the test-only driver against the single isolated MOD host, with an auditable command file."""
from pathlib import Path
import argparse, subprocess, json, time, re, sys
sys.stdout.reconfigure(encoding='utf-8')

ROOT=Path(__file__).resolve().parent
JDK=Path('C:/Program Files/Microsoft/jdk-25.0.2.10-hotspot/bin')
p=argparse.ArgumentParser();p.add_argument('--action',default='snapshot');p.add_argument('--name',default='snapshot');p.add_argument('--media',default='sync-stereo-aac.mp4');p.add_argument('--beat',default='0');p.add_argument('--index',default='0');p.add_argument('--offset',default='2');p.add_argument('--duration',default='12');p.add_argument('--tempo',default='110');p.add_argument('--points',default='0:80,8:160,16:100,32:140');p.add_argument('--build',action='store_true')
a=p.parse_args()
if not re.fullmatch(r'[a-zA-Z0-9_-]+',a.name):raise ValueError('Invalid result name')
last=ROOT/'native-agent-last.txt'
previous=Path(last.read_text(encoding='utf-8')) if last.exists() else None
if a.build or previous is None or not previous.exists() or (ROOT/'NativeAcceptanceAgent.java').stat().st_mtime_ns>previous.stat().st_mtime_ns:
    folder=ROOT/'native-agent-java'/str(time.time_ns());folder.mkdir(parents=True)
    subprocess.run([str(JDK/'javac.exe'),'-encoding','UTF-8','-d',str(folder),str(ROOT/'NativeAcceptanceAgent.java')],check=True)
    jar=folder/'test-driver.jar'
    subprocess.run([str(JDK/'jar.exe'),'--create','--file',str(jar),'--manifest',str(ROOT/'native-agent.mf'),'-C',str(folder),'local'],check=True)
    last.write_text(str(jar),encoding='utf-8')
jar=Path(last.read_text(encoding='utf-8'))
config=ROOT/'native-acceptance.properties'
config.write_text(f'action={a.action}\nname={a.name}\nmedia={a.media}\nbeat={a.beat}\nindex={a.index}\noffset={a.offset}\nduration={a.duration}\ntempo={a.tempo}\npoints={a.points}\n',encoding='utf-8')
processes=json.loads(subprocess.check_output(['powershell','-NoProfile','-Command','@(Get-Process BitwigStudioApp -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id) | ConvertTo-Json -Compress'],text=True))
if isinstance(processes,int):processes=[processes]
if len(processes)!=1:raise RuntimeError('Expected exactly one Bitwig host')
output=ROOT/f'results/native-{a.name}.txt';before=output.stat().st_mtime_ns if output.exists() else 0
subprocess.run([str(JDK/'java.exe'),'-jar',str(jar),str(processes[0]),str(ROOT/'native-agent-build/Release/video_acceptance.dll'),str(jar)+'|'+str(config)],check=True)
deadline=time.monotonic()+45
while time.monotonic()<deadline:
    if output.exists() and output.stat().st_mtime_ns!=before:
        raw=output.read_text(encoding='utf-8')
        try:
            state=json.loads(raw)
            if isinstance(state,list):
                print(json.dumps({'dropTargets':len(state),'videoTargets':sum(str(row.get('handler','')).endswith('$VideoDrop') for row in state)},ensure_ascii=False));print(f'EVIDENCE {output}');break
            if 'mode' in state:
                lat=sorted(state['uiLatencyMs']);err=sorted(abs(x) for x in state['playbackFrameErrorsSeconds'])
                print(json.dumps({k:state.get(k) for k in ('mode','title','result','finalExpected','finalDecoded','settleMs','completedSeeks','before-hwdec-current','after-hwdec-current','after-frame-drop-count','after-decoder-frame-drop-count')},ensure_ascii=False))
                print(json.dumps({'ui_p95_ms':lat[int((len(lat)-1)*.95)] if lat else None,'ui_max_ms':max(lat) if lat else None,'play_p95_ms':err[int((len(err)-1)*.95)]*1000 if err else None,'play_max_ms':max(err)*1000 if err else None}))
                print(f'EVIDENCE {output}');break
            print(json.dumps({k:state[k] for k in ('root','status','undo','playing','playerStatus','playerTime','playerLoaded','playerPosition') if k in state},ensure_ascii=False))
            print(json.dumps([{'type':c['type'],'title':c['title'],'beat':c['getTime'],'duration':c['getDuration'],'offset':c['getPlayStartOffset'],'bound':c['media']['bound']} for c in state['clips']],ensure_ascii=False))
            print(f'EVIDENCE {output}')
        except (ValueError,KeyError):print(raw)
        break
    time.sleep(.1)
else:raise TimeoutError(output)

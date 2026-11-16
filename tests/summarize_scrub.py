"""Summarize mpv's diagnostic log; this is software timing, not display latency."""
from pathlib import Path
import json, re, statistics

root=Path(__file__).resolve().parent.parent
log=root/'runtime/Patchbay-Stage2/config/mods/video-preview/logs/player.log'
source=log.read_text(encoding='utf-8',errors='replace')
snapshot=root/'tests/results/scrub-runtime-dev2-player.log'
snapshot.write_text(source,encoding='utf-8')
events=[]
pending=[]
maximum=0
loads=0
for line in source.splitlines():
    stamp=re.match(r'\[\s*([\d.]+)\]',line)
    if not stamp: continue
    now=float(stamp[1])
    if 'Run command: loadfile,' in line:
        loads+=1; pending=[]
    target=re.search(r'Run command: seek,.*target="([\d.-]+)"',line)
    if target:
        pending.append((now,float(target[1])))
        maximum=max(maximum,len(pending))
    frame=re.search(r'playback restart complete @ ([\d.-]+)',line)
    if frame and pending:
        start,target=pending[-1]
        events.append({'target_s':target,'decoded_s':float(frame[1]),'decode_ready_ms':round((now-start)*1000,3)})
        pending=[]
times=sorted(e['decode_ready_ms'] for e in events)
summary={
    'log':str(snapshot.relative_to(root)),
    'scope':'mpv command-to-playback-restart software timing; not display latency or a benchmark across codecs',
    'load_commands':loads,
    'completed_seek_samples':len(events),
    'maximum_seek_commands_before_restart':maximum,
    'decode_ready_median_ms':statistics.median(times) if times else None,
    'decode_ready_max_ms':max(times) if times else None,
    'events':events,
}
out=root/'tests/results/scrub-runtime-dev2.json'
out.write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in summary.items() if k!='events'},indent=2))

"""Native edits/undo and decoded playback after the long-GOP optimization."""
from pathlib import Path
import json,subprocess,sys
sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parent
def mapping(state):
    for c in state['clips']:
        if c['media']['bound']:
            assert abs(c['beginSeconds']-c['resolvedAtStart']['offset']-c['notes'][0]['nativeSourceAtClipStart'])<1e-7,c['title']
rows=[]
for index,codec in enumerate(['h264','hevc','av1','vp9','prores']):
    for mode in ['move','trim','split','play']+(['play-drag'] if index==0 else []):
        name=f'longgop-regression-{codec}-{mode}'
        r=subprocess.run([sys.executable,str(ROOT/'run_native_acceptance.py'),'--action','stress-'+mode,'--index',str(index),'--name',name],capture_output=True,text=True,encoding='utf-8',errors='replace');r.check_returncode()
        try:d=json.loads((ROOT/f'results/native-{name}.txt').read_text(encoding='utf-8'))
        except ValueError:raise RuntimeError(r.stdout)
        assert d['result']=='PASS',(name,r.stdout)
        state=json.loads(d['state']);mapping(state)
        if mode=='split':
            cuts=json.loads(d['splitState']);mapping(cuts)
            group=[c for c in cuts['clips'] if c['title']==d['title']]
            assert len(group)==13
            offsets=[c['resolvedAtStart']['offset'] for c in group]
            assert max(offsets)-min(offsets)<1e-7
            assert sum(c['title']==d['title'] for c in state['clips'])==1
        if mode=='trim':assert d['completedSeeks']==0
        latency=sorted(d['uiLatencyMs']);errors=[abs(e)*1000 for e in d['playbackFrameErrorsSeconds']]
        if mode.startswith('play'):
            assert len(errors)==35 and max(errors)<80
            assert int(d['after-frame-drop-count'])==int(d['after-decoder-frame-drop-count'])==0
        row={'codec':codec,'mode':mode,'settle_ms':d['settleMs'],'ui_p95_ms':latency[int((len(latency)-1)*.95)] if latency else None,'ui_max_ms':max(latency) if latency else None,'play_max_ms':max(errors) if errors else None,'seeks':d['completedSeeks'],'steps':d.get('steppedFrames',0),'reuses':d.get('reusedFrames',0)}
        rows.append(row);print(json.dumps(row),flush=True)
        (ROOT/'results/longgop-regression.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
print('LONGGOP_REGRESSION_PASS',flush=True)

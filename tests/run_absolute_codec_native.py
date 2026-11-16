"""4K/VFR regression through the native editor with absolute time enabled."""
from pathlib import Path
import json,subprocess,sys
sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parent
def run(action,name,**kw):
    cmd=[sys.executable,str(ROOT/'run_native_acceptance.py'),'--action',action,'--name','absolute-codec-'+name]
    for k,v in kw.items():cmd.extend(['--'+k,str(v)])
    subprocess.run(cmd,check=True,stdout=subprocess.DEVNULL)
    raw=(ROOT/f'results/native-absolute-codec-{name}.txt').read_text(encoding='utf8')
    try:return json.loads(raw)
    except ValueError:raise RuntimeError(raw)
def stable(action,name,**kw):run(action,name,**kw);return run('snapshot',name+'-settled')
def mapping(state):
    assert state['absoluteFailures']==0,state['status']
    for c in state['clips']:
        if c['media']['bound']:
            n=c['notes'][0]
            assert abs(c['beginSeconds']-c['resolvedAtStart']['offset']-n['nativeSourceAtClipStart'])<0.00002,c['title']
            assert abs(n['nativeSourceAfterOneSecond']-n['nativeSourceAtClipStart']-1)<0.00002,c['title']
rows=[]
for index,codec in enumerate(['h264','hevc','av1','vp9','prores']):
    for mode in ['move','trim','split','play']+(['play-drag'] if index==0 else []):
        d=run('stress-'+mode,codec+'-'+mode,index=index)
        assert d['result']=='PASS',(codec,mode,d)
        state=json.loads(d['state']);mapping(state)
        assert all(c['media']['absolute'] for c in state['clips'])
        if mode=='split':
            cuts=json.loads(d['splitState']);mapping(cuts)
            group=[c for c in cuts['clips'] if c['title']==d['title']]
            assert len(group)==13
            offsets=[c['resolvedAtStart']['offset'] for c in group]
            assert max(offsets)-min(offsets)<0.00002
            assert sum(c['title']==d['title'] for c in state['clips'])==1
        latency=sorted(d['uiLatencyMs']);errors=[abs(e)*1000 for e in d['playbackFrameErrorsSeconds']]
        if mode.startswith('play'):
            assert len(errors)==35 and max(errors)<80
            assert int(d['after-frame-drop-count'])==int(d['after-decoder-frame-drop-count'])==0
        row={'codec':codec,'mode':mode,'settle_ms':d['settleMs'],'ui_p95_ms':latency[int((len(latency)-1)*.95)] if latency else None,'ui_max_ms':max(latency) if latency else None,'play_max_ms':max(errors) if errors else None,'seeks':d['completedSeeks'],'steps':d.get('steppedFrames',0),'reuses':d.get('reusedFrames',0)}
        rows.append(row);print(json.dumps(row),flush=True)
        (ROOT/'results/absolute-codec-regression.json').write_text(json.dumps(rows,indent=2),encoding='utf8')
run('save','saved')
print('ABSOLUTE_CODEC_REGRESSION_PASS',flush=True)

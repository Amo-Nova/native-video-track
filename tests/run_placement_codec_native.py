"""Native editing and real decoder regression, one codec on each preview placement."""
from pathlib import Path
import json, subprocess, sys
sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parent
def run(action,name,**options):
    name='placement-codec-'+name
    command=[sys.executable,str(ROOT/'run_native_acceptance.py'),'--action',action,'--name',name]
    for key,value in options.items():command.extend(['--'+key,str(value)])
    result=subprocess.run(command,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=55)
    if result.returncode:raise RuntimeError(result.stdout+result.stderr)
    return json.loads((ROOT/f'results/native-{name}.txt').read_text(encoding='utf-8'))
def mapping(state):
    assert state['absoluteFailures']==0
    for clip in state['clips']:
        assert clip['media']['absolute']
        if clip['media']['bound']:
            note=clip['notes'][0]
            assert abs(clip['sourceStart']-note['nativeSourceAtClipStart'])<.00002,clip['title']
            assert abs(note['nativeSourceAfterOneSecond']-note['nativeSourceAtClipStart']-1)<.00002,clip['title']
rows=[]
for index,(codec,position) in enumerate([('h264','top'),('hevc','right'),('av1','float'),('vp9','left'),('prores','bottom')]):
    run('placement',codec+'-position',media=position)
    for mode in ('move','trim','split','play'):
        result=run('stress-'+mode,codec+'-'+mode,index=index)
        assert result['result']=='PASS',(codec,position,mode,result)
        state=json.loads(result['state']);mapping(state)
        assert state['previewPosition']==position and state['previewWindow']['presented']
        if mode=='split':
            cuts=json.loads(result['splitState']);mapping(cuts)
            group=[c for c in cuts['clips'] if c['title']==result['title']]
            assert len(group)==13
            offsets=[c['resolvedAtStart']['offset'] for c in group]
            assert max(offsets)-min(offsets)<.00002
            assert sum(c['title']==result['title'] for c in state['clips'])==1
        latency=sorted(result['uiLatencyMs']);errors=[abs(x)*1000 for x in result['playbackFrameErrorsSeconds']]
        if mode=='play':
            assert len(errors)==35 and max(errors)<80
            assert int(result['after-frame-drop-count'])==int(result['after-decoder-frame-drop-count'])==0
        row={'codec':codec,'position':position,'mode':mode,'settle_ms':result['settleMs'],'ui_p95_ms':latency[int((len(latency)-1)*.95)] if latency else None,'ui_max_ms':max(latency) if latency else None,'play_max_ms':max(errors) if errors else None,'seeks':result['completedSeeks'],'reuses':result.get('reusedFrames',0),'result':'PASS'}
        rows.append(row);print(json.dumps(row),flush=True)
        (ROOT/'results/placement-codec-regression.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
run('save','saved')
print('PLACEMENT_CODEC_REGRESSION_PASS',flush=True)

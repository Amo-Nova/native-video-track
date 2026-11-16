"""Focused editing/decoder regression after persistent ordinary startup."""
from pathlib import Path
import json, subprocess, sys, time
sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parents[2]/'tests'
OUT=Path(__file__).resolve().parent/'native-edit-regression.json'
stamp=str(time.time_ns());rows=[]
def run(action):
    name='persistent-'+stamp+'-'+action
    r=subprocess.run([sys.executable,str(ROOT/'run_native_acceptance.py'),'--action',action,'--index','0','--name',name],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=55)
    if r.returncode:raise RuntimeError(r.stdout+r.stderr)
    return json.loads((ROOT/f'results/native-{name}.txt').read_text(encoding='utf-8'))
def mapping(state):
    assert state['absoluteFailures']==0
    for clip in state['clips']:
        assert clip['media']['absolute']
        if clip['media']['bound']:
            note=clip['notes'][0]
            assert abs(clip['sourceStart']-note['nativeSourceAtClipStart'])<.00002
            assert abs(note['nativeSourceAfterOneSecond']-note['nativeSourceAtClipStart']-1)<.00002
initial=run('snapshot');mapping(initial)
for mode in ['seek','move','trim','split','play']:
    result=run('stress-'+mode);assert result['result']=='PASS',result
    state=json.loads(result['state']);mapping(state)
    assert len(state['clips'])==len(initial['clips']) and state['previewWindow']['presented']
    if mode=='split':
        split=json.loads(result['splitState']);mapping(split)
        assert len(split['clips'])==len(initial['clips'])+12
        first=initial['clips'][0]
        parts=sorted([c for c in split['clips'] if c['beginSeconds']>=first['beginSeconds']-.00002 and c['endSeconds']<=first['endSeconds']+.00002],key=lambda c:c['beginSeconds'])
        assert len(parts)==13
        for a,b in zip(parts,parts[1:]):
            assert abs(a['endSeconds']-b['beginSeconds'])<.00002
            assert abs(b['sourceStart']-a['sourceStart']-a['endSeconds']+a['beginSeconds'])<.00002
    latency=sorted(result['uiLatencyMs']);errors=[abs(x)*1000 for x in result['playbackFrameErrorsSeconds']]
    if mode=='play':
        assert len(errors)==35 and max(errors)<80
        assert int(result['after-frame-drop-count'])==int(result['after-decoder-frame-drop-count'])==0
    row={'mode':mode,'result':'PASS','settleMs':result['settleMs'],'uiP95Ms':latency[int((len(latency)-1)*.95)] if latency else None,'uiMaxMs':max(latency) if latency else None,'playMaxFrameErrorMs':max(errors) if errors else None,'seeks':result['completedSeeks']}
    rows.append(row);print(json.dumps(row),flush=True);OUT.write_text(json.dumps(rows,indent=2),encoding='utf-8')
run('save')
print('PERSISTENT_EDIT_REGRESSION_PASS',flush=True)

"""Live placement acceptance against an already opened, guarded test project.

Checks real native state and stable decoder identity; never captures desktop pixels.
"""
from pathlib import Path
import json, subprocess, sys, time

ROOT=Path(__file__).resolve().parent
STAMP=time.strftime('%Y%m%d-%H%M%S')
OUT=ROOT/('results/placement-050-'+STAMP)
OUT.mkdir(exist_ok=True)
serial=0
def run(action,**options):
    global serial
    serial+=1
    name=f'placement-check-{STAMP}-{serial:03d}'
    command=[sys.executable,str(ROOT/'run_native_acceptance.py'),'--action',action,'--name',name]
    for key,value in options.items():command.extend(['--'+key,str(value)])
    result=subprocess.run(command,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=55)
    if result.returncode:raise RuntimeError(result.stdout+result.stderr)
    value=json.loads((ROOT/f'results/native-{name}.txt').read_text(encoding='utf-8'))
    (OUT/f'{serial:03d}-{action}.json').write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
    return value
def settled(action,**options):
    run(action,**options)
    return run('snapshot')
def content(state):
    return {k:state[k] for k in ('clips','markers','allNativeTracks','undo','modified')}

run('seek',beat=30)
baseline=run('snapshot')
assert baseline['playerPid'] and len(baseline['clips'])==2,'Expected the two-clip Absolute Verified fixture'
pid=baseline['playerPid'];hwnd=baseline['previewWindow']['handle'];initial=content(baseline)
report=[]
def verify(state,position,playing=False):
    assert state['previewPosition']==position,state
    assert state['previewShown'] and state['previewWindow']['presented'],state
    assert state['playerPid']==pid and state['previewWindow']['handle']==hwnd,'Decoder/render window restarted'
    assert content(state)==initial,'A layout operation changed project content or undo state'
    edges=[e for e in state['previewLayout'] if e['active']]
    assert len(edges)==(0 if position=='float' else 1)
    if edges:assert edges[0]['position']==position and not edges[0]['collapsed']
    assert state.get('floatingVisible',False)==(position=='float')
    if not playing:assert abs(state['playerPosition']-baseline['playerPosition'])<.002
    else:
        assert state['playing']
        # Both fixture clips preserve project seconds minus 10 seconds as the source time.
        error=state['playerPosition']-(state['projectSeconds']-10)
        assert abs(error)<.080,f'Playback error {error}'
        report.append({'case':'playing-'+position,'clockErrorMs':error*1000})

for position in ('top','left','right','bottom','float','top','float'):
    state=settled('placement',media=position);verify(state,position)
    report.append({'case':'stopped-'+position,'window':state['previewWindow']})
print('Five placements preserve frame, project, decoder and render HWND.',flush=True)

state=settled('float-size',beat=300,offset=180,duration=720,tempo=480)
verify(state,'float');assert state['floatingRect']==[300,180,1020,660],state['floatingRect']
state=settled('float-close');assert not state['previewShown'] and not state['floatingVisible'] and not state['previewWindow']['presented']
state=settled('panel');verify(state,'float');assert state['floatingRect']==[300,180,1020,660]
report.append({'case':'float-resize-close-restore','rect':state['floatingRect']})
state=settled('placement',media='left')
state=settled('preview-size',duration=320);verify(state,'left')
assert abs(next(e for e in state['previewLayout'] if e['active'])['size']-320)<1
state=settled('placement',media='bottom');state=settled('placement',media='left');verify(state,'left')
assert abs(next(e for e in state['previewLayout'] if e['active'])['size']-320)<1
report.append({'case':'dock-size-restored','size':320})
print('Floating close/restore and per-edge size memory passed.',flush=True)

run('play',beat=30)
try:
    for position in ('float','right','top','bottom','left'):
        state=settled('placement',media=position);verify(state,position,True)
finally:run('stop')
run('seek',beat=30)
(OUT/'summary.json').write_text(json.dumps({'result':'PASS','decoderPid':pid,'renderHwnd':hwnd,'cases':report},ensure_ascii=False,indent=2),encoding='utf-8')
print('PLACEMENT_ACCEPTANCE_PASS '+str(OUT/'summary.json'),flush=True)

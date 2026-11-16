"""New project, AV import, tempo, separation/undo, and collection with the new layout."""
from pathlib import Path
import json, subprocess, sys
ROOT=Path(__file__).resolve().parent
def run(action,name,**kw):
    name='placement-life-'+name
    cmd=[sys.executable,str(ROOT/'run_native_acceptance.py'),'--action',action,'--name',name]
    for k,v in kw.items():cmd.extend(['--'+k,str(v)])
    p=subprocess.run(cmd,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=55)
    if p.returncode:raise RuntimeError(p.stdout+p.stderr)
    raw=(ROOT/f'results/native-{name}.txt').read_text(encoding='utf-8')
    try:return json.loads(raw)
    except ValueError:
        if 'asynchronous' in raw:return None
        raise
def stable(action,name,**kw):run(action,name,**kw);return run('snapshot',name+'-stable')
def times(state):return [[c[k] for k in ('beginSeconds','endSeconds','sourceStart')] for c in state['clips']]

stable('placement','left',media='left')
state=stable('new','new')
assert state['previewPosition']=='left' and state['clips']==[] and not state['previewWindow']['presented'] and state['playerLoaded']==''
state=stable('import','import',media='sync-stereo-aac.mp4',beat=8)
assert len(state['clips'])==1 and state['clips'][0]['media']['bound'] and state['clips'][0]['media']['absolute']
assert int(state['clips'][0]['notes'][0]['getChannelCount'])==2
before=times(state)
state=stable('marker-add','cue',media='PlacementCue',beat=6)
assert state['markers'][-1]['locked'] and abs(state['markers'][-1]['seconds']-6)<.00002
state=stable('tempo','tempo',tempo=140)
assert all(abs(a-b)<.00002 for u,v in zip(before,times(state)) for a,b in zip(u,v))
assert abs(state['markers'][-1]['seconds']-6)<.00002
state=stable('separate','separate')
assert not state['clips'][0]['media']['bound'] and sum(len(t) for t in state['allNativeTracks'])==2
state=stable('undo','rebind')
assert state['clips'][0]['media']['bound'] and sum(len(t) for t in state['allNativeTracks'])==1
state=stable('saveas','save',media='projects/placement-new/Layout Lifecycle.bwproject')
assert not state['modified'] and Path(state['projectFile']).is_file()
asset=state['clips'][0]['media']['asset'];folder=Path(state['root'])
assert (folder/asset['relative']).stat().st_size==asset['bytes']
assert (folder/asset['audioRelative']).stat().st_size==asset['audioBytes']
state=stable('placement','float',media='float')
assert not state['modified']
state=stable('seek','frame',beat=14)
assert state['previewWindow']['presented']
(ROOT/'results/placement-lifecycle.json').write_text(json.dumps({'result':'PASS','projectFile':state['projectFile'],'beforeTimes':before,'savedState':state},ensure_ascii=False,indent=2),encoding='utf-8')
print('PLACEMENT_LIFECYCLE_PASS '+state['projectFile'],flush=True)

"""Real host/decoder acceptance; all media and project writes stay in generated tests."""
from pathlib import Path
import argparse, json, shutil, subprocess, sys, time

ROOT=Path(__file__).resolve().parent
RESULT=ROOT/'results/multi-video-import'
RESULT.mkdir(parents=True,exist_ok=True)
serial=0
run_id=str(time.time_ns())

def run(action,**options):
    global serial
    serial+=1
    name=f'multi-{ARGS.phase}-{run_id}-{serial}-{action}'
    command=[sys.executable,str(ROOT/'run_native_acceptance.py'),'--action',action,'--name',name]
    for key,value in options.items():command.extend(['--'+key,str(value)])
    result=subprocess.run(command,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=55)
    if result.returncode:raise RuntimeError(result.stdout+result.stderr)
    text=(ROOT/f'results/native-{name}.txt').read_text(encoding='utf-8')
    try:return json.loads(text)
    except ValueError:raise RuntimeError(text)

def wait_for(predicate,timeout=40):
    end=time.monotonic()+timeout
    while True:
        state=run('snapshot')
        if predicate(state):return state
        if time.monotonic()>end:raise AssertionError({k:state.get(k) for k in ('projectFile','status','playerStatus','playerPosition','projectSeconds')})
        time.sleep(.25)

def mapping(state):
    assert state['absoluteFailures']==0
    for clip in state['clips']:
        assert clip['media']['absolute']
        if clip['media']['bound']:
            note=clip['notes'][0]
            assert abs(clip['sourceStart']-note['nativeSourceAtClipStart'])<.00002
            assert abs(note['nativeSourceAfterOneSecond']-note['nativeSourceAtClipStart']-1)<.00002

def locate(clip):
    # Tests use a constant tempo; arbitrary tempo-map regression is a separate suite.
    state=run('snapshot');beat=clip['getTime']+state['tempo']/60
    run('seek',beat=beat)
    expected=clip['sourceStart']+1
    state=wait_for(lambda s: s.get('playerLoaded')==str(Path(s['root'])/clip['media']['asset']['relative'])
                   and abs(s.get('playerPosition',-100)-expected)<.065 and s['previewWindow']['presented'])
    mapping(state)
    return {'file':clip['media']['file'],'expected':expected,'actual':state['playerPosition'],'loaded':state['playerLoaded']}

def save_report(name,value):
    (RESULT/(name+'.json')).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
    print(name+' PASS',flush=True)

parser=argparse.ArgumentParser();parser.add_argument('phase',choices=['import','portable','missing','recovered'])
ARGS=parser.parse_args()
if ARGS.phase=='import':
    fixtures=ROOT/'media/multi-import-fixtures'
    for name,source in [('a/shared.mp4','sync-stereo-aac.mp4'),('b/shared.mp4','sync-delayed-aac.mp4'),('silent/clip.mp4','clip-b-24fps.mp4')]:
        target=fixtures/name;target.parent.mkdir(parents=True,exist_ok=True)
        if not target.exists():shutil.copy2(ROOT/'media'/source,target)
    state=run('snapshot')
    assert not state['modified'] and not state['projectFile'] and not any(state['allNativeTracks'])
    (ROOT/'native-owned-temp.txt').write_text(state['root'],encoding='utf-8')
    rows=run('drop',media='multi-import-fixtures/a/shared.mp4|multi-import-fixtures/b/shared.mp4',beat=8)
    assert len(rows)>1 and all(r['handler'].endswith('$VideoDrop') for r in rows)
    state=wait_for(lambda s:len(s['clips'])==2)
    first,second=state['clips'];mapping(state)
    assert abs(first['getTime']-8)<.00002
    assert abs(first['endSeconds']-second['beginSeconds'])<.00002
    assert first['media']['asset']['id']!=second['media']['asset']['id']
    assert Path(first['media']['file']).name==Path(second['media']['file']).name=='shared.mp4'
    views=[locate(first),locate(second)]
    # Separate import requests arrive in the same event dispatch before the first can finish.
    run('queue-imports',media='multi-import-fixtures/silent/clip.mp4|multi-import-fixtures/a/shared.mp4',beat=80)
    state=wait_for(lambda s:len(s['clips'])==4)
    assert sorted(round(c['getTime'],5) for c in state['clips'])==[8,30,80,120]
    assert len({c['media']['asset']['id'] for c in state['clips']})==3
    views.extend(locate(c) for c in state['clips'] if c['getTime']>=80)
    index=next(i for i,c in enumerate(state['clips']) if c['getTime']==8)
    run('move',index=index,beat=4)
    moved=run('snapshot');mapping(moved)
    chosen=next(c for c in moved['clips'] if c['getTime']==4)
    assert abs(chosen['sourceStart']-first['sourceStart'])<.00002
    assert abs(chosen['endSeconds']-chosen['beginSeconds']-12)<.00002
    views.append(locate(chosen))
    run('undo');run('split',index=index,beat=18)
    cuts=wait_for(lambda s:len(s['clips'])==5);mapping(cuts)
    pair=sorted([c for c in cuts['clips'] if c['getTime']<30],key=lambda c:c['getTime'])
    assert len(pair)==2 and abs(pair[0]['endSeconds']-pair[1]['beginSeconds'])<.00002
    assert abs(pair[1]['sourceStart']-(pair[0]['endSeconds']-pair[0]['beginSeconds']))<.00002
    views.append(locate(pair[1]));run('undo')
    state=wait_for(lambda s:len(s['clips'])==4)
    before=[(c['beginSeconds'],c['endSeconds'],c['sourceStart']) for c in state['clips']]
    run('tempo',tempo=140);state=run('snapshot');mapping(state)
    after=[(c['beginSeconds'],c['endSeconds'],c['sourceStart']) for c in state['clips']]
    assert all(abs(a-b)<.00002 for u,v in zip(before,after) for a,b in zip(u,v))
    run('saveas',media='projects/multi-import/Multi Import.bwproject')
    saved=wait_for(lambda s:bool(s['projectFile']) and not s['modified'] and '素材已收集' in s['status'])
    for clip in saved['clips']:
        asset=clip['media']['asset'];folder=Path(saved['root'])
        assert (folder/asset['relative']).stat().st_size==asset['bytes']
        if asset['audioRelative']:assert (folder/asset['audioRelative']).stat().st_size==asset['audioBytes']
    save_report('import',{'result':'PASS','dropTargets':rows,'views':views,'saved':saved,'split':cuts})
elif ARGS.phase in ('portable','missing'):
    imported=json.loads((RESULT/'import.json').read_text(encoding='utf-8'))['saved']
    project=ROOT/'projects/multi-import-portable/Multi Import'/Path(imported['projectFile']).name
    run('open',media=str(project.relative_to(ROOT)))
    state=wait_for(lambda s:Path(s['projectFile'])==project and len(s['clips'])==4 and
                   ('素材已收集' in s['status'] if ARGS.phase=='portable' else '待恢复' in s['status']))
    mapping(state);views=[]
    for clip in state['clips']:
        asset=clip['media']['asset']
        if ARGS.phase=='missing' and asset['id']==imported['clips'][0]['media']['asset']['id']:continue
        views.append(locate(clip))
    assert len(views)==(4 if ARGS.phase=='portable' else 2)
    save_report(ARGS.phase,{'result':'PASS','views':views,'state':state})
else:
    expected=json.loads((RESULT/'import.json').read_text(encoding='utf-8'))['saved']['clips'][0]['media']['asset']['id']
    state=wait_for(lambda s:'素材已收集' in s['status'],timeout=25)
    clip=next(c for c in state['clips'] if c['media']['asset']['id']==expected)
    save_report('recovered',{'result':'PASS','view':locate(clip),'state':state})

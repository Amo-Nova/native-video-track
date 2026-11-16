"""Requirements-based acceptance of absolute-time edits in an owned Bitwig project."""
from pathlib import Path
import json, subprocess, sys, argparse

ROOT=Path(__file__).resolve().parent
sys.stdout.reconfigure(encoding='utf-8')
parser=argparse.ArgumentParser();parser.add_argument('--phase',default='edits');args=parser.parse_args()
rows=[]
def action(kind,tag=None,**kw):
    name='absolute-'+args.phase+'-'+(tag or kind)
    command=[sys.executable,str(ROOT/'run_native_acceptance.py'),'--action',kind,'--name',name]
    for key,value in kw.items():command.extend(['--'+key,str(value)])
    subprocess.run(command,check=True,stdout=subprocess.DEVNULL)
    value=json.loads((ROOT/f'results/native-{name}.txt').read_text(encoding='utf-8'))
    return value
def settled(kind,tag=None,**kw):
    action(kind,tag,**kw)
    return action('snapshot',(tag or kind)+'-settled')
def near(a,b):assert abs(a-b)<0.00002,(a,b)
def geometry(s,expected):
    assert s['absoluteFailures']==0,s['status']
    assert len(s['clips'])==len(expected),(len(s['clips']),len(expected))
    for clip,want in zip(s['clips'],expected):
        for key,value in zip(['beginSeconds','endSeconds','sourceStart'],want):near(clip[key],value)
        for note in clip['notes']:
            near(note['nativeSourceAtClipStart'],clip['sourceStart'])
            near(note['nativeSourceAfterOneSecond'],clip['sourceStart']+1)
            assert note['getWarpMode']=='RAW'
    return s
def check(name,s):
    rows.append({'case':name,'clips':[(c['beginSeconds'],c['endSeconds'],c['sourceStart']) for c in s['clips']],'failures':s['absoluteFailures']})
    print('PASS',name,flush=True)
def restore(name):return geometry(settled('undo',name),[(12,32,2),(32,70,22)])
try:
    if args.phase=='edits':
        base=geometry(action('snapshot','before'),[(12,32,2),(32,70,22)])
        map_after32=lambda beat:base['beatSeconds']['32.0']+(beat-32)*60/140
        c=base['clips'][1]
        moved=geometry(settled('move',index=1,beat=180),[(12,32,2),(map_after32(180),map_after32(180)+38,22)])
        assert moved['clips'][1]['identity']==c['identity'];check('curve move keeps duration, source, identity',moved);restore('undo-move')
        copied=geometry(settled('copy',index=1,beat=200),[(12,32,2),(32,70,22),(map_after32(200),map_after32(200)+38,22)])
        check('curve copy keeps source and real duration',copied);restore('undo-copy')
        trimmed=geometry(settled('trim',index=1,beat=75,duration=65,offset=c['getPlayStartOffset']+2),[(12,32,2),(map_after32(75),map_after32(140),22+2*60/140)])
        check('curve trim keeps video and RAW audio aligned',trimmed);restore('undo-trim')
        split=geometry(settled('split',index=1,beat=100),[(12,32,2),(32,map_after32(100),22),(map_after32(100),70,22+map_after32(100)-32)])
        check('native split preserves continuity',split)
        expected=[(c['beginSeconds'],c['endSeconds'],c['sourceStart']) for c in split['clips']]
        again=geometry(settled('tempomap','second-curve',points='0:150,10:75,60:180'),expected)
        near(again['markers'][0]['seconds'],30);check('second curve preserves all split ranges and cues',again)
        geometry(settled('undo','undo-second-curve'),expected);restore('undo-split')
        unlocked=settled('marker-lock','unlock-marker',index=0,offset=0)
        assert not unlocked['markers'][0]['locked'];check('unlock cue',unlocked)
        locked=settled('undo','undo-unlock')
        assert locked['markers'][0]['locked'];check('cue mode undo',locked)
        separated=settled('separate',index=0)
        geometry(separated,[(32,70,22),(12,32,2)] if separated['clips'][0]['beginSeconds']>20 else [(12,32,2),(32,70,22)])
        assert len(separated['timedClips'])==3
        detached=[c for c in separated['timedClips'] if c['media']['audioOnly']]
        assert len(detached)==1
        for key,value in zip(['beginSeconds','endSeconds','sourceStart'],[12,32,2]):near(detached[0][key],value)
        check('separation keeps independent locked audio and video',separated)
        other=settled('tempomap','separated-curve',points='0:150,10:75,60:180')
        for c in other['timedClips']:
            match=next(x for x in separated['timedClips'] if x['identity']==c['identity'])
            for key in ['beginSeconds','endSeconds','sourceStart']:near(c[key],match[key])
        check('tempo change after separation',other)
        settled('undo','undo-separated-curve');restore('undo-separate')
    else:raise ValueError(args.phase)
    result={'result':'PASS','cases':rows}
except Exception as exc:
    result={'result':'FAIL','cases':rows,'error':repr(exc)}
    raise
finally:
    (ROOT/f'results/absolute-{args.phase}-summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')

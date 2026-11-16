from pathlib import Path
import json,subprocess,sys
ROOT=Path(__file__).resolve().parent
sys.stdout.reconfigure(encoding='utf8')
def run(action,name,**kw):
    cmd=[sys.executable,str(ROOT/'run_native_acceptance.py'),'--action',action,'--name','absolute-prepare-'+name]
    for k,v in kw.items():cmd.extend(['--'+k,str(v)])
    subprocess.run(cmd,check=True,stdout=subprocess.DEVNULL)
    return json.loads((ROOT/f'results/native-absolute-prepare-{name}.txt').read_text(encoding='utf8'))
def stable(action,name,**kw):run(action,name,**kw);return run('snapshot',name+'-settled')
def geom(s):return [(c['beginSeconds'],c['endSeconds'],c['sourceStart']) for c in s['clips']]
def equal(a,b):
    assert len(a)==len(b)
    for x,y in zip(a,b):
        for p,q in zip(x,y):assert abs(p-q)<.00002,(a,b)
s=run('snapshot','resume') if '--resume' in sys.argv else stable('open','old-open',media='projects/compatibility/Compatibility/Compatibility.bwproject')
assert len(s['clips'])==5
assert all(not c['media']['absolute'] for c in s['clips'])
baseline=geom(s)
if '--resume' not in sys.argv:s=stable('saveas','copy',media='projects/absolute-codecs-final/Absolute Codecs.bwproject')
assert all(not c['media']['absolute'] for c in s['clips'])
changed=stable('tempomap','old-tempo',points='0:240,200:240')
assert [(c['getTime'],c['getDuration'],c['getPlayStartOffset']) for c in changed['clips']]==[(c['getTime'],c['getDuration'],c['getPlayStartOffset']) for c in s['clips']]
assert abs(geom(changed)[0][0]-baseline[0][0])>1
equal(geom(stable('undo','old-tempo-undo')),baseline)
print('PASS old project keeps beat semantics',flush=True)
for i in range(5):
    s=stable('lock-time','convert-'+str(i),index=i)
    assert s['clips'][i]['media']['absolute'];equal(geom(s),baseline)
    if i==0:
        u=stable('undo','conversion-undo');assert not u['clips'][0]['media']['absolute'];equal(geom(u),baseline)
        r=stable('redo','conversion-redo');assert r['clips'][0]['media']['absolute'];equal(geom(r),baseline)
print('PASS conversions preserve geometry/source and undo',flush=True)
s=stable('tempomap','curve',points='0:150,10:75,60:180');equal(geom(s),baseline);assert s['absoluteFailures']==0
print('PASS all five converted codecs keep absolute seconds through curve',flush=True)
stable('save','saved')
(ROOT/'results/absolute-legacy-summary.json').write_text(json.dumps({'result':'PASS','baseline':baseline,'project':s['projectFile']},indent=2),encoding='utf8')

"""Verify actual decoder frames, native edits and saved/reopened media for dev.4."""
from pathlib import Path
import json,statistics,hashlib
P=Path(__file__).resolve().parent.parent
R=P/'tests/results'
def read(name):return json.loads((R/f'native-{name}.txt').read_text(encoding='utf-8'))
def state(value):return json.loads(value) if isinstance(value,str) else value
def mapping(s):
    errors=[]
    for c in s['clips']:
        if c['media']['bound']:
            errors.append(abs(c['beginSeconds']-c['resolvedAtStart']['offset']-c['notes'][0]['nativeSourceAtClipStart']))
    assert errors and max(errors)<1e-7,errors
    return max(errors)
before=json.loads((R/'longgop-before.json').read_text(encoding='utf-8'))
after=json.loads((R/'longgop-after.json').read_text(encoding='utf-8'))
assert len(before)==9 and len(after)==27
timing=[]
for old in before:
    case=old['case'];samples=[x for x in after if x['case']==case]
    assert len(samples)==3
    baseline=read('longgop-before-'+case)
    for row in samples:
        d=read('longgop-after-'+case+(f'-r{row["round"]}' if row['round']>1 else ''))
        assert d['result']=='PASS' and abs(d['finalDecoded']-d['rawExactReference'])<1e-6
        assert abs(d['finalExpected']-baseline['finalExpected'])<1e-6
        mapping(state(d['state']))
    times=[x['settle_ms'] for x in samples]
    timing.append({'case':case,'before_single_ms':old['settle_ms'],'after_min_ms':min(times),'after_median_ms':statistics.median(times),'after_max_ms':max(times)})
matrix=json.loads((R/'longgop-regression.json').read_text(encoding='utf-8'))
assert len(matrix)==21
for row in matrix:
    d=read('longgop-regression-'+row['codec']+'-'+row['mode'])
    assert d['result']=='PASS' and abs(d['finalDecoded']-d['rawExactReference'])<1e-6
    mapping(state(d['state']))
    if row['mode']=='split':
        cut=state(d['splitState']);mapping(cut)
        clips=[c for c in cut['clips'] if c['title']==d['title']]
        assert len(clips)==13
        offsets=[c['resolvedAtStart']['offset'] for c in clips]
        assert max(offsets)-min(offsets)<1e-7
        assert sum(c['title']==d['title'] for c in state(d['state'])['clips'])==1
    if row['mode'].startswith('play'):
        assert len(d['playbackFrameErrorsSeconds'])==35
        assert max(map(abs,d['playbackFrameErrorsSeconds']))<.080
        assert int(d['after-frame-drop-count'])==int(d['after-decoder-frame-drop-count'])==0
step=read('longgop-tempo-split-visible');assert step['result']=='PASS';mapping(state(step['splitState']))
assert len(state(step['splitState'])['clips'])==13 and len(state(step['state'])['clips'])==1
final=read('longgop-final-reopened');assert len(final['clips'])==5 and not final['playing'];mapping(final)
for clip in final['clips']:
    for value in [clip['resolvedAtStart']['file'],*[n['getAudioFile'] for n in clip['notes']]]:
        path=Path(value);assert path.is_file() and path.is_relative_to(Path(final['root']))
cold=read('longgop-final-cold-play');assert cold['result']=='PASS' and len(cold['playbackFrameErrorsSeconds'])==35
stable={}
for name in ['VideoTimeline.java','VideoWidget.java','DockHost.java','TransportTime.java']:
    source=P/'video-preview/src/local/bitwig/video'/name
    assert source.read_bytes()==(P/'releases/0.3.0-dev.3/video-preview/src/local/bitwig/video'/name).read_bytes()
    stable[name]=hashlib.sha256(source.read_bytes()).hexdigest()
for test,marker in [('MpvExactReuseTest','EXACT_REUSE_PASS checks=175'),('MpvForwardStepTest','FORWARD_STEP_PASS checked=90'),('MpvPipeTest','PIPE_PASS replies=1280'),('MpvFinalRetargetTest','FINAL_RETARGET_PASS interrupted=3')]:
    assert marker in (R/f'{test}.log').read_text(encoding='utf-8')
result={'result':'PASS','version':'0.3.0-dev.4','helper_sha256':hashlib.sha256((P/'out/video-preview/helper.jar').read_bytes()).hexdigest(),'timing':timing,'regression':matrix,'splits_and_undo':72,'source_mapping_max_s':mapping(final),'playback_sample_max_ms':max([row['play_max_ms'] or 0 for row in matrix]),'reopened_cold_play_max_ms':max(map(abs,cold['playbackFrameErrorsSeconds']))*1000,'stable_sources':stable,'saved_reopened_project':final['projectFile'],'limits':['Timing includes host transport and IPC observation; not mouse-to-display latency. Baseline is one sample per case; candidate three.','Uncached/cross-GOP exact decoding is still required; some locations are comparable to or slower than the earlier sample.','Playback sampled from 0.7 seconds after start, 35 samples per case; no display/soundcard loopback measurement.','The secondary tempo project playback produced no active transport samples and is excluded; its split/undo test passed.','Native API edits, not OS mouse input; window clipping code unchanged without new visual acceptance.']}
(R/'longgop-summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print('LONGGOP_VERIFIED',json.dumps({k:v for k,v in result.items() if k not in ['regression','stable_sources','timing']},ensure_ascii=True))
for row in timing:print(json.dumps(row))

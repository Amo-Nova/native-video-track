"""Validate recorded host effects, not just successful API calls."""
from pathlib import Path
import json,hashlib
ROOT=Path(__file__).resolve().parent.parent
RESULTS=ROOT/'tests/results'
def read(name):return json.loads((RESULTS/f'native-{name}.txt').read_text(encoding='utf-8'))
def percentile(values,p=.95):
    values=sorted(values);return values[int((len(values)-1)*p)] if values else None
def audio_video_error(state):
    errors=[]
    for clip in state['clips']:
        if clip['media']['bound'] and 'beginSeconds' in clip:
            audio=clip['notes'][0]['nativeSourceAtClipStart']
            video=clip['beginSeconds']-clip['resolvedAtStart']['offset']
            errors.append(abs(audio-video))
    assert errors and max(errors)<1e-7,errors
    return max(errors)
assets=json.loads((RESULTS/'compatibility-assets/report.json').read_text(encoding='utf-8'))
assert len(assets)==5 and all(a['result']=='PASS' for a in assets)
matrix=[]
for codec in ('h264','hevc','av1','vp9','prores'):
    row={'codec':codec,'modes':{}}
    for mode in ('seek','move','trim','split','play'):
        report=read(f'compat-{codec}-{mode}');assert report['result']=='PASS',(codec,mode)
        latency=report['uiLatencyMs'];errors=report['playbackFrameErrorsSeconds']
        assert report['settleMs']<750,(codec,mode,report['settleMs'])
        if mode in ('seek','move','trim'):assert percentile(latency)<16 and max(latency)<100,(codec,mode,latency)
        if mode=='trim':assert report['completedSeeks']==0,(codec,report['completedSeeks'])
        if mode=='split':
            split=json.loads(report['splitState']);clips=[c for c in split['clips'] if c['title']==report['title']]
            assert len(clips)==13
            offsets=[c['resolvedAtStart']['offset'] for c in clips]
            assert max(offsets)-min(offsets)<1e-7
            assert max(latency)<150
            restored=json.loads(report['state']);assert sum(c['title']==report['title'] for c in restored['clips'])==1
        if mode=='play':
            assert len(errors)==35 and max(map(abs,errors))<.080
            assert int(report['after-frame-drop-count'])==0 and int(report['after-decoder-frame-drop-count'])==0
        row['modes'][mode]={'ui_p95_ms':percentile(latency),'ui_max_ms':max(latency) if latency else None,'settle_ms':report['settleMs'],'play_p95_ms':percentile([abs(x)*1000 for x in errors]),'play_max_ms':max(map(abs,errors))*1000 if errors else None,'decoded_seeks':report['completedSeeks']}
    row['hwdec']=report['after-hwdec-current'];matrix.append(row)
before=read('compat-tempo-settled');clip=before['clips'][0]
old_error=abs(before['beatSeconds']['10.0']-clip['resolvedAtStart']['offset']-clip['notes'][0]['nativeSourceAtClipStart'])
assert old_error>.13
tempo=[]
for bpm in (60,240,90):
    state=read(f'compat-static-{bpm}');error=audio_video_error(state)
    assert abs(state['clips'][0]['notes'][0]['nativeSourceAtClipStart']-240/bpm)<1e-7
    tempo.append({'bpm':bpm,'source_error_s':error})
step=read('compat-step-settled');audio_video_error(step)
split=read('compat-step-split');assert split['result']=='PASS';audio_video_error(json.loads(split['splitState']))
before_separate=json.loads(read('compat-h264-move')['state'])['clips'][0]
separated=read('compat-separated-tempo-settled');silent=next(c for c in separated['clips'] if c['type']=='DrA')
assert abs(silent['resolvedAtStart']['offset']-before_separate['resolvedAtStart']['offset'])<1e-8
assert read('compat-separation-undo')['clips'][0]['media']['bound']
final=read('compat-final-reopened');assert len(final['clips'])==5
audio_video_error(final)
for clip in final['clips']:
    assert Path(clip['resolvedAtStart']['file']).is_relative_to(Path(final['root']))
    assert Path(clip['resolvedAtStart']['file']).is_file()
    for note in clip['notes']:
        assert note['getWarpMode']=='RAW' and Path(note['getAudioFile']).is_file()
        assert Path(note['getAudioFile']).is_relative_to(Path(final['root']))
stable={}
for name in ('MpvConnection.java','ScrubCadence.java','DockHost.java'):
    current=ROOT/'video-preview/src/local/bitwig/video'/name
    prior=ROOT/'tests/results/stage3-backup-dev4/video-preview/src/local/bitwig/video'/name
    assert current.read_bytes()==prior.read_bytes(),name
    stable[name]=hashlib.sha256(current.read_bytes()).hexdigest()
cold_before=read('compat-reopened-play');cold_after=read('compat-reopened-play-fixed')
assert cold_after['result']=='PASS'
cold={'before_p95_ms':percentile([abs(e)*1000 for e in cold_before['playbackFrameErrorsSeconds']]),'before_max_ms':max(map(abs,cold_before['playbackFrameErrorsSeconds']))*1000,'after_p95_ms':percentile([abs(e)*1000 for e in cold_after['playbackFrameErrorsSeconds']]),'after_max_ms':max(map(abs,cold_after['playbackFrameErrorsSeconds']))*1000}
report={'result':'PASS','version':'0.3.0-dev.3','old_tempo_source_error_ms':old_error*1000,'fixed_tempo_source_error_s':audio_video_error(final),'cold_playback_correction':cold,'static_tempo_cases':tempo,'matrix':matrix,'assets_pulse_flash_max_ms':max(a['max_pulse_flash_error_seconds'] for a in assets)*1000,'stable_preview_sources':stable,'saved_reopened_project':final['projectFile'],'measurement_limits':['Host model/decoder timestamps; not display-to-soundcard end-to-end latency.','12-second codec fixtures; playback sampled from 0.7 seconds after start for about 3.5 seconds.','Native model editing under UI thread, not OS mouse event injection.','VFR timing tested; adjacent-frame buttons still use nominal FPS.']}
(RESULTS/'compatibility-summary.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('COMPATIBILITY_VERIFIED',json.dumps({k:v for k,v in report.items() if k not in ('matrix','stable_preview_sources')},ensure_ascii=True))

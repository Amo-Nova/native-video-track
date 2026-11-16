"""Assert observed native host outcomes; does not launch or mutate Bitwig."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parent
RESULTS = ROOT / 'results'

def state(name):
    return json.loads((RESULTS / f'native-{name}.txt').read_text(encoding='utf-8'))

def audio_clips(s):
    return [c for track in s['allNativeTracks'] for c in track if c['getTitle'].startswith('AUDIO')]

def clip_named(s, suffix):
    return next(c for c in s['clips'] if c['title'].endswith(suffix))

checks = {}
initial = state('imported')['clips'][0]
assert initial['type'] == 'DyF' and initial['media']['bound']
assert initial['notes'][0]['getChannelCount'] == '2'
assert initial['notes'][0]['getWarpMode'] == 'RAW'
checks['native_stereo_waveform_source'] = True

split = state('split-native')['clips']
assert [(c['getTime'], c['getDuration'], c['getPlayStartOffset']) for c in split] == [(4, 6, 2), (10, 6, 8)]
assert all(c['media']['bound'] and abs(c['resolvedAtStart']['offset'] - 12 / 11) < 1e-9 for c in split)
checks['native_split_preserves_av_and_source_offset'] = True

separated = state('dev2-separated')
assert len(audio_clips(separated)) == 1 and not separated['clips'][0]['media']['bound']
restored = state('dev2-separation-undo')['clips'][0]
assert restored['type'] == 'DyF' and restored['media']['bound'] and restored['getPlayStartOffset'] == 2
checks['separate_and_one_undo_restore_binding'] = True

for name, beat in [('separate-video-moved', 20), ('separate-move-undo', 4), ('separate-move-redo', 20)]:
    s = state(name)
    assert clip_named(s, 'sync-stereo-aac.mp4')['getTime'] == beat
    assert audio_clips(s)[0]['getTime'] == 4
checks['independent_video_move_undo_redo'] = True

for name, bound in [('legacy-bound', True), ('legacy-bind-undo', False)]:
    c = clip_named(state(name), 'sync-delayed-aac.mp4')
    assert c['media']['bound'] == bound
    assert (c['getTime'], c['getDuration'], c['getPlayStartOffset']) == (16002, 12, 2)
checks['legacy_binding_and_undo_preserve_edits'] = True
assert clip_named(state('relink-complete'), 'sync-stereo-aac.mp4')['media']['file'].endswith('clip-b-24fps.mp4')
assert clip_named(state('relink-undo'), 'sync-stereo-aac.mp4')['media']['file'].endswith('sync-stereo-aac.mp4')
checks['relink_undo_restores_original_metadata'] = True

positions = {}
for name, requested in [('start', .1), ('middle', 4052.8721355), ('end', 8104.744271)]:
    s = state(f'large-{name}-settled')
    assert s['playerLoaded'].endswith('large-av-3gb.mp4')
    actual = s['playerPosition']
    assert abs(actual - requested) <= 1 / 30
    positions[name] = {'requested_s': requested, 'actual_s': actual}
checks['large_native_start_middle_end'] = positions
playing = state('large-playing-verified')
assert playing['playing'] and playing['playerPosition'] > 4120
offset = clip_named(playing, 'large-av-3gb.mp4')['resolvedAtStart']['offset']
clock_error = playing['playerPosition'] - (playing['projectSeconds'] - offset)
assert abs(clock_error) < .08
checks['large_native_playback_over_70_seconds'] = True
checks['one_sample_software_clock_error_s'] = clock_error

for name in ['portable-resolved', 'saveas-completed', 'large-reopened-verified']:
    s = state(name)
    project = Path(s['root'])
    for c in s['clips']:
        movie = Path(c['resolvedAtStart']['file'])
        assert movie.is_file() and movie.is_relative_to(project)
        for n in c['notes']:
            sound = Path(n['getAudioFile'])
            assert sound.is_file() and sound.is_relative_to(project)
    checks[name] = True
assert json.loads((RESULTS / 'stage3-native-checks.json').read_text())['portable_original_offline']
checks['original_reference_was_offline_during_portable_open'] = True
reopened = state('large-reopened-verified')
assert abs(reopened['playerPosition'] - 8104.744271) < 1e-6
large = clip_named(reopened, 'large-av-3gb.mp4')['media']['asset']
assert large['bytes'] == 3358281675 and large['audioBytes'] == 1556349056
checks['large_bytes'] = large['bytes']
checks['extracted_large_audio_bytes'] = large['audioBytes']

source = ROOT.parent / 'video-preview/src/local/bitwig/video'
baseline = RESULTS / 'stage3-backup-dev4/video-preview/src/local/bitwig/video'
for name in ['DockHost.java', 'MpvEngine.java', 'MpvConnection.java', 'ScrubCadence.java']:
    assert hashlib.sha256((source / name).read_bytes()).digest() == hashlib.sha256((baseline / name).read_bytes()).digest()
checks['dev4_decode_and_dock_implementation_unchanged'] = True

report = {'version': '0.3.0-dev.2', 'host': 'Windows Bitwig Studio 6.1.7', 'checks': checks,
          'limits': ['Host model/API integration, not a repeated cross-application mouse drop test.',
                     'No measurement of final sound-card/display latency.',
                     'Tempo-map edits, VFR, 4K/long GOP and multi-display DPI are not covered.']}
destination = RESULTS / 'stage3-native-summary.json'
destination.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'NATIVE_STAGE3_CHECKS_OK {destination}')

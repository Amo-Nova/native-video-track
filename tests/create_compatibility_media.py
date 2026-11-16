"""Short, valid codec fixtures with identical visual flashes and audio pulses."""
from pathlib import Path
import os
import json, subprocess, sys, time

ROOT = Path(__file__).resolve().parent
MEDIA = ROOT / 'media' / 'compatibility'
FF = Path(os.environ.get('NVT_FFMPEG','ffmpeg'))
MEDIA.mkdir(parents=True, exist_ok=True)
# Long GOP is deliberate: dragging must not queue every intervening frame.
CASES = [
    ('4k60-h264-aac.mp4', 60, '3840x2160', ['-c:v','libx264','-preset','ultrafast','-crf','26','-threads','4','-g','240','-keyint_min','240','-sc_threshold','0','-bf','3'], ['-c:a','aac','-b:a','192k']),
    ('4k30-hevc10-aac.mp4', 30, '3840x2160', ['-c:v','libx265','-preset','ultrafast','-crf','28','-x265-params','pools=4:frame-threads=2:keyint=120:min-keyint=120:scenecut=0:log-level=error','-pix_fmt','yuv420p10le'], ['-c:a','aac','-b:a','192k']),
    ('4k30-av1-opus.mkv', 30, '3840x2160', ['-c:v','libsvtav1','-preset','12','-crf','34','-g','120','-svtav1-params','lp=4'], ['-c:a','libopus','-b:a','160k']),
    ('1080-vfr-vp9-opus.webm', 60, '1920x1080', ['-c:v','libvpx-vp9','-deadline','realtime','-cpu-used','8','-crf','36','-b:v','0','-g','240','-row-mt','1','-threads','4'], ['-c:a','libopus','-b:a','160k']),
    ('4k24-prores-pcm.mov', 24, '3840x2160', ['-c:v','prores_ks','-profile:v','1','-pix_fmt','yuv422p10le','-threads','4'], ['-c:a','pcm_s24le']),
]

report=[]
for name,fps,size,video,audio in CASES:
    out=MEDIA/name
    if out.exists() and out.stat().st_size==0:out.unlink()
    if not out.exists():
        # Moving texture plus a large white flash at 1, 3, 5, 7, 9, 11 seconds.
        vf="drawbox=x=0:y=0:w=iw:h=ih:color=white:t=fill:enable='lt(mod(t+1,2),0.045)'"
        if 'vfr' in name:
            vf+=",select='if(lt(mod(t,4),2),not(mod(n,2)),not(mod(n,3)))'"
        args=[str(FF),'-hide_banner','-loglevel','warning','-n','-filter_threads','2',
              '-f','lavfi','-i',f'testsrc2=s={size}:r={fps}:d=12',
              '-f','lavfi','-i',r'aevalsrc=exprs=if(lt(mod(t\,2)\,0.04)\,0.6*sin(2*PI*1000*t)\,0)|if(lt(mod(t\,2)\,0.04)\,0.4*sin(2*PI*600*t)\,0):s=48000:d=11',
              '-vf',vf,'-af','adelay=1000:all=1',*video,*audio,'-t','12']
        if 'vfr' in name: args+=['-fps_mode','vfr']
        if out.suffix=='.mp4':args+=['-movflags','+faststart']
        started=time.monotonic()
        pending=out.with_name(out.stem+'.encoding'+out.suffix)
        subprocess.run(args+[str(pending)],check=True)
        pending.replace(out)
        print(f'CREATED {name} {out.stat().st_size} bytes {time.monotonic()-started:.1f}s',flush=True)
    probe=json.loads(subprocess.check_output([str(FF.with_name('ffprobe.exe')),'-v','error','-show_streams','-show_format','-of','json',str(out)]))
    report.append({'name':name,'bytes':out.stat().st_size,'streams':[{k:s.get(k) for k in ['codec_name','codec_type','profile','width','height','pix_fmt','r_frame_rate','avg_frame_rate','sample_rate','channels','start_time','duration']} for s in probe['streams']]})
(MEDIA/'manifest.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print('COMPATIBILITY_FIXTURES_READY',flush=True)

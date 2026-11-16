"""Valid AV sync fixtures; never modifies input media."""
from pathlib import Path
import os
import subprocess, sys

ROOT = Path(__file__).resolve().parent
MEDIA = ROOT / 'media'
FF = Path(os.environ.get('NVT_FFMPEG','ffmpeg'))
MEDIA.mkdir(exist_ok=True)

def make(name, args):
    out = MEDIA / name
    if out.exists():
        print('Preserved', out, out.stat().st_size, flush=True)
        return
    subprocess.run([str(FF), '-hide_banner', '-loglevel', 'warning', '-n', *args, str(out)], check=True)
    print(out, out.stat().st_size, flush=True)

if '--large' in sys.argv:
    # Every byte is real H.264/AAC media, 135 min, exceeding 3 GB and 2 GiB offsets.
    make('large-av-3gb.mp4', ['-stream_loop', '269', '-i', str(MEDIA/'frame-clock-30fps.mp4'),
         '-map', '0', '-c', 'copy', '-movflags', '+faststart'])
else:
    for delayed in (False, True):
        args = ['-f','lavfi','-i',"color=c=0x263944:s=640x360:r=30:d=12",
                '-f','lavfi','-i',"aevalsrc=exprs=if(lt(mod(t\\,2)\\,0.04)\\,0.6*sin(2*PI*1000*t)\\,0)|if(lt(mod(t\\,2)\\,0.04)\\,0.4*sin(2*PI*600*t)\\,0):s=48000:d=11"]
        # Both sound and a white patch begin at 1 s, then 3, 5, 7, 9, 11.
        af = 'asetpts=PTS+1/TB' if delayed else 'adelay=1000:all=1'
        args += ['-vf',"drawbox=x=160:y=80:w=320:h=200:color=white:t=fill:enable='lt(mod(t+1,2),0.04)'",
                 '-af', af, '-c:v','libx264','-preset','ultrafast','-crf','24','-pix_fmt','yuv420p',
                 '-c:a','aac','-b:a','192k','-t','12','-movflags','+faststart']
        make('sync-delayed-aac.mp4' if delayed else 'sync-stereo-aac.mp4', args)

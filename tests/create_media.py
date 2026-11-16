from pathlib import Path
import os, subprocess
root=Path(__file__).resolve().parent
media=root/'media';media.mkdir(exist_ok=True)
ffmpeg=Path(os.environ.get('NVT_FFMPEG','ffmpeg'))
out=media/'frame-clock-30fps.mp4'
if out.exists() and out.stat().st_size>10000:raise SystemExit('Fixture already exists; preserved.')
subprocess.run([str(ffmpeg),'-y','-hide_banner','-loglevel','warning','-f','lavfi','-i','testsrc2=size=960x540:rate=30','-f','lavfi','-i','sine=frequency=1000:sample_rate=48000','-t','30','-vf',"drawtext=fontfile=consola.ttf:text='FRAME %{n}  TIME %{pts\\:hms}':x=30:y=30:fontsize=38:fontcolor=white:box=1:boxcolor=black",'-c:v','libx264','-preset','ultrafast','-crf','28','-pix_fmt','yuv420p','-c:a','aac','-b:a','96k','-movflags','+faststart',str(out)],cwd=Path(os.environ.get('WINDIR','C:/Windows'))/'Fonts',check=True)
print(out)

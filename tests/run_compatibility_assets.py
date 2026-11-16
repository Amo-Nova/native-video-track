"""Production importer, extracted pulse times, and independently decoded flash timestamps."""
from pathlib import Path
import os,subprocess,json,array,struct,re,sys
sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parent.parent
JDK=Path('C:/Program Files/Microsoft/jdk-25.0.2.10-hotspot/bin')
FF=Path(os.environ.get('NVT_FFMPEG','ffmpeg'))
BUILD=Path((ROOT/'last-build.txt').read_text(encoding='utf-8'))
CP=str(BUILD/'test-classes')+';C:/Program Files/Bitwig Studio/bin/libs.jar'
RESULTS=ROOT/'tests/results/compatibility-assets';RESULTS.mkdir(exist_ok=True,parents=True)
subprocess.run([str(JDK/'javac.exe'),'-encoding','UTF-8','-cp',CP,'-d',str(BUILD/'test-classes'),str(ROOT/'tests/MediaAssetsTest.java')],check=True)
reports=[]
for fixture in json.loads((ROOT/'tests/media/compatibility/manifest.json').read_text(encoding='utf-8')):
    name=fixture['name'];source=ROOT/'tests/media/compatibility'/name
    run=subprocess.run([str(JDK/'java.exe'),'-Dfile.encoding=UTF-8','--enable-native-access=ALL-UNNAMED','-Xmx128m','-cp',CP,'local.bitwig.video.MediaAssetsTest',str(ROOT),str(source),'compatibility-assets'],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=120)
    (RESULTS/(name+'.log')).write_text(run.stdout+run.stderr,encoding='utf-8')
    if run.returncode:print(run.stdout+run.stderr);run.check_returncode()
    asset=json.loads(next(line[6:] for line in run.stdout.splitlines() if line.startswith('ASSET ')))
    with (RESULTS/name/asset['audioRelative']).open('rb') as f:
        f.seek(12)
        while True:
            kind,size=struct.unpack('<4sI',f.read(8))
            if kind==b'data':a=array.array('f');a.frombytes(f.read(size));break
            f.seek(size+(size&1),1)
    rate=asset['sampleRate'];channels=asset['channels'];pulses=[];last=-rate
    for i in range(0,len(a),channels):
        if abs(a[i])>.2:
            if i-last>rate*channels*.2:pulses.append(i/channels/rate)
            last=i
    frames=subprocess.run([str(FF),'-hide_banner','-threads','4','-i',str(source),'-an','-vf','scale=1:1:flags=area,format=gray,showinfo','-f','null','-'],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=120)
    frames.check_returncode();(RESULTS/(name+'.frames.log')).write_text(frames.stderr,encoding='utf-8')
    flashes=[];last=-1
    for line in frames.stderr.splitlines():
        m=re.search(r'pts_time:([\d.]+).*mean:\[(\d+)\]',line)
        if m and int(m[2])>245:
            t=float(m[1])
            if t-last>.2:flashes.append(t)
            last=t
    assert len(pulses)==len(flashes)==6,(name,pulses,flashes)
    errors=[a-v for a,v in zip(pulses,flashes)]
    # Independently decoded timestamps, including container origin and codec pre-skip.
    assert max(map(abs,errors))<.051,(name,errors)
    report={**fixture,'asset':asset,'pulses':pulses,'flash_pts':flashes,'max_pulse_flash_error_seconds':max(map(abs,errors)),'seeks':[line for line in run.stdout.splitlines() if line.startswith('SEEK_OK')],'result':'PASS'}
    reports.append(report);(RESULTS/'report.json').write_text(json.dumps(reports,indent=2),encoding='utf-8')
    print(f'PASS {name} pulse/flash max error {max(map(abs,errors))*1000:.3f} ms',flush=True)

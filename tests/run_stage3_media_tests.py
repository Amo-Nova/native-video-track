from pathlib import Path
import subprocess, json, array, struct, sys

ROOT=Path(__file__).resolve().parent.parent
JDK=Path('C:/Program Files/Microsoft/jdk-25.0.2.10-hotspot/bin')
BUILD=Path((ROOT/'last-build.txt').read_text(encoding='utf-8'))
CP=str(BUILD/'test-classes')+';C:/Program Files/Bitwig Studio/bin/libs.jar'
RESULTS=ROOT/'tests/results/stage3-assets'
RESULTS.mkdir(exist_ok=True,parents=True)
subprocess.run([str(JDK/'javac.exe'),'-encoding','UTF-8','-cp',CP,'-d',str(BUILD/'test-classes'),str(ROOT/'tests/MediaAssetsTest.java')],check=True)
reports=[]
for name in ['sync-stereo-aac.mp4','sync-delayed-aac.mp4','frame-clock-30fps.mp4','clip-b-24fps.mp4','large-av-3gb.mp4']:
    run=subprocess.run([str(JDK/'java.exe'),'-Dfile.encoding=UTF-8','--enable-native-access=ALL-UNNAMED','-Xmx128m','-cp',CP,
                        'local.bitwig.video.MediaAssetsTest',str(ROOT),str(ROOT/'tests/media'/name)],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=180)
    (RESULTS/(name+'.log')).write_text(run.stdout+run.stderr,encoding='utf-8')
    if run.returncode: print(run.stdout,run.stderr);run.check_returncode()
    asset=json.loads(next(line[6:] for line in run.stdout.splitlines() if line.startswith('ASSET ')))
    report={'file':name,'asset':asset,'result':'PASS','seeks':[line for line in run.stdout.splitlines() if line.startswith('SEEK_OK')]}
    if name.startswith('sync-'):
        wav=RESULTS/name/asset['audioRelative']
        with wav.open('rb') as f:
            f.seek(12)
            while True:
                kind,size=struct.unpack('<4sI',f.read(8))
                if kind==b'data':
                    a=array.array('f');a.frombytes(f.read(size));break
                f.seek(size+(size&1),1)
        rate=asset['sampleRate']; channels=asset['channels'];starts=[];last=-rate
        for i in range(0,len(a),channels):
            if abs(a[i])>.2:
                if i-last>rate*channels*.2: starts.append(i/channels/rate)
                last=i
        expected=[1,3,5,7,9,11]
        assert len(starts)==len(expected),(name,starts)
        errors=[actual-want for actual,want in zip(starts,expected)]
        assert max(map(abs,errors))<.001,(name,errors)
        report.update(pulse_seconds=starts,max_sync_error_seconds=max(map(abs,errors)),channels=channels)
    reports.append(report)
    print('PASS',name,flush=True)
(RESULTS/'report.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2),encoding='utf-8')

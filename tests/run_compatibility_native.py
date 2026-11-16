"""Sequential acceptance calls; commands never overlap on the host UI thread."""
from pathlib import Path
import subprocess,json,time,sys
sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parent
def run(action,name,**args):
    command=[sys.executable,str(ROOT/'run_native_acceptance.py'),'--action',action,'--name',name]
    for key,value in args.items():command += ['--'+key,str(value)]
    subprocess.run(command,check=True)
    data=(ROOT/f'results/native-{name}.txt').read_text(encoding='utf-8')
    try:report=json.loads(data)
    except ValueError:raise RuntimeError(data)
    if report.get('result')=='FAIL':raise AssertionError(report)
    return report

cases=[('h264','4k60-h264-aac.mp4',10),('hevc','4k30-hevc10-aac.mp4',40),('av1','4k30-av1-opus.mkv',80),('vp9','1080-vfr-vp9-opus.webm',120),('prores','4k24-prores-pcm.mov',160)]
for label,name,beat in cases:
    state=run('snapshot','compat-'+label+'-before')
    if not any(name in c['title'] for c in state['clips']):
        run('import','compat-'+label+'-import',media='compatibility/'+name,beat=beat)
        for attempt in range(20):
            time.sleep(.4);state=run('snapshot','compat-'+label+'-ready')
            if any(name in c['title'] for c in state['clips']):break
        else:raise TimeoutError(name)
    index=next(i for i,c in enumerate(state['clips']) if name in c['title'])
    for mode in ['seek','move','trim','split','play']:
        run('stress-'+mode,'compat-'+label+'-'+mode,index=index)
run('save','compat-matrix-saved')
print('NATIVE_CODEC_MATRIX_COMPLETED',flush=True)

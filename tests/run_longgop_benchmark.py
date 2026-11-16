"""Paired host benchmark with varied release positions and raw exact frame references."""
from pathlib import Path
import argparse,json,subprocess,sys
sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('label',choices=['before','after']);p.add_argument('--rounds',type=int,default=1);a=p.parse_args()
cases=[('h264-fine-a',0,'seek-random',.053),('h264-fine-b',0,'seek-random',.023),('h264-fine-c',0,'seek-random',.073),('h264-move',0,'move',.0),('h264-sweep',0,'sweep',.173),('hevc-fine',1,'seek-random',.053),('av1-fine',2,'seek-random',.053),('vp9-fine',3,'seek-random',.053),('prores-fine',4,'seek-random',.053)]
rows=[]
for round_number,(case,index,mode,offset) in [(r,c) for r in range(1,a.rounds+1) for c in cases]:
    name=f'longgop-{a.label}-{case}'+(f'-r{round_number}' if round_number>1 else '')
    run=subprocess.run([sys.executable,str(ROOT/'run_native_acceptance.py'),'--action','stress-'+mode,'--index',str(index),'--offset',str(offset),'--name',name],capture_output=True,text=True,encoding='utf-8',errors='replace')
    run.check_returncode()
    try:d=json.loads((ROOT/f'results/native-{name}.txt').read_text(encoding='utf-8'))
    except ValueError:raise RuntimeError(run.stdout)
    assert d['result']=='PASS',(name,{k:d.get(k) for k in ('settleMs','finalDecoded','rawExactReference')})
    lat=sorted(d['uiLatencyMs'])
    row={'case':case,'round':round_number,'settle_ms':d['settleMs'],'decoded_seeks':d['completedSeeks'],'reused_frames':d.get('reusedFrames',0),'stepped_frames':d.get('steppedFrames',0),'ui_p95_ms':lat[int((len(lat)-1)*.95)],'source_target':d['finalExpected'],'project_target':d.get('requestedProjectSeconds'),'frame':d['finalDecoded'],'raw_exact_frame':d['rawExactReference']}
    rows.append(row);print(json.dumps(row),flush=True)
    (ROOT/f'results/longgop-{a.label}.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
print('LONG_GOP_BENCHMARK_PASS '+a.label,flush=True)

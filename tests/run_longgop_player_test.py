from pathlib import Path
import subprocess,sys
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parent.parent
build=Path((root/'last-build.txt').read_text(encoding='utf-8'))
jdk=Path('C:/Program Files/Microsoft/jdk-25.0.2.10-hotspot/bin')
cp=str(build/'test-classes')+';C:/Program Files/Bitwig Studio/bin/libs.jar'
tests=['MpvExactReuseTest','MpvForwardStepTest','MpvPipeTest','MpvFinalRetargetTest']
subprocess.run([str(jdk/'javac.exe'),'--release','21','-encoding','UTF-8','-cp',cp,'-d',str(build/'test-classes'),*[str(root/'tests'/f'{t}.java') for t in tests]],check=True)
for test in (sys.argv[1:] or tests):
    assert test in tests
    run=subprocess.run([str(jdk/'java.exe'),'--enable-native-access=ALL-UNNAMED','-cp',cp,'local.bitwig.video.'+test,str(root)],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=180)
    (root/'tests/results'/f'{test}.log').write_text(run.stdout+run.stderr,encoding='utf-8')
    print(run.stdout+run.stderr,flush=True);run.check_returncode()

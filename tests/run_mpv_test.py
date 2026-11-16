from pathlib import Path
import subprocess,sys
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parent.parent
build=Path((root/'last-build.txt').read_text(encoding='utf-8'))
jdk=Path('C:/Program Files/Microsoft/jdk-25.0.2.10-hotspot/bin')
cp=str(build/'test-classes')+';C:/Program Files/Bitwig Studio/bin/libs.jar'
subprocess.run([str(jdk/'javac.exe'),'-J-Duser.language=en','--release','21','-encoding','UTF-8','-cp',cp,'-d',str(build/'test-classes'),str(root/'tests/MpvSmokeTest.java'),str(root/'tests/MpvScrubTest.java'),str(root/'tests/MpvFineScrubTest.java'),str(root/'tests/TransportTimeTest.java')],check=True)
subprocess.run([str(jdk/'java.exe'),'-Duser.language=en','-cp',cp,'local.bitwig.video.TransportTimeTest'],check=True)
subprocess.run([str(jdk/'java.exe'),'--enable-native-access=ALL-UNNAMED','-Duser.language=en','-cp',cp,'local.bitwig.video.MpvSmokeTest',str(root/'vendor/mpv'),str(root/'tests/media/frame-clock-30fps.mp4'),str(root/'tests/results')],check=True,timeout=45)
subprocess.run([str(jdk/'java.exe'),'--enable-native-access=ALL-UNNAMED','-Duser.language=en','-cp',cp,'local.bitwig.video.MpvScrubTest',str(root/'vendor/mpv'),str(root/'tests/media'),str(root/'tests/results')],check=True,timeout=45)
subprocess.run([str(jdk/'java.exe'),'--enable-native-access=ALL-UNNAMED','-Duser.language=en','-cp',cp,'local.bitwig.video.MpvFineScrubTest',str(root/'vendor/mpv'),str(root/'tests/media'),str(root/'tests/results')],check=True,timeout=45)

"""Prepare the latest candidate without replacing files used by a live host."""
from pathlib import Path
import shutil, subprocess, sys
sys.stdout.reconfigure(encoding='utf-8')
root=Path(__file__).resolve().parent.parent
vendor=root/'vendor/Patchbay-0.36.0-dev.14/BML-0.36.0-dev.14-windows'
build=Path((root/'last-build.txt').read_text(encoding='utf-8'))
candidate=build/'prepared-candidate'
shutil.copytree(vendor/'loader',candidate/'loader',dirs_exist_ok=True)
shutil.copytree(vendor/'maps',candidate/'maps',dirs_exist_ok=True)
for name in ['action-menu-composition-framework','panel-management-framework','preferences-composition-framework']:
    shutil.copytree(vendor/'mods'/name,candidate/'mods'/name,dirs_exist_ok=True)
shutil.copytree(root/'out/video-preview',candidate/'mods/video-preview',dirs_exist_ok=True)
command=['C:/Program Files/Microsoft/jdk-25.0.2.10-hotspot/bin/java.exe','-Dfile.encoding=UTF-8','-Duser.language=en','-cp',str(candidate/'loader/app/bml-control.jar'),'dev.bitwigre.bml.JavaPreparer','--loader-root',str(candidate/'loader'),'--bitwig-bin','C:/Program Files/Bitwig Studio/bin','--mods-root',str(candidate/'mods')]
result=subprocess.run(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
(build/'prepare-candidate.log').write_text(result.stdout,encoding='utf-8')
print(result.stdout)
if result.returncode:raise SystemExit(result.returncode)
print('PREPARED_CANDIDATE',candidate)

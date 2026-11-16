"""Test isolated player provisioning; --online also downloads the pinned upstream archive."""
from pathlib import Path
import argparse
import datetime
import os
import subprocess
from build_source import ROOT, config


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, default=ROOT / 'build.config.local.json')
    parser.add_argument('--online', action='store_true')
    args = parser.parse_args()
    paths = config(args.config.resolve())
    build = ROOT / 'build' / ('player-' + datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    classes = build / 'classes'; classes.mkdir(parents=True)
    env = os.environ.copy(); env.pop('JAVA_TOOL_OPTIONS', None)
    subprocess.run([str(paths['java_home'] / 'bin/javac.exe'), '--release', '21', '-encoding', 'UTF-8', '-d', str(classes),
        str(ROOT / 'video-preview/src/local/bitwig/video/PlayerRuntime.java'), str(ROOT / 'tests/PlayerRuntimeTest.java')], check=True, env=env)
    modes = ('unit', 'online') if args.online else ('unit',)
    for mode in modes:
        # Inherit stdout so a slow real download continues to display progress.
        command = [str(paths['java_home'] / 'bin/java.exe'), '-Dfile.encoding=UTF-8', '-cp', str(classes),
            'local.bitwig.video.PlayerRuntimeTest', mode, str(build / mode)]
        with (build / (mode + '.log')).open('w', encoding='utf-8') as log:
            process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace', env=env)
            for line in process.stdout:
                print(line, end='', flush=True); log.write(line); log.flush()
            if process.wait() != 0:
                raise subprocess.CalledProcessError(process.returncode, command)
    print('PLAYER_ACCEPTANCE_RECORD', build)


if __name__ == '__main__':
    main()

Native Video Track downloads its video player on first use.

The public Launcher ZIP contains no mpv, FFmpeg, libplacebo or Microsoft DLL
binaries. The MOD obtains the unchanged, pinned Windows x64 mpv build directly
from shinchiro's official GitHub Release, verifies the archive and each of the
three player files, and caches them for offline use at:
  %LOCALAPPDATA%/BitwigVideo/player/20261111-bcde6b93cc/

First use needs access to GitHub and Windows tar.exe.
Downloading uses HTTPS from the Java runtime already supplied by Bitwig.
Downloading and extraction run on a background worker. Existing clips are not
changed by a failed setup. Check the connection and repeat the video operation
to retry. MOD updates do not delete the player cache or project media.

Pinned upstream archive:
https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/20261111/mpv-x86_64-20261111-git-bcde6b93cc.7z
SHA-256: f259b5fd4b5c395617c948a4fadcdf55bba1c5282fb7c13e88626b3f71072baf

Only mpv.exe, mpv.com and d3dcompiler_43.dll are extracted. No bundled upstream
installer, registration or updater script is run. The codec build is the same
one used in the project's recorded Windows tests.

Upstream license information:
https://github.com/mpv-player/mpv/blob/bcde6b93cc/Copyright
https://github.com/shinchiro/mpv-winbuild-cmake/releases/tag/20261111
Project credits: https://github.com/Amo-Nova/native-video-track/blob/main/CREDITS.txt

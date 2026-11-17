This directory is the optional mpv resource for the macOS edition.
No player binary is included or automatically downloaded.

Player lookup order:
1. BITWIG_VIDEO_MPV, if set to an existing executable
2. mpv in this resource directory
3. /opt/homebrew/bin/mpv
4. /usr/local/bin/mpv
5. mpv on PATH

The normal setup is: brew install mpv
Homebrew's executable depends on its installed libraries; copying only that
executable to a different machine does not provide a complete installation.
The resolver can restore the executable permission on a supplied resource.

macOS testing and contributions: AUPhui (https://github.com/AUPhui).
Full project attribution and license are in CREDITS.txt and LICENSE.

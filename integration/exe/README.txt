Native Video Track - single-file Windows installer source 1.0.0-dev.3
Integration 1.0.0-dev.1 / Video MOD 0.5.0-dev.3

Target: Windows x64 and the exact tested Bitwig Studio 6.1.7 build.
The target computer needs Bitwig, Windows .NET Framework and PowerShell.
Python, a development JDK and a Patchbay development folder are not required.
The installer UI currently uses Chinese labels; this is English documentation,
not an English-localized executable.

1. Copy the locally built Native-Video-Track-Setup-1.0.0-dev.3-Windows-x64.exe to the target PC.
2. Save projects and close Bitwig normally.
3. Open the EXE and verify/browse to the Bitwig installation directory.
4. Choose Install / Repair and accept administrator access if requested.
5. After success, use the ordinary Bitwig icon or .bwproject file.

The same EXE checks status, repairs components and restores the original host.
Restore preserves projects, media and layout settings. Backups and independent
recovery tools are under the host's bin/bitwig-video directory. Runtime does
not depend on the EXE's location. Transfer the entire collected project folder
when moving a project between computers.

The host installation path must be ASCII. Media/project names and the EXE's
own location may use Unicode. A running host or unknown version causes a
reported refusal; the installer does not terminate the host or force an update.

This development EXE is unsigned. Local management policies may require an
administrator. It is not a macOS installer; see MAC_COMPATIBILITY.md.

Credits are available in the installer and CREDITS.txt.
Logs: %LOCALAPPDATA%/BitwigVideo/installer-logs

Maintainer commands:
--headless --action Status --root <Bitwig-directory> --report <result-json>
--headless --action Verify --report <result-json>
--headless --action Install/Restore uses the same core and elevates as needed.
Tests can use an isolated --data-root and --skip-association to avoid changing
the real project-file association. Inspect all target paths before testing.

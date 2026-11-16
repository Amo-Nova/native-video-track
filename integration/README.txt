Native Video Track persistent integration 1.0.0-dev.1
Video MOD 0.5.0-dev.3 / Windows x64 / exact Bitwig Studio 6.1.7

These instructions apply to the generated integration package. Build it using
the source root's BUILDING.md; the source archive itself contains no payload.

INSTALL: Save projects and close Bitwig normally. Double-click Install.cmd.
Accept Windows administrator access if requested. After installation, use the
ordinary Bitwig icon or open a .bwproject file; no special video launcher is
required. Run Install.cmd again to repair missing component files.

STATUS: Status.cmd reports installation health without changing files.

RESTORE: Close Bitwig and run Restore.cmd. It restores the verified original
bitwig.jar and removes unchanged component files belonging to this package.
Original backups, recovery tools, settings, projects and media remain.
A separate recovery copy is in Bitwig's bin/bitwig-video/Recovery directory.

The host installation path must be ASCII because of the tested native
launcher's path behavior. Project and media filenames are not restricted to
ASCII. Unknown host versions are rejected. Official updates or repairs may
replace the integration, requiring a separately verified compatible package.

If no valid .bwproject default association or UserChoice exists, installation
can add the current user's open command. Existing defaults and later changes
are preserved. The EXE wrapper performs this in the initiating user's context.

Settings and logs: %LOCALAPPDATA%/BitwigVideo
Only an original settings.properties file should be supplied to the
PowerShell installer's -SettingsSource parameter. Existing settings are kept.

Ordinary start-menu, taskbar and project-file cold starts, multi-video save,
relocated reopen, partial recovery and edit/decode regression were tested on
the development machine. See VALIDATION_PERSISTENT_INSTALL.md for scope.

This is third-party local integration, not an official Bitwig component API.
The source archive does not contain Bitwig or pre-generated host classes.
Preserve upstream notices and review the root LICENSE_STATUS.md before a
public release. Runtime controls retain their current Chinese UI strings.

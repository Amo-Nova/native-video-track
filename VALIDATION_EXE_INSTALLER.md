# Single-file Windows installer validation

Date: 2026-11-16. Setup 1.0.0-dev.2 / Integration 1.0.0-dev.1 / Video 0.5.0-dev.2.

The WinForms/.NET Framework EXE embeds the existing installation payload and PowerShell scripts. Target machines need neither Python nor a development JDK/Patchbay tree. The UI offers install/repair, status, restore, logs and credits. Startup/status are not elevated; a worker elevates when the target requires it.

The embedded payload is checked by whole-payload SHA-256, followed by internal manifest/file checks. Extraction uses scoped/reparse-point checks and cleans temporary state. The EXE invokes the core with `-SkipProjectAssociation`, then completes associations in the original user's context so an alternate-admin worker does not modify the wrong HKCU.

## Automated acceptance

`integration/exe/test_exe.py` passed using isolated detection/install fixtures and original JAR backup. Fixtures were not represented as full host runtime tests.

1. Standalone EXE execution from Unicode, space and apostrophe paths; payload verification and status.
2. Refusal while a controlled target-named process was running, without terminating it or changing the JAR.
3. First installation, backup identity and healthy status, including spaces, apostrophes and dollar signs in host test paths without command interpretation.
4. Repeated installation without replacing the active integrated JAR.
5. Missing-component detection and repair without JAR replacement.
6. Recovery of a simulated pending transaction through the EXE.
7. Restore/repeated restore with exact original hash and preserved settings.
8. Install/restore refusal for unknown or upgraded JARs, with unchanged files.
9. Readable failure for a nonexistent host.
10. Refusal of deliberately corrupted embedded payload, without host changes.
11. Unchanged production-host JAR throughout the isolated tests.

Ten project-association checks passed in an isolated HKCU tree, covering initial/repeated/interrupted setup, restoration, existing defaults, UserChoice and later changes.

Evidence: `integration/exe/tests/20261116-090040/results.json` and `integration/tests/Test-ProjectAssociation.ps1`. Raw outputs are not bundled.

## UI and limits

The final EXE's Chinese text, main buttons, directory selection and credits were inspected and were readable. It detected the installed component as healthy. That UI check performed status/credits only, not production installation or restoration.

Full interactive standard-user UAC, alternate-admin credentials and cancellation behavior remain untested despite implemented code paths. No second physical Windows PC was used; portability evidence is standalone execution from an isolated local directory without adjacent dependencies. Exact Windows x64 Bitwig 6.1.7 remains the supported target. The EXE is unsigned and no Mac executable is available.

Video core source, player and runtime payload behavior were unchanged for this packaging stage. The full codec/editing matrix was not repeated. The English source handoff changes documentation, not the installer's runtime language or these historical results.

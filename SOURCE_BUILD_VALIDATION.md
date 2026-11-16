# Clean source build validation

Date: 2026-11-16. Environment: Windows x64, Python 3.14, Microsoft JDK 25.0.2 and an unmodified Bitwig Studio 6.1.7 SDK with its bundled JRE.

The candidate ZIP was extracted into a fresh directory. All 124 manifest-listed files were verified before running:

```text
python tools/build_source.py all
```

| Stage | Result |
|---|---|
| Dependency and original-host hashes | Passed |
| Java video MOD compilation | Passed; all 22 core Java files match the project |
| Patchbay modkit finish | Passed: 10 hooks, 5 non-public accesses, 18 rewritten classes |
| Isolated preparation and deep verification | `PREPARE_OK`: 4 MODs, 64 transforms, 49 replacement classes |
| Persistent-integration packaging | Passed: 49 replacement classes, 196 overlay entries, 38 component files |
| C# EXE compilation | Passed |
| Transport rules | `TRANSPORT_TIME_OK`: pause, locate, loop and project switching |
| Embedded EXE payload | `SOURCE_BUILD_VERIFY_OK` |
| Source files unchanged by the build | Passed |
| Installed Bitwig JAR unchanged | Passed |

The validation build produced a 54,013,440-byte EXE with SHA-256:

```text
706485ba25acfa1f25d48958b0ad961f3ba25e21eab81d1d0b96b4986137da14
```

This identifies that particular build, not a reproducible-binary promise; archive timestamps and other build details can change the result. The source ZIP does not contain the EXE.

The r2 handoff revises delivery documentation and adds English documentation. Production source, build scripts and test code are compared byte-for-byte with the successfully built candidate. Documentation changes are not treated as another runtime acceptance pass. The adjacent `.sha256` and `source-release.json` identify the final archive; `SOURCE_MANIFEST.json` identifies individual files.

No installation, host launch or working-project automation was performed for source verification. The full 4K, long-GOP, large-file and editing matrix was not repeated for this documentation revision. Those earlier results and limits are summarized in `VALIDATION_*.md`. Windows 6.1.0 and macOS remain unadapted and untested.

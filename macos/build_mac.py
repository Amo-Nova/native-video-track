#!/usr/bin/env python3
"""Build the video-preview mod for macOS arm64 / Bitwig 6.1.7 against the local
Bitwig install, mirroring Patchbay tools/build_mod.py (kit path) and
package_patchbay_release.py (release layout). Writes nothing outside this tree
except the compile jar cache in build/.

  python3 build_mac.py            build + package out/video-preview-<version>-macos.zip
"""
from __future__ import annotations
import hashlib, io, json, os, shutil, subprocess, sys, tempfile, zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
MOD_DIR = HERE / "video-preview"
PATCHBAY = Path.home() / "Downloads" / "BML-0.36.0-dev.16-macos"
# compile/finish/uses always read the PRISTINE jar: once the persistent overlay is installed,
# the live bitwig.jar contains the merged helper (bml/api included), and compile-jar would
# rewrite those classes and drop the @Hook annotation defaults.
BITWIG_JAVA = HERE / "pristine-bin"
BITWIG = BITWIG_JAVA / "bitwig.jar"
LIBS = BITWIG_JAVA / "libs.jar"
JDK = Path(subprocess.run(["/usr/libexec/java_home"], capture_output=True, text=True,
                          check=True).stdout.strip()) / "bin"
sys.path.insert(0, str(HERE.parent / "patchbay-src" / "tools"))
import mod_uses  # noqa: E402
import build_mod  # noqa: E402  (kit_manifest / release_files logic, imported for reuse)

FIXED_DATE = "2026-01-01T00:00:00Z"
FIXED_ZIP_TIME = (2026, 1, 1, 0, 0, 0)
MOD = "video-preview"


def sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def run(cmd: list[str], **kw) -> subprocess.CompletedProcess:
    p = subprocess.run([str(c) for c in cmd], capture_output=True, text=True, errors="replace", **kw)
    if p.returncode:
        sys.exit(f"failed: {[str(c) for c in cmd]}\n{p.stdout}{p.stderr}")
    return p


def main() -> int:
    manifest = json.loads((MOD_DIR / "mod.json").read_text("utf8"))
    build = json.loads((MOD_DIR / "build.json").read_text("utf8"))
    version = manifest["mod"]["version"]
    tmp = Path(tempfile.mkdtemp(prefix="vp-mac-"))
    try:
        # 1. compile jar from the pristine local Mac jar (never shipped)
        compile_jar = tmp / "compile.jar"
        run([JDK / "java", "-jar", PATCHBAY / "modkit" / "bml-modkit.jar",
             "compile-jar", BITWIG, compile_jar])

        # 2. classpath: compile jar, libs.jar (Gson/JNA), bml-api.jar, classpath mods
        cp = [compile_jar, LIBS, PATCHBAY / "modkit" / "bml-api.jar"]
        for dep in build.get("classpath_mods", []):
            dep, _, acc = dep.partition("@")
            cp.append(PATCHBAY / "mods" / dep / "helper.jar")
        src = MOD_DIR / "src"
        classes = tmp / "classes"
        classes.mkdir()

        # 3. javac (one release group, as build_mod does per release)
        args_file = tmp / "sources.txt"
        args_file.write_text("\n".join(f'"{f.as_posix()}"' for f in sorted(src.rglob("*.java"))))
        run([JDK / "javac", "--release", str(build["release"]), "-nowarn", "-proc:none",
             "-encoding", "UTF-8", "-implicit:none", "-sourcepath", str(src),
             "-cp", ":".join(str(p) for p in cp), "-d", str(classes), f"@{args_file}"])

        # 4. modkit finish: check every @Hook against the real jar, rewrite bitwig.obf
        hooks_json = tmp / "hooks.json"
        others = [c for c in cp[1:] if not str(c).endswith("bml-api.jar")]
        p = run([JDK / "java", "-jar", PATCHBAY / "modkit" / "bml-modkit.jar", "finish",
                 str(classes), str(hooks_json), str(BITWIG)] + [str(o) for o in others])
        print(p.stdout.strip())
        found = json.loads(hooks_json.read_text("utf8"))
        for f in sorted(src.rglob("*")):
            if f.is_file() and f.suffix != ".java":
                out = classes / f.relative_to(src)
                out.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(f, out)
        jar = tmp / "helper.jar"
        run([JDK / "jar", "--create", "--file", str(jar), f"--date={FIXED_DATE}", "-C", str(classes), "."])
        helper = jar.read_bytes()

        # 5. shipped manifest: declared mod.json + one hook per @Hook + opens
        shipped_manifest = build_mod.kit_manifest(MOD, manifest, found)

        # 6. uses.tsv in the names of the build this helper was compiled against
        uses_rows = mod_uses.with_fields(mod_uses.uses(helper, BITWIG), json.loads(shipped_manifest))
        print(f"uses.tsv rows: {len(uses_rows)}")

        # 7. release folder contents (official layout: package/ at root, resources shipped)
        files: dict[str, bytes] = {
            "mod.json": shipped_manifest,
            "helper.jar": helper,
            "uses.tsv": "".join(r + "\n" for r in uses_rows).encode(),
            "README.md": (MOD_DIR / "README.md").read_bytes(),
            "LICENSE": (HERE / "LICENSE").read_bytes(),
            "CREDITS.txt": (HERE / "CREDITS.txt").read_bytes(),
        }
        for name in ("actions.properties", "THIRD_PARTY.md", "video.svg"):
            files[name] = (MOD_DIR / "package" / name).read_bytes()
        for f in sorted((MOD_DIR / "player").rglob("*")):
            if f.is_file():
                files["player/" + f.relative_to(MOD_DIR / "player").as_posix()] = f.read_bytes()
        files["logs/.keep"] = (MOD_DIR / "logs" / ".keep").read_bytes()
        files["build.json"] = (MOD_DIR / "build.json").read_bytes()
        for f in sorted(src.rglob("*")):
            if f.is_file():
                files["src/" + f.relative_to(src).as_posix()] = f.read_bytes()
        files["SHA256SUMS.txt"] = "".join(f"{sha(b)}  {n}\n" for n, b in sorted(files.items())).encode()

        out = HERE / "out"
        out.mkdir(exist_ok=True)
        zip_path = out / f"{MOD}-{version}-macos.zip"
        with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
            for n in sorted(files):
                info = zipfile.ZipInfo(f"{MOD}/{n}", FIXED_ZIP_TIME)
                info.compress_type = zipfile.ZIP_DEFLATED
                # keep the bundled player binary executable when Patchbay unpacks it
                if n == "player/mpv":
                    info.external_attr = 0o755 << 16
                z.writestr(info, files[n])
        unpack = out / "check-mods" / MOD
        if unpack.exists():
            shutil.rmtree(unpack)
        unpack.mkdir(parents=True)
        for n, b in files.items():
            path = unpack / n
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b)
            if n == "player/mpv":
                path.chmod(0o755)
        print(f"wrote {zip_path} ({zip_path.stat().st_size} bytes), {len(files)} files")
        print(f"unpacked for the offline check: {unpack}")
        print(f"hooks: {len(found['hooks'])}, opens: {len(found.get('access', []))}, "
              f"fields: {len(found.get('fields', []))}")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())

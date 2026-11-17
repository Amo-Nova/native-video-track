#!/usr/bin/env python3
"""Persistent Bitwig Video install for macOS arm64 / Bitwig 6.1.7.

Mirrors NativeVideoTrack integration/build_package.py + Install-BitwigVideo.ps1:
bakes the prepared replacement classes and the merged helper into bitwig.jar,
adds local/bitwig/video/install/InstalledRuntime as the bootstrap, and installs
the mod's component files (mods/<id>/...) beside the jar. The original jar is
backed up by SHA-256; nothing is edited in place; the app bundle is re-signed
ad-hoc after the jar changes.

  python3 persistent_install.py build     build package/ from the prepared runtime
  python3 persistent_install.py verify    offline InstalledRuntime diagnosis (staged copy)
  python3 persistent_install.py install   backup + replace jar + component + re-sign
  python3 persistent_install.py status    what is installed
  python3 persistent_install.py restore   put the original jar back and re-sign
"""
from __future__ import annotations
import hashlib, json, os, shutil, struct, subprocess, sys, tempfile, zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
MAC_MOD = HERE.parent
RUNTIME = MAC_MOD / "bml-check/loader/mod-runtime"          # prepared for macos-6.1.7
CHECK_MODS = MAC_MOD / "out/check-mods"                       # video-preview + action-menu framework
BML_ROOT = Path.home() / "Downloads/BML-0.36.0-dev.16-macos"
STOCK = Path("/Applications/Bitwig Studio.app/Contents/Java/bitwig.jar")
APP = Path("/Applications/Bitwig Studio.app")
ENTITLEMENTS = APP / "Contents/Resources/BitwigStudio.entitlements"
# build inputs always read the pristine backup: the live jar may be a previous overlay
PRISTINE = MAC_MOD / "pristine-bin/bitwig.jar"
EXPECTED_SHA = "6024752847797eb41e6fbc91e081211937931cc452a695ab78226321cefdac8d"
INTEGRATION_VERSION = "1.0.0-dev.3"
VIDEO_VERSION = "0.5.0-dev.14"
JDK = Path(subprocess.run(["/usr/libexec/java_home"], capture_output=True, text=True,
                          check=True).stdout.strip()) / "bin"
PKG = HERE / "package"
USER_DATA = Path.home() / "Library/Application Support/BitwigVideo"


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def redirect_getenv(data: bytes) -> tuple[bytes, int]:
    """Change only System.getenv(String)'s Methodref owner; no code or stack-map rewriting."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("Not a class")
    count = struct.unpack_from(">H", data, 8)[0]
    at = 10
    i = 1
    cp = {}
    positions = {}
    while i < count:
        tag = data[at]
        positions[i] = at
        at += 1
        if tag == 1:
            n = struct.unpack_from(">H", data, at)[0]
            at += 2
            cp[i] = (tag, data[at:at + n].decode("utf-8", errors="replace"))
            at += n
        elif tag in (7, 8, 16, 19, 20):
            cp[i] = (tag, struct.unpack_from(">H", data, at)[0])
            at += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            cp[i] = (tag, *struct.unpack_from(">HH", data, at))
            at += 4
        elif tag in (3, 4):
            at += 4
        elif tag in (5, 6):
            at += 8
            i += 1
        elif tag == 15:
            at += 3
        else:
            raise ValueError(f"Unknown constant pool tag {tag}")
        i += 1
    refs = []
    for index, item in cp.items():
        if item[0] == 10 and cp[cp[item[1]][1]][1] == "java/lang/System":
            nt = cp[item[2]]
            if cp[nt[1]][1] == "getenv" and cp[nt[2]][1] == "(Ljava/lang/String;)Ljava/lang/String;":
                refs.append(index)
    if not refs:
        return data, 0
    owner = b"local/bitwig/video/install/InstalledRuntime"
    prefix = bytearray(data[:at])
    struct.pack_into(">H", prefix, 8, count + 2)
    for index in refs:
        struct.pack_into(">H", prefix, positions[index] + 1, count + 1)
    suffix = b"\x01" + struct.pack(">H", len(owner)) + owner + b"\x07" + struct.pack(">H", count)
    return bytes(prefix) + suffix + data[at:], len(refs)


def rewritten_bindings() -> bytes:
    """The prepared bindings with every path moved to the installed layout."""
    text = (RUNTIME / "mod-bindings.properties").read_text("utf8")
    component_base = str(CHECK_MODS)                    # .../out/check-mods
    fake_bml = str(MAC_MOD / "fake-home/Library/Application Support/BML")
    component_uri = (CHECK_MODS).as_uri()
    fake_uri = (MAC_MOD / "fake-home/Library/Application Support/BML").as_uri()
    out = []
    for line in text.splitlines():
        if "=" not in line or line.startswith("#"):
            out.append(line)
            continue
        key, value = line.split("=", 1)
        if key == "loader.components.path":
            value = "${user.data}/components"
        else:
            value = value.replace(fake_bml, "${user.data}") \
                         .replace(component_base, "${component.root}/mods")
            if key.endswith(".uri"):
                value = value.replace(fake_uri, "${user.uri}") \
                             .replace(component_uri, "${component.uri}/mods")
        if "${" in value and "component.root}" not in value and "user.data}" not in value \
                and "component.uri}" not in value and "user.uri}" not in value:
            raise SystemExit(f"unrewritten path in {key}: {value}")
        out.append(f"{key}={value}")
    props = "\n".join(out) + "\n"
    props.encode("ascii")  # must stay ASCII like the original
    return props.encode("ascii")


def build() -> int:
    if file_sha(PRISTINE) != EXPECTED_SHA:
        raise SystemExit(f"unexpected pristine jar sha {file_sha(PRISTINE)}")
    PKG.mkdir(parents=True, exist_ok=True)
    classes = HERE / "build/classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    subprocess.run([str(JDK / "javac"), "--release", "21", "-encoding", "UTF-8",
                    "-d", str(classes), str(HERE / "InstalledRuntime.java")], check=True)

    overlay: dict[str, bytes] = {}
    replaced = []
    with zipfile.ZipFile(PRISTINE) as original:
        names = set(original.namelist())
        if any(n.upper().endswith((".SF", ".RSA", ".DSA", ".EC")) and n.upper().startswith("META-INF/")
               for n in names):
            raise SystemExit("stock jar carries signature files; refusing")
        for line in (RUNTIME / "replacement-map.tsv").read_text().splitlines():
            name, path = line.split("\t")
            entry = name.replace(".", "/") + ".class"
            if entry not in names or entry in overlay:
                raise SystemExit(f"replacement not applicable: {entry}")
            overlay[entry] = Path(path).read_bytes()
            replaced.append({"path": entry, "originalSha256": sha(original.read(entry))})
        redirected = {}
        with zipfile.ZipFile(RUNTIME / "helper.jar") as helper:
            for item in helper.infolist():
                if item.is_dir() or item.filename.upper() == "META-INF/MANIFEST.MF":
                    continue
                if item.filename in names or item.filename in overlay:
                    raise SystemExit(f"helper clash: {item.filename}")
                data = helper.read(item)
                if item.filename.endswith(".class"):
                    data, count = redirect_getenv(data)
                    if count:
                        redirected[item.filename] = count
                overlay[item.filename] = data
    for f in classes.rglob("*.class"):
        overlay[f.relative_to(classes).as_posix()] = f.read_bytes()
    overlay["bitwig-video-install/bindings.properties"] = rewritten_bindings()
    overlay["bitwig-video-install/version.txt"] = (INTEGRATION_VERSION + "\n").encode()

    # the new jar: every stock entry, replacements applied, overlay appended
    jar_path = PKG / "bitwig-video.jar"
    with zipfile.ZipFile(STOCK) as original, \
            zipfile.ZipFile(jar_path, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as out:
        for info in original.infolist():
            if info.filename in overlay:
                out.writestr(info.filename, overlay.pop(info.filename))
            else:
                out.writestr(info, original.read(info.filename))
        for name in sorted(overlay):
            out.writestr(name, overlay.pop(name))
    original_hashes = {x["path"]: x["originalSha256"] for x in replaced}
    overlay_items = [{"path": n, "sha256": sha(None if False else b"")} for n in []]  # placeholder
    # recompute the manifest from the produced jar
    with zipfile.ZipFile(jar_path) as z:
        overlay_items = []
        for info in z.infolist():
            if info.filename in original_hashes or info.filename in {
                    "bitwig-video-install/bindings.properties", "bitwig-video-install/version.txt"} \
                    or info.filename.startswith(("local/", "bml/", "dev/bitwigre/", "bitwig/")):
                overlay_items.append({"path": info.filename, "sha256": sha(z.read(info.filename)),
                                      "originalSha256": original_hashes.get(info.filename)})

    # component package: the mod folders the bindings point into (component root + mods/<id>)
    component_files = {}
    for mod in ("video-preview", "action-menu-composition-framework"):
        for f in (CHECK_MODS / mod).rglob("*"):
            if not f.is_file():
                continue
            relative = f.relative_to(CHECK_MODS)
            if "src" in relative.parts:
                continue
            component_files["mods/" + relative.as_posix()] = f.read_bytes()
    for name in ("LICENSE", "THIRD_PARTY_NOTICES.md"):
        component_files["licenses/Patchbay-" + name] = (BML_ROOT / name).read_bytes()
    component_files["component.json"] = json.dumps({
        "integrationVersion": INTEGRATION_VERSION, "videoVersion": VIDEO_VERSION,
        "bitwigVersion": "6.1.7", "platform": "macos", "architecture": "arm64"}, indent=2).encode()
    component_zip = PKG / "component.zip"
    component_items = []
    with zipfile.ZipFile(component_zip, "w", zipfile.ZIP_DEFLATED, compresslevel=3) as z:
        for name, data in sorted(component_files.items()):
            z.writestr(name, data)
            component_items.append({"path": name, "sha256": sha(data)})

    manifest = {
        "format": 1, "platform": "macos", "integrationVersion": INTEGRATION_VERSION,
        "videoVersion": VIDEO_VERSION, "bitwigVersion": "6.1.7",
        "originalSha256": EXPECTED_SHA,
        "overlaySha256": file_sha(jar_path), "componentSha256": file_sha(component_zip),
        "overlay": overlay_items, "components": component_items, "redirectedClasses": redirected,
        "replacedClasses": replaced,
    }
    (PKG / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"package": str(PKG), "replacements": len(replaced),
                      "redirected": redirected, "componentFiles": len(component_items),
                      "jar": str(jar_path), "jarSha256": file_sha(jar_path)}, indent=2))
    return 0


def staged_component_root() -> Path:
    return PKG / "bitwig-video/releases" / INTEGRATION_VERSION


def verify() -> int:
    """Offline diagnosis with the staged jar: InstalledRuntime initializes against package/."""
    root = staged_component_root()
    if not (root / "component.json").is_file():
        root.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(PKG / "component.zip") as z:
            z.extractall(root)
    env = dict(os.environ)
    scratch = PKG / "scratch-data"
    scratch.mkdir(parents=True, exist_ok=True)
    env["BITWIG_VIDEO_DATA"] = str(scratch)
    p = subprocess.run([str(JDK / "java"), "-cp", str(PKG / "bitwig-video.jar"),
                        "local.bitwig.video.install.InstalledRuntime"],
                       capture_output=True, text=True, env=env)
    print(p.stdout.strip())
    if p.returncode:
        print(p.stderr.strip()[:4000])
        return 1
    (scratch / "README.txt").write_text("offline diagnosis scratch; safe to delete\n")
    return 0


def bitwig_running() -> bool:
    return subprocess.run(["pgrep", "-x", "BitwigStudio"], capture_output=True).returncode == 0


def resign() -> None:
    ent = ["--entitlements", str(ENTITLEMENTS)] if ENTITLEMENTS.is_file() else []
    p = subprocess.run(["codesign", "--force", "--sign", "-", *ent, str(APP)],
                       capture_output=True, text=True)
    if p.returncode:
        raise SystemExit(f"codesign failed:\n{p.stderr}")
    print("re-signed (ad-hoc): " + APP.name)


def install(allow_running: bool = False) -> int:
    if bitwig_running() and not allow_running:
        raise SystemExit("Bitwig Studio is running; quit it first (or --allow-running: on macOS "
                         "the jar replace is an atomic inode swap; restart Bitwig afterwards).")
    m = json.loads((PKG / "manifest.json").read_text())
    backups = STOCK.parent / "bitwig-video/backups"
    backups.mkdir(parents=True, exist_ok=True)
    backup = backups / (EXPECTED_SHA + ".jar")
    if not backup.is_file():
        if file_sha(STOCK) != EXPECTED_SHA:
            raise SystemExit(f"no backup and unexpected live jar sha {file_sha(STOCK)}")
        shutil.copy2(STOCK, backup)
    if file_sha(backup) != EXPECTED_SHA:
        raise SystemExit("backup verification failed")
    print(f"backup: {backup}")
    if file_sha(STOCK) == m["overlaySha256"]:
        print("already installed (this overlay jar in place)")
        return 0
    # atomic replace via a staged copy in the same directory; a previous overlay is simply
    # replaced (the verified pristine backup is what restore uses)
    staged = STOCK.parent / ".bitwig-video-install.tmp"
    shutil.copy2(PKG / "bitwig-video.jar", staged)
    if file_sha(staged) != m["overlaySha256"]:
        staged.unlink()
        raise SystemExit("staged jar verification failed")
    os.replace(staged, STOCK)
    print(f"installed jar: {STOCK} ({file_sha(STOCK)})")
    root = STOCK.parent / "bitwig-video/releases" / INTEGRATION_VERSION
    if not (root / "component.json").is_file():
        root.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(PKG / "component.zip") as z:
            z.extractall(root)
    print(f"component: {root}")
    for mod in ("video-preview", "action-menu-composition-framework"):
        (USER_DATA / "mods" / mod).mkdir(parents=True, exist_ok=True)
    (USER_DATA / "mods/video-preview/logs").mkdir(parents=True, exist_ok=True)
    print(f"user data: {USER_DATA}")
    resign()
    print("INSTALL_OK " + INTEGRATION_VERSION + " video " + VIDEO_VERSION)
    return 0


def status() -> int:
    print(f"stock sha now: {file_sha(STOCK)}")
    print(f"expected stock: {EXPECTED_SHA}")
    m = PKG / "manifest.json"
    if m.is_file():
        print(f"package overlay sha: {json.loads(m.read_text())['overlaySha256']}")
    backups = sorted((STOCK.parent / "bitwig-video/backups").glob("*.jar")) \
        if (STOCK.parent / "bitwig-video/backups").is_dir() else []
    print(f"backups: {[str(b) for b in backups]}")
    print(f"component: {staged_component_root().is_dir() and staged_component_root() or STOCK.parent / 'bitwig-video'}")
    p = subprocess.run(["codesign", "-v", str(APP)], capture_output=True, text=True)
    print("codesign -v:", "OK" if p.returncode == 0 else p.stderr.strip())
    return 0


def restore(allow_running: bool = False) -> int:
    if bitwig_running() and not allow_running:
        raise SystemExit("Bitwig Studio is running; quit it first (or --allow-running: the jar "
                         "replace is an atomic inode swap; restart Bitwig afterwards).")
    backup = STOCK.parent / "bitwig-video/backups" / (EXPECTED_SHA + ".jar")
    if not backup.is_file():
        raise SystemExit(f"no backup at {backup}")
    if file_sha(backup) != EXPECTED_SHA:
        raise SystemExit("backup verification failed")
    if file_sha(STOCK) == EXPECTED_SHA:
        print("original already in place")
    else:
        shutil.copy2(backup, STOCK)
        if file_sha(STOCK) != EXPECTED_SHA:
            raise SystemExit("restore verification failed")
        print(f"restored original jar: {STOCK}")
    resign()
    print("RESTORE_OK (component files left in place; delete "
          f"{STOCK.parent / 'bitwig-video'} and {USER_DATA} to remove all traces)")
    return 0


def main() -> int:
    allow = "--allow-running" in sys.argv[1:]
    cmd = next((a for a in sys.argv[1:] if not a.startswith("--")), "")
    return {"build": build, "verify": verify,
            "install": lambda: install(allow), "status": status, "restore": lambda: restore(allow)} \
        .get(cmd, lambda: print(__doc__) or 1)()


if __name__ == "__main__":
    sys.exit(main())

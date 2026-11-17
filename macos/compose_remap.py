#!/usr/bin/env python3
"""Compose a windows-6.1.7 -> macos-6.1.7 name translation from two Patchbay remaps:

    windows-6.1.7 --(invert: windows-6.1.3__windows-6.1.7, windows pack)--> windows-6.1.3
                 --(windows-6.1.3__macos-6.1.7, macos pack)--------------> macos-6.1.7

Alias rows (inherited members) can repeat a triple; duplicates are kept in lists and
the composition requires every candidate to agree, else reports AMBIGUOUS.

Usage:
  python3 compose_remap.py lookup <WinOwner> <winName> [winDesc]   # member (desc omits: all overloads)
  python3 compose_remap.py class <WinClass>                        # class translation
  python3 compose_remap.py dump out.tsv                           # full composed table (M rows keyed by win617)
"""
from __future__ import annotations
import gzip, sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
A = HERE.parent / "dwl-bml-win" / "maps-win617" / "windows-6.1.3__windows-6.1.7.tsv.gz"
B = Path.home() / "Downloads/BML-0.36.0-dev.16-macos/maps/macos-6.1.7-map1/remap/windows-6.1.3__macos-6.1.7.tsv.gz"

Triple = tuple[str, str, str]


def load(path: Path):
    cls_s2t: dict[str, str] = {}
    cls_t2s: dict[str, str] = {}
    mem_s2t: dict[Triple, list[Triple]] = {}
    mem_t2s: dict[Triple, list[Triple]] = {}
    with gzip.open(path, "rt", encoding="utf8") as f:
        for line in f:
            c = line.rstrip("\n").split("\t")
            if c[0] == "C":
                cls_s2t.setdefault(c[1], c[2])
                cls_t2s.setdefault(c[2], c[1])
            elif c[0] in ("F", "M"):
                s, t = (c[1], c[2], c[3]), (c[4], c[5], c[6])
                mem_s2t.setdefault(s, []).append(t)
                mem_t2s.setdefault(t, []).append(s)
    return cls_s2t, cls_t2s, mem_s2t, mem_t2s


def main() -> int:
    a_cls_s2t, a_cls_t2s, a_mem_s2t, a_mem_t2s = load(A)   # win613 -> win617
    b_cls_s2t, b_cls_t2s, b_mem_s2t, b_mem_t2s = load(B)    # win613 -> mac617
    win_classes = {w: b_cls_s2t[s] for w, s in a_cls_t2s.items() if s in b_cls_s2t}
    win_members: dict[Triple, list[Triple]] = {}
    ambiguous = 0
    for t, sources in a_mem_t2s.items():        # t = win617 triple
        outs = []
        for s in sources:                        # s = win613 triple
            outs.extend(b_mem_s2t.get(s, []))
        unique = {(o[0], o[1], o[2]) for o in outs}
        names = {(o[0], o[1]) for o in outs}
        if len(names) > 1:
            ambiguous += 1
        if unique:
            win_members[t] = sorted(unique)
    print(f"composed classes: {len(win_classes)}  members: {len(win_members)}  "
          f"ambiguous(member name disagrees among candidates): {ambiguous}", file=sys.stderr)

    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "class":
        print(win_classes.get(sys.argv[2], "NOT FOUND"))
    elif cmd == "lookup":
        owner, name = sys.argv[2], sys.argv[3]
        desc = sys.argv[4] if len(sys.argv) > 4 else None
        hits = {t: v for t, v in win_members.items() if t[0] == owner and t[1] == name
                and (desc is None or t[2] == desc)}
        if not hits:
            print("NOT FOUND")
        for t, v in sorted(hits.items()):
            print(f"{t[0]}.{t[1]} {t[2]}  ->  " + " | ".join(f"{x[0]}.{x[1]} {x[2]}" for x in v))
    elif cmd == "dump":
        out = Path(sys.argv[2])
        with out.open("w") as f:
            for w, m in sorted(win_classes.items()):
                f.write(f"C\t{w}\t{m}\n")
            for t, vs in sorted(win_members.items()):
                for v in vs:
                    f.write(f"M\t{t[0]}\t{t[1]}\t{t[2]}\t{v[0]}\t{v[1]}\t{v[2]}\n")
        print(f"wrote {out}")
    else:
        print(__doc__)
    return 0


if __name__ == "__main__":
    sys.exit(main())

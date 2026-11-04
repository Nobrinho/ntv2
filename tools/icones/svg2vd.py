"""Converte SVGs do Lucide (traço, viewBox 24) em VectorDrawable do Android.

Uso: python -I svg2vd.py <pasta_svgs> <pasta_saida> <nomes.txt>
nomes.txt: uma linha por ícone, "nome" ou "nome:filled" (gera também ic_nbr_<nome>_filled).
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

NUM = re.compile(r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?")


def fmt(v: float) -> str:
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def normalize_path(d: str) -> str:
    """Reescreve o path com separadores explícitos (o parser do Android erra flags de arco compactadas)."""
    out, i, cmd = [], 0, None
    counts = {"m": 2, "l": 2, "h": 1, "v": 1, "c": 6, "s": 4, "q": 4, "t": 2, "a": 7, "z": 0}

    def skip(j):
        while j < len(d) and d[j] in " ,\t\n\r":
            j += 1
        return j

    while True:
        i = skip(i)
        if i >= len(d):
            break
        if d[i].isalpha():
            cmd = d[i]
            i += 1
            out.append(cmd)
            if cmd.lower() == "z":
                continue
        elif cmd is None:
            raise ValueError("path sem comando: " + d)
        n = counts[cmd.lower()]
        args = []
        for k in range(n):
            i = skip(i)
            if cmd.lower() == "a" and k in (3, 4):
                args.append(d[i])
                i += 1
                continue
            m = NUM.match(d, i)
            if not m:
                raise ValueError(f"número esperado em {i}: {d}")
            args.append(fmt(float(m.group())))
            i = m.end()
        if out and out[-1] != cmd and not out[-1].isalpha():
            out.append(cmd)
        out.append(" ".join(args))
        # repetição implícita: depois de M vira L
        if cmd == "M":
            cmd = "L"
        elif cmd == "m":
            cmd = "l"
    return " ".join(out).replace(" Z", "Z").replace(" z", "z")


def circle(cx, cy, rx, ry):
    return (f"M{fmt(cx - rx)} {fmt(cy)} a{fmt(rx)} {fmt(ry)} 0 1 0 {fmt(2 * rx)} 0 "
            f"a{fmt(rx)} {fmt(ry)} 0 1 0 {fmt(-2 * rx)} 0z")


def rect(x, y, w, h, rx, ry):
    if rx == 0 and ry == 0:
        return f"M{fmt(x)} {fmt(y)}h{fmt(w)}v{fmt(h)}h{fmt(-w)}z"
    rx, ry = min(rx, w / 2), min(ry, h / 2)
    return (f"M{fmt(x + rx)} {fmt(y)}h{fmt(w - 2 * rx)}a{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(rx)} {fmt(ry)}"
            f"v{fmt(h - 2 * ry)}a{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(-rx)} {fmt(ry)}h{fmt(-(w - 2 * rx))}"
            f"a{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(-rx)} {fmt(-ry)}v{fmt(-(h - 2 * ry))}"
            f"a{fmt(rx)} {fmt(ry)} 0 0 1 {fmt(rx)} {fmt(-ry)}z")


def element_to_path(el):
    tag = el.tag.split("}")[-1]
    g = lambda k, dflt=0.0: float(el.get(k, dflt))
    if tag == "path":
        return normalize_path(el.get("d"))
    if tag == "circle":
        return circle(g("cx"), g("cy"), g("r"), g("r"))
    if tag == "ellipse":
        return circle(g("cx"), g("cy"), g("rx"), g("ry"))
    if tag == "rect":
        rx = el.get("rx"); ry = el.get("ry")
        rxv = float(rx) if rx else (float(ry) if ry else 0.0)
        ryv = float(ry) if ry else rxv
        return rect(g("x"), g("y"), g("width"), g("height"), rxv, ryv)
    if tag == "line":
        return f"M{fmt(g('x1'))} {fmt(g('y1'))}L{fmt(g('x2'))} {fmt(g('y2'))}"
    if tag in ("polyline", "polygon"):
        pts = [fmt(float(v)) for v in NUM.findall(el.get("points"))]
        pairs = [f"{pts[k]} {pts[k + 1]}" for k in range(0, len(pts), 2)]
        return "M" + "L".join(pairs) + ("z" if tag == "polygon" else "")
    raise ValueError("elemento não suportado: " + tag)


def to_vd(svg_text: str, name: str, filled: bool) -> str:
    root = ET.fromstring(svg_text)
    paths = []
    for el in root.iter():
        tag = el.tag.split("}")[-1]
        if tag in ("svg", "g", "title", "desc"):
            continue
        d = element_to_path(el)
        fill = '\n        android:fillColor="#FFFFFFFF"' if filled else ""
        paths.append(
            f'    <path\n        android:pathData="{d}"{fill}\n'
            f'        android:strokeColor="#FFFFFFFF"\n        android:strokeWidth="2"\n'
            f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round" />'
        )
    return ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            f"<!-- NBR Play: {name}{' (preenchido)' if filled else ''}. Desenho base Lucide 1.57.0 (ISC). -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="24"\n    android:viewportHeight="24">\n'
            + "\n".join(paths) + "\n</vector>\n")


def main():
    src, dst, names = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    dst.mkdir(parents=True, exist_ok=True)
    made, missing = [], []
    for line in names.read_text(encoding="utf-8").split():
        name, _, flag = line.partition(":")
        svg = src / f"{name}.svg"
        if not svg.exists():
            missing.append(name)
            continue
        text = svg.read_text(encoding="utf-8")
        base = "ic_nbr_" + name.replace("-", "_")
        (dst / f"{base}.xml").write_text(to_vd(text, name, False), encoding="utf-8")
        made.append(base)
        if flag == "filled":
            (dst / f"{base}_filled.xml").write_text(to_vd(text, name, True), encoding="utf-8")
            made.append(base + "_filled")
    print(len(made), "gerados")
    if missing:
        print("FALTANDO:", " ".join(missing))
        sys.exit(1)


if __name__ == "__main__":
    main()

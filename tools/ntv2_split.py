#!/usr/bin/env python3
"""Divide (e junta) filmes grandes em partes por bytes para subir no Telegram e tocar no ntv2.

O arquivo original (MKV, MP4...) NÃO é alterado nem recodificado: é só fatiado em pedaços de
tamanho quase igual, cada um dentro do limite de upload do Telegram. O app junta as partes em um
arquivo lógico só na hora de tocar.

Nome das partes:  <arquivo original>.partNNofMM   (ex.: Filme.2020.mkv.part03of11)
O formato é o mesmo de app/.../core/multipart/PartName.kt — manter os dois em sincronia.

Uso:
  ntv2_split.py split  FILME.mkv [--part-size 1900M] [--out-dir PASTA] [--dry-run] [--force]
  ntv2_split.py verify PASTA [--original FILME.mkv]
  ntv2_split.py join   PASTA [-o SAIDA] [--force]

Suba SOMENTE os arquivos .partNNofMM, como DOCUMENTO (não como vídeo). O .manifest.json fica
com você (serve para o verify/join). Sem dependências além da biblioteca padrão.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

MIB = 1024 * 1024

# Limites de upload do Telegram (conta comum / Premium).
FREE_LIMIT_BYTES = 2000 * MIB
PREMIUM_LIMIT_BYTES = 4000 * MIB
# Padrão: um pouco abaixo do limite da conta comum.
DEFAULT_PART_SIZE = 1900 * MIB

MIN_PARTS = 2
CHUNK = 8 * MIB
MANIFEST_SUFFIX = ".manifest.json"

# Mesma regra de PartName.parse (Kotlin): índice 1..total, total >= 2.
_PART_RE = re.compile(r"(.+)\.part([0-9]+)of([0-9]+)", re.IGNORECASE)

Progress = Optional[Callable[[int, int], None]]


class SplitError(Exception):
    """Erro de uso/ambiente com mensagem pronta para o usuário."""


# --------------------------------------------------------------------------- nomes

@dataclass(frozen=True)
class PartName:
    base: str
    index: int  # 1-based, como no nome
    total: int


def format_part_name(base: str, index: int, total: int) -> str:
    width = max(2, len(str(total)))
    return f"{base}.part{index:0{width}d}of{total:0{width}d}"


def parse_part_name(name: str) -> Optional[PartName]:
    m = _PART_RE.fullmatch(name.strip())
    if not m:
        return None
    index, total = int(m.group(2)), int(m.group(3))
    if total < MIN_PARTS or not 1 <= index <= total:
        return None
    return PartName(m.group(1), index, total)


# --------------------------------------------------------------------------- tamanhos

_SIZE_RE = re.compile(r"\s*([0-9]+(?:\.[0-9]+)?)\s*([kmg]?)(?:i?b)?\s*", re.IGNORECASE)
_UNITS = {"": 1, "k": 1024, "m": MIB, "g": 1024 * MIB}


def parse_size(text: str) -> int:
    """'1900M', '1.9G', '500k' ou '123' (bytes). Base 1024."""
    m = _SIZE_RE.fullmatch(text)
    if not m:
        raise SplitError(f"Tamanho inválido: {text!r} (use por ex. 1900M, 1.9G ou 4096)")
    value = int(float(m.group(1)) * _UNITS[m.group(2).lower()])
    if value <= 0:
        raise SplitError(f"Tamanho inválido: {text!r}")
    return value


def human(n: int) -> str:
    if n >= 1024 * MIB:
        return f"{n / (1024 * MIB):.2f} GiB"
    if n >= MIB:
        return f"{n / MIB:.1f} MiB"
    return f"{n} B"


def plan_parts(total_size: int, max_part_size: int) -> list[int]:
    """Tamanhos das partes: o mínimo de partes que respeita o máximo, com tamanhos quase iguais
    (evita uma última parte minúscula). Diferença entre partes: no máximo 1 byte."""
    if total_size <= 0:
        raise SplitError("O arquivo está vazio.")
    count = -(-total_size // max_part_size)  # ceil
    if count < MIN_PARTS:
        raise SplitError(
            f"O arquivo ({human(total_size)}) cabe em uma parte só com o limite de "
            f"{human(max_part_size)}: não precisa dividir."
        )
    base, extra = divmod(total_size, count)
    return [base + 1 if i < extra else base for i in range(count)]


# --------------------------------------------------------------------------- split

def split_file(
    source: Path,
    max_part_size: int = DEFAULT_PART_SIZE,
    out_dir: Optional[Path] = None,
    force: bool = False,
    dry_run: bool = False,
    progress: Progress = None,
) -> dict:
    """Divide [source] em partes e grava o manifesto. Retorna o manifesto (dict)."""
    if not source.is_file():
        raise SplitError(f"Arquivo não encontrado: {source}")
    if max_part_size > PREMIUM_LIMIT_BYTES:
        raise SplitError(
            f"Parte de {human(max_part_size)} passa do limite do Telegram Premium "
            f"({human(PREMIUM_LIMIT_BYTES)})."
        )
    total_size = source.stat().st_size
    sizes = plan_parts(total_size, max_part_size)
    base = source.name
    out_dir = out_dir or source.with_name(source.name + ".parts")
    names = [format_part_name(base, i + 1, len(sizes)) for i in range(len(sizes))]

    manifest = {
        "name": base,
        "size": total_size,
        "parts": [{"name": n, "size": s} for n, s in zip(names, sizes)],
    }
    if dry_run:
        return manifest

    existing = [n for n in names if (out_dir / n).exists()]
    if (existing or (out_dir / (base + MANIFEST_SUFFIX)).exists()) and not force:
        raise SplitError(f"Já existem partes em {out_dir}. Use --force para sobrescrever.")
    out_dir.mkdir(parents=True, exist_ok=True)
    # As partes são uma CÓPIA: precisa de espaço para o arquivo inteiro de novo.
    free = shutil.disk_usage(out_dir).free
    if free < total_size + 64 * MIB:
        raise SplitError(
            f"Espaço insuficiente em {out_dir}: livre {human(free)}, preciso de {human(total_size)}."
        )

    overall = hashlib.sha256()
    done = 0
    written: list[Path] = []
    try:
        with source.open("rb") as src:
            for name, size in zip(names, sizes):
                tmp = out_dir / (name + ".tmp")
                final = out_dir / name
                part_hash = hashlib.sha256()
                left = size
                with tmp.open("wb") as dst:
                    while left > 0:
                        chunk = src.read(min(CHUNK, left))
                        if not chunk:
                            raise SplitError(f"{source} acabou antes do esperado (arquivo mudou?).")
                        dst.write(chunk)
                        overall.update(chunk)
                        part_hash.update(chunk)
                        left -= len(chunk)
                        done += len(chunk)
                        if progress:
                            progress(done, total_size)
                os.replace(tmp, final)
                written.append(final)
                next(p for p in manifest["parts"] if p["name"] == name)["sha256"] = part_hash.hexdigest()
            if src.read(1):
                raise SplitError(f"{source} cresceu durante a divisão. Tente de novo.")
    except BaseException:
        for p in written:
            p.unlink(missing_ok=True)
        for n in names:
            (out_dir / (n + ".tmp")).unlink(missing_ok=True)
        raise

    manifest["sha256"] = overall.hexdigest()
    (out_dir / (base + MANIFEST_SUFFIX)).write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    return manifest


# --------------------------------------------------------------------------- descoberta / verify / join

def discover_parts(directory: Path) -> tuple[str, list[Path]]:
    """Partes de UM arquivo na pasta, em ordem. Erro se houver mais de um arquivo, faltar ou
    sobrar parte."""
    if not directory.is_dir():
        raise SplitError(f"Pasta não encontrada: {directory}")
    groups: dict[tuple[str, int], dict[int, Path]] = {}
    for entry in sorted(directory.iterdir()):
        part = parse_part_name(entry.name) if entry.is_file() else None
        if part:
            groups.setdefault((part.base, part.total), {})[part.index] = entry
    if not groups:
        raise SplitError(f"Nenhuma parte (.partNNofMM) em {directory}.")
    if len(groups) > 1:
        names = ", ".join(f"{b} ({t} partes)" for b, t in groups)
        raise SplitError(f"Há mais de um arquivo dividido na pasta: {names}. Use uma pasta por filme.")
    (base, total), found = next(iter(groups.items()))
    missing = [i for i in range(1, total + 1) if i not in found]
    if missing:
        raise SplitError(f"Faltam as partes {missing} de {total} de {base}.")
    return base, [found[i] for i in range(1, total + 1)]


def _sha256_of(path: Path, progress: Progress = None, offset: int = 0, total: int = 0) -> str:
    h = hashlib.sha256()
    done = offset
    with path.open("rb") as f:
        while chunk := f.read(CHUNK):
            h.update(chunk)
            done += len(chunk)
            if progress:
                progress(done, total)
    return h.hexdigest()


def _load_manifest(directory: Path, base: str) -> Optional[dict]:
    path = directory / (base + MANIFEST_SUFFIX)
    if not path.is_file():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def verify(directory: Path, original: Optional[Path] = None, progress: Progress = None) -> list[str]:
    """Confere as partes. Retorna a lista de problemas (vazia = tudo certo)."""
    base, parts = discover_parts(directory)
    problems: list[str] = []
    manifest = _load_manifest(directory, base)
    if manifest is None and original is None:
        problems.append("Sem manifesto nem --original: só dá para conferir que as partes estão completas.")

    if manifest is not None:
        by_name = {p["name"]: p for p in manifest["parts"]}
        for part in parts:
            entry = by_name.get(part.name)
            if entry is None:
                problems.append(f"{part.name}: não consta no manifesto")
            elif part.stat().st_size != entry["size"]:
                problems.append(f"{part.name}: tamanho {part.stat().st_size} != {entry['size']}")
        if len(parts) != len(manifest["parts"]):
            problems.append(f"{len(parts)} partes na pasta, {len(manifest['parts'])} no manifesto")

    total = sum(p.stat().st_size for p in parts)
    overall = hashlib.sha256()
    done = 0
    for part in parts:
        part_hash = hashlib.sha256()
        with part.open("rb") as f:
            while chunk := f.read(CHUNK):
                overall.update(chunk)
                part_hash.update(chunk)
                done += len(chunk)
                if progress:
                    progress(done, total)
        if manifest is not None:
            entry = next((p for p in manifest["parts"] if p["name"] == part.name), None)
            if entry and entry.get("sha256") and entry["sha256"] != part_hash.hexdigest():
                problems.append(f"{part.name}: conteúdo diferente do manifesto (parte corrompida)")
    digest = overall.hexdigest()
    if manifest is not None and manifest.get("sha256") and manifest["sha256"] != digest:
        problems.append("Hash do conjunto diferente do manifesto")
    if original is not None:
        if not original.is_file():
            problems.append(f"Original não encontrado: {original}")
        elif original.stat().st_size != total:
            problems.append(f"Original tem {original.stat().st_size} bytes, as partes somam {total}")
        elif _sha256_of(original) != digest:
            problems.append("Partes juntas diferem do arquivo original")
    return problems


def join_parts(
    directory: Path, output: Optional[Path] = None, force: bool = False, progress: Progress = None
) -> Path:
    """Remonta o arquivo original. Se houver manifesto, confere o hash do resultado."""
    base, parts = discover_parts(directory)
    output = output or Path.cwd() / base
    if output.exists() and not force:
        raise SplitError(f"{output} já existe. Use --force para sobrescrever.")
    total = sum(p.stat().st_size for p in parts)
    if shutil.disk_usage(output.resolve().parent).free < total + 64 * MIB:
        raise SplitError(f"Espaço insuficiente para remontar {human(total)}.")
    tmp = output.with_name(output.name + ".tmp")
    overall = hashlib.sha256()
    done = 0
    try:
        with tmp.open("wb") as dst:
            for part in parts:
                with part.open("rb") as f:
                    while chunk := f.read(CHUNK):
                        dst.write(chunk)
                        overall.update(chunk)
                        done += len(chunk)
                        if progress:
                            progress(done, total)
        manifest = _load_manifest(directory, base)
        if manifest and manifest.get("sha256") and manifest["sha256"] != overall.hexdigest():
            raise SplitError("O arquivo remontado não confere com o manifesto (alguma parte corrompida).")
        os.replace(tmp, output)
    except BaseException:
        tmp.unlink(missing_ok=True)
        raise
    return output


# --------------------------------------------------------------------------- CLI

def _make_progress(label: str) -> Progress:
    if not sys.stderr.isatty():
        return None
    last = [0.0]

    def report(done: int, total: int) -> None:
        now = time.monotonic()
        if now - last[0] < 0.5 and done < total:
            return
        last[0] = now
        pct = 100 * done / total if total else 100
        print(f"\r{label}: {pct:5.1f}%  ({human(done)} / {human(total)})", end="", file=sys.stderr)
        if done >= total:
            print(file=sys.stderr)

    return report


def _cmd_split(args: argparse.Namespace) -> int:
    part_size = parse_size(args.part_size)
    if part_size > FREE_LIMIT_BYTES:
        print(
            f"Aviso: partes de {human(part_size)} só sobem em conta Telegram Premium "
            f"(limite comum: {human(FREE_LIMIT_BYTES)}).",
            file=sys.stderr,
        )
    out_dir = Path(args.out_dir) if args.out_dir else None
    manifest = split_file(
        Path(args.file), part_size, out_dir, force=args.force, dry_run=args.dry_run,
        progress=_make_progress("Dividindo"),
    )
    parts = manifest["parts"]
    print(f"{manifest['name']}: {human(manifest['size'])} -> {len(parts)} partes de ~{human(parts[0]['size'])}")
    for p in parts:
        print(f"  {p['name']}  {human(p['size'])}")
    if args.dry_run:
        print("(simulação: nada foi gravado)")
    else:
        where = out_dir or Path(args.file).with_name(Path(args.file).name + ".parts")
        print(f"\nGravado em {where}")
        print("Suba somente os .partNNofMM, como DOCUMENTO, no canal. Guarde o .manifest.json.")
    return 0


def _cmd_verify(args: argparse.Namespace) -> int:
    original = Path(args.original) if args.original else None
    problems = verify(Path(args.dir), original, progress=_make_progress("Conferindo"))
    if problems:
        for p in problems:
            print(f"PROBLEMA: {p}", file=sys.stderr)
        return 1
    print("OK: partes completas e íntegras.")
    return 0


def _cmd_join(args: argparse.Namespace) -> int:
    out = join_parts(
        Path(args.dir), Path(args.output) if args.output else None, force=args.force,
        progress=_make_progress("Juntando"),
    )
    print(f"Remontado: {out}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="ntv2_split.py", description="Divide/junta filmes grandes em partes para o Telegram + ntv2."
    )
    sub = parser.add_subparsers(dest="command", required=True)

    sp = sub.add_parser("split", help="divide um arquivo em partes")
    sp.add_argument("file")
    sp.add_argument("--part-size", default="1900M",
                    help="tamanho máximo de cada parte (padrão 1900M; Premium até 4000M)")
    sp.add_argument("--out-dir", help="pasta de saída (padrão: <arquivo>.parts ao lado do original)")
    sp.add_argument("--dry-run", action="store_true", help="só mostra o plano, não grava")
    sp.add_argument("--force", action="store_true", help="sobrescreve partes existentes")
    sp.set_defaults(func=_cmd_split)

    vp = sub.add_parser("verify", help="confere as partes de uma pasta")
    vp.add_argument("dir")
    vp.add_argument("--original", help="compara também com o arquivo original")
    vp.set_defaults(func=_cmd_verify)

    jp = sub.add_parser("join", help="remonta o arquivo original")
    jp.add_argument("dir")
    jp.add_argument("-o", "--output", help="arquivo de saída (padrão: ./<nome original>)")
    jp.add_argument("--force", action="store_true")
    jp.set_defaults(func=_cmd_join)
    return parser


def main(argv: Optional[list[str]] = None) -> int:
    # Console do Windows costuma ser cp1252: sem isso os acentos saem quebrados.
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure:
            reconfigure(encoding="utf-8", errors="replace")
    args = build_parser().parse_args(argv)
    try:
        return args.func(args)
    except SplitError as e:
        print(f"Erro: {e}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("\nInterrompido.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())

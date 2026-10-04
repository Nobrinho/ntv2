"""Testes do cortador. Rodar: python -m unittest discover -s tools -v"""
import contextlib
import io
import os
import random
import tempfile
import unittest
from pathlib import Path

import ntv2_split as s


def make_file(path: Path, size: int, seed: int = 1) -> bytes:
    data = random.Random(seed).randbytes(size)
    path.write_bytes(data)
    return data


class PartNameTest(unittest.TestCase):
    # Mesmos exemplos de PartNameTest.kt: os dois lados precisam concordar.
    def test_parse_canonical(self):
        self.assertEqual(s.PartName("Filme.2020.mkv", 3, 11), s.parse_part_name("Filme.2020.mkv.part03of11"))
        self.assertEqual(s.PartName("a.mp4", 1, 2), s.parse_part_name("a.mp4.part01of02"))

    def test_parse_lenient(self):
        self.assertEqual(s.PartName("a.mkv", 1, 3), s.parse_part_name("a.mkv.part1of3"))
        self.assertEqual(s.PartName("a.mkv", 2, 3), s.parse_part_name("  a.mkv.PART02OF03 "))

    def test_parse_rejects(self):
        for name in ["Filme.mkv", ".part01of02", "Filme.mkv.part01", "Filme.mkv.001",
                     "a.mkv.part00of05", "a.mkv.part06of05", "a.mkv.part01of01", "a.mkv.part01of00"]:
            self.assertIsNone(s.parse_part_name(name), name)

    def test_last_part_suffix_wins(self):
        self.assertEqual(s.PartName("x.part01of02.mkv", 2, 4), s.parse_part_name("x.part01of02.mkv.part02of04"))

    def test_format(self):
        self.assertEqual("a.mkv.part01of09", s.format_part_name("a.mkv", 1, 9))
        self.assertEqual("a.mkv.part03of11", s.format_part_name("a.mkv", 3, 11))
        self.assertEqual("a.mkv.part007of120", s.format_part_name("a.mkv", 7, 120))

    def test_roundtrip(self):
        for total in (2, 9, 10, 99, 100, 250):
            for index in (1, total // 2 + 1, total):
                name = s.format_part_name("Filme (2020) [4K].mkv", index, total)
                self.assertEqual(s.PartName("Filme (2020) [4K].mkv", index, total), s.parse_part_name(name))


class SizeTest(unittest.TestCase):
    def test_parse_size(self):
        self.assertEqual(1900 * s.MIB, s.parse_size("1900M"))
        self.assertEqual(1900 * s.MIB, s.parse_size("1900MiB"))
        self.assertEqual(int(1.5 * 1024 * s.MIB), s.parse_size("1.5g"))
        self.assertEqual(500 * 1024, s.parse_size("500k"))
        self.assertEqual(123, s.parse_size("123"))

    def test_parse_size_invalid(self):
        for text in ["", "abc", "-5M", "0", "10X"]:
            with self.assertRaises(s.SplitError, msg=text):
                s.parse_size(text)

    def test_plan_is_even_and_within_limit(self):
        for total, limit in [(1000, 300), (1001, 300), (999, 500), (10_000, 3), (7, 3)]:
            sizes = s.plan_parts(total, limit)
            self.assertEqual(total, sum(sizes))
            self.assertTrue(all(0 < x <= limit for x in sizes), (total, limit, sizes))
            self.assertLessEqual(max(sizes) - min(sizes), 1)
            # o mínimo de partes possível
            self.assertEqual(-(-total // limit), len(sizes))

    def test_plan_no_tiny_tail(self):
        # 1 byte além de 2 partes cheias não pode virar uma parte de 1 byte
        sizes = s.plan_parts(601, 300)
        self.assertEqual([201, 200, 200], sizes)

    def test_plan_single_part_is_error(self):
        with self.assertRaises(s.SplitError):
            s.plan_parts(100, 100)
        with self.assertRaises(s.SplitError):
            s.plan_parts(100, 500)

    def test_plan_empty_file_is_error(self):
        with self.assertRaises(s.SplitError):
            s.plan_parts(0, 100)


class SplitJoinTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.tmp = Path(self._tmp.name)
        self.addCleanup(self._tmp.cleanup)
        self.src = self.tmp / "Filme.2020.mkv"
        self.data = make_file(self.src, 10_000)

    def split(self, **kw):
        return s.split_file(self.src, 3_000, self.tmp / "out", **kw)

    def test_split_creates_named_parts_and_manifest(self):
        manifest = self.split()
        out = self.tmp / "out"
        names = sorted(p.name for p in out.iterdir())
        self.assertEqual(
            ["Filme.2020.mkv.manifest.json"] + [f"Filme.2020.mkv.part0{i}of04" for i in range(1, 5)], names
        )
        self.assertEqual(10_000, manifest["size"])
        self.assertEqual(self.data, b"".join((out / p["name"]).read_bytes() for p in manifest["parts"]))
        for p in manifest["parts"]:
            self.assertEqual(p["size"], (out / p["name"]).stat().st_size)
            self.assertLessEqual(p["size"], 3_000)
        self.assertEqual([], [n for n in os.listdir(out) if n.endswith(".tmp")])

    def test_default_out_dir_is_next_to_original(self):
        s.split_file(self.src, 3_000)
        self.assertTrue((self.tmp / "Filme.2020.mkv.parts" / "Filme.2020.mkv.part01of04").exists())

    def test_dry_run_writes_nothing(self):
        manifest = self.split(dry_run=True)
        self.assertEqual(4, len(manifest["parts"]))
        self.assertFalse((self.tmp / "out").exists())

    def test_refuses_to_overwrite_without_force(self):
        self.split()
        with self.assertRaises(s.SplitError):
            self.split()
        self.split(force=True)

    def test_part_size_above_premium_limit_is_refused(self):
        with self.assertRaises(s.SplitError):
            s.split_file(self.src, s.PREMIUM_LIMIT_BYTES + 1, self.tmp / "out")

    def test_missing_source(self):
        with self.assertRaises(s.SplitError):
            s.split_file(self.tmp / "nao-existe.mkv", 100)

    def test_join_restores_identical_file(self):
        self.split()
        out = s.join_parts(self.tmp / "out", self.tmp / "joined.mkv")
        self.assertEqual(self.data, out.read_bytes())

    def test_join_refuses_existing_output(self):
        self.split()
        with self.assertRaises(s.SplitError):
            s.join_parts(self.tmp / "out", self.src)

    def test_join_missing_part(self):
        self.split()
        (self.tmp / "out" / "Filme.2020.mkv.part03of04").unlink()
        with self.assertRaises(s.SplitError) as ctx:
            s.join_parts(self.tmp / "out", self.tmp / "joined.mkv")
        self.assertIn("[3]", str(ctx.exception))
        self.assertFalse((self.tmp / "joined.mkv").exists())

    def test_join_detects_corruption_and_leaves_no_output(self):
        self.split()
        part = self.tmp / "out" / "Filme.2020.mkv.part02of04"
        raw = bytearray(part.read_bytes())
        raw[10] ^= 0xFF
        part.write_bytes(bytes(raw))
        with self.assertRaises(s.SplitError):
            s.join_parts(self.tmp / "out", self.tmp / "joined.mkv")
        self.assertFalse((self.tmp / "joined.mkv").exists())
        self.assertFalse((self.tmp / "joined.mkv.tmp").exists())

    def test_verify_ok_and_against_original(self):
        self.split()
        self.assertEqual([], s.verify(self.tmp / "out"))
        self.assertEqual([], s.verify(self.tmp / "out", self.src))

    def test_verify_detects_corrupt_part(self):
        self.split()
        part = self.tmp / "out" / "Filme.2020.mkv.part01of04"
        raw = bytearray(part.read_bytes())
        raw[0] ^= 0xFF
        part.write_bytes(bytes(raw))
        problems = s.verify(self.tmp / "out")
        self.assertTrue(any("part01of04" in p and "corrompida" in p for p in problems), problems)

    def test_verify_detects_wrong_size(self):
        self.split()
        part = self.tmp / "out" / "Filme.2020.mkv.part04of04"
        part.write_bytes(part.read_bytes()[:-1])
        problems = s.verify(self.tmp / "out")
        self.assertTrue(any("tamanho" in p for p in problems), problems)

    def test_verify_detects_different_original(self):
        self.split()
        other = self.tmp / "outro.mkv"
        make_file(other, 10_000, seed=2)
        problems = s.verify(self.tmp / "out", other)
        self.assertTrue(any("original" in p.lower() for p in problems), problems)

    def test_verify_without_manifest_warns(self):
        self.split()
        (self.tmp / "out" / "Filme.2020.mkv.manifest.json").unlink()
        problems = s.verify(self.tmp / "out")
        self.assertEqual(1, len(problems))
        self.assertEqual([], s.verify(self.tmp / "out", self.src))

    def test_discover_rejects_two_movies_in_one_folder(self):
        self.split()
        (self.tmp / "out" / "Outro.mkv.part01of02").write_bytes(b"x")
        with self.assertRaises(s.SplitError):
            s.discover_parts(self.tmp / "out")

    def test_discover_ignores_non_parts(self):
        self.split()
        (self.tmp / "out" / "legenda.srt").write_text("x")
        base, parts = s.discover_parts(self.tmp / "out")
        self.assertEqual("Filme.2020.mkv", base)
        self.assertEqual(4, len(parts))

    def test_ordering_with_more_than_nine_parts(self):
        src = self.tmp / "grande.mp4"
        data = make_file(src, 1_200)
        s.split_file(src, 100, self.tmp / "g")  # 12 partes
        self.assertTrue((self.tmp / "g" / "grande.mp4.part10of12").exists())
        out = s.join_parts(self.tmp / "g", self.tmp / "g.mp4")
        self.assertEqual(data, out.read_bytes())


class CliTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.tmp = Path(self._tmp.name)
        self.addCleanup(self._tmp.cleanup)
        self.src = self.tmp / "f.mp4"
        make_file(self.src, 5_000)

    def run_cli(self, *argv):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = s.main(list(argv))
        return code, out.getvalue(), err.getvalue()

    def test_split_verify_join(self):
        code, out, _ = self.run_cli("split", str(self.src), "--part-size", "2000")
        self.assertEqual(0, code)
        self.assertIn("3 partes", out)
        parts = self.tmp / "f.mp4.parts"
        self.assertEqual(0, self.run_cli("verify", str(parts), "--original", str(self.src))[0])
        joined = self.tmp / "joined.mp4"
        self.assertEqual(0, self.run_cli("join", str(parts), "-o", str(joined))[0])
        self.assertEqual(self.src.read_bytes(), joined.read_bytes())

    def test_user_errors_return_2(self):
        code, _, err = self.run_cli("split", str(self.src), "--part-size", "10M")
        self.assertEqual(2, code)
        self.assertIn("não precisa dividir", err)
        self.assertEqual(2, self.run_cli("verify", str(self.tmp / "nao-existe"))[0])

    def test_verify_failure_returns_1(self):
        self.run_cli("split", str(self.src), "--part-size", "2000")
        parts = self.tmp / "f.mp4.parts"
        p = parts / "f.mp4.part02of03"
        p.write_bytes(b"\0" * p.stat().st_size)
        self.assertEqual(1, self.run_cli("verify", str(parts))[0])

    def test_premium_warning(self):
        code, _, err = self.run_cli("split", str(self.src), "--part-size", "2500M", "--dry-run")
        # avisa do Premium antes; o arquivo de teste é pequeno demais para dividir nesse limite
        self.assertIn("Premium", err)
        self.assertEqual(2, code)


if __name__ == "__main__":
    unittest.main()

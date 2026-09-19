"""The map generator, exercised as the command a person runs (rule 6 of the standard): a fixture
tree in a temporary directory, `python3 tools/map.py` to write, `--check` to pass, an edit to
the source, `--check` to fail."""
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

TOOL = Path(__file__).resolve().parent / "map.py"


def run(root: Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run([sys.executable, str(TOOL), "--root", str(root), *args], capture_output=True, text=True)


class MapCommandTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        pkg = self.root / "core" / "src" / "main" / "java" / "example" / "ledger"
        pkg.mkdir(parents=True)
        (pkg / "package-info.java").write_text("/** Ledgers as conserved quantities. */\npackage example.ledger;\n")
        (pkg / "Ledger.java").write_text(textwrap.dedent('''
            package example.ledger;

            import java.util.Map;

            /** Money as a conserved quantity. Every entry names a source or sink. */
            public final class Ledger {
                /** How many entries the log keeps. */
                public static final int DEFAULT_RETENTION = 1_000_000;
                private final Map<String, Long> balances = new java.util.HashMap<>();

                public Ledger(int retention) {
                    // a brace in a string: "{" and a comment with a brace }
                }

                /** Moves money; refused when {@code from} lacks it. */
                public void transfer(String from, String to, long amount) {
                    if (amount < 0) {
                        throw new IllegalArgumentException("negative");
                    }
                }

                private void hidden() {
                }

                /** A nested record. */
                public record Entry(long sequence, String party, long amount) {
                }
            }
        '''))

    def tearDown(self):
        self.tmp.cleanup()

    def test_write_then_check_then_break(self):
        wrote = run(self.root)
        self.assertEqual(0, wrote.returncode, wrote.stderr)
        locator = (self.root / "docs" / "map.md").read_text()
        self.assertIn("`example.ledger`", locator)
        self.assertIn("Ledgers as conserved quantities.", locator)
        self.assertIn("Ledger", locator)
        pages = list((self.root / "docs" / "map").rglob("*.md"))
        self.assertEqual(1, len(pages), pages)
        page = pages[0].read_text()
        self.assertIn("`void transfer(String from, String to, long amount)` — Moves money; refused when from lacks it.", page)
        self.assertIn("`Ledger(int retention)`", page)
        self.assertIn("`int DEFAULT_RETENTION`", page)
        self.assertIn("record Entry(long sequence, String party, long amount)", page)
        self.assertNotIn("hidden", page)
        self.assertIn("Money as a conserved quantity.", page)

        current = run(self.root, "--check")
        self.assertEqual(0, current.returncode, current.stderr)

        source = self.root / "core" / "src" / "main" / "java" / "example" / "ledger" / "Ledger.java"
        source.write_text(source.read_text().replace("private void hidden()", "public void exposed()"))
        stale = run(self.root, "--check")
        self.assertEqual(1, stale.returncode, "a new public method without regenerating must fail the check")
        self.assertIn("stale", stale.stderr)

    def test_an_orphan_page_fails_the_check(self):
        run(self.root)
        orphan = self.root / "docs" / "map" / "core" / "gone.md"
        orphan.write_text("left behind\n")
        stale = run(self.root, "--check")
        self.assertEqual(1, stale.returncode)
        self.assertIn("orphan", stale.stderr)


if __name__ == "__main__":
    unittest.main()

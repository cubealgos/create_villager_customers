import unittest

from release_notes import section

CHANGELOG = """# Changelog

## [1.1.0+26.2] - 2026-10-01

### Added

- Later.

## [1.0.0+26.2] - 2026-09-19

Tested with X.

### Added

- The item.
"""


class ReleaseNotesTest(unittest.TestCase):
    def test_the_section_of_a_version_stops_at_the_next_heading(self):
        self.assertEqual(section(CHANGELOG, "1.0.0+26.2"), "Tested with X.\n\n### Added\n\n- The item.")
        self.assertEqual(section(CHANGELOG, "1.1.0+26.2"), "### Added\n\n- Later.")

    def test_a_missing_version_is_an_error(self):
        with self.assertRaises(SystemExit):
            section(CHANGELOG, "9.9.9+26.2")


if __name__ == "__main__":
    unittest.main()

"""Synthetic audit contracts only: these fixtures do not render Android UI."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


TOOL = Path(__file__).resolve().parents[1] / 'tools' / 'audit_runtime_locales.py'
SPEC = importlib.util.spec_from_file_location('runtime_audit_under_test', TOOL)
AUDITOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDITOR)


class RuntimeAuditTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='lume-audit-contract-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.evidence = self.root / 'synthetic-evidence'
        self.evidence.mkdir()
        root_patch = patch.object(AUDITOR, 'ROOT', self.root)
        root_patch.start()
        self.addCleanup(root_patch.stop)
        self.catalogs = {}
        for tag in AUDITOR.TAGS:
            catalog = {
                'common_screensaver': f'{tag} Saver',
                'common_procedural_caption': '%1$s | synthetic art',
                'common_photo_caption': '%1$s | %2$s',
                'sample_action': f'{tag} Action',
                'common_photo_01': f'{tag} Lake',
            }
            catalog.update({f'common_procedural_{i:02}': f'{tag} Scene {i}' for i in range(1, 21)})
            self.catalogs[tag] = catalog
            if tag != 'en-US':
                self.write(self.root / 'tools' / 'translations' / f'{tag}.json', catalog)
            self.write(self.evidence / f'{tag}-compiled-strings.json', catalog)
            self.write(self.evidence / f'{tag}-screensaver.json', {
                'secure': True,
                'caption': f'{tag} Scene 1 | synthetic art',
                'description': f'{tag} Saver: synthetic fixture',
            })
        master = {key: {'en': value} for key, value in self.catalogs['en-US'].items()}
        self.write(self.root / 'tools' / 'common_strings.json', master)
        self.write(self.root / 'tools' / 'activity_strings.json', {})
        self.write(self.root / 'src/android/app/src/main/assets/screensaver.json', [{'artist': 'Fixture Author'}])
        self.screens = [{
            'locale': tag, 'screen': screen,
            'texts': [self.text()],
        } for tag in AUDITOR.TAGS for screen in AUDITOR.SCREENS]
        self.write(self.evidence / 'report.json', self.screens)

    @staticmethod
    def text(**overrides):
        return {
            'text': 'Synthetic visible action', 'shown': True, 'laidOut': True,
            'inViewport': True, 'width': 100, 'height': 30,
            'layoutHeight': 20, 'paddingTop': 5, 'paddingBottom': 5,
            'ellipsis': 0, **overrides,
        }

    @staticmethod
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=False), encoding='utf-8')

    def run_audit(self):
        with contextlib.redirect_stdout(io.StringIO()):
            failed = AUDITOR.audit(self.evidence)
        return failed, json.loads((self.evidence / 'audit.json').read_text(encoding='utf-8'))

    def change_saver(self, **changes):
        path = self.evidence / 'ar-screensaver.json'
        value = json.loads(path.read_text(encoding='utf-8'))
        self.write(path, {**value, **changes})

    def test_matching_synthetic_evidence_passes_and_writes_summary(self):
        failed, summary = self.run_audit()
        self.assertFalse(failed)
        self.assertEqual(summary['compiled_values'], 25 * 21)
        self.assertEqual(summary['screens'], 273)
        self.assertEqual(summary['screensavers'], 21)
        self.assertEqual(summary['visible_text_views'], 273)
        self.assertEqual(summary['differences'], [])

    def test_wrong_language_source_fails_even_with_complete_keys(self):
        self.write(self.evidence / 'ar-compiled-strings.json', self.catalogs['en-US'])
        failed, summary = self.run_audit()
        self.assertTrue(failed)
        self.assertTrue(any(error.get('key') == 'sample_action' for error in summary['differences']))

    def test_missing_or_unexpected_compiled_key_fails(self):
        for mutation in ('missing', 'extra'):
            with self.subTest(mutation=mutation):
                actual = dict(self.catalogs['ar'])
                if mutation == 'missing':
                    del actual['sample_action']
                else:
                    actual['unknown_key'] = 'Unexpected'
                self.write(self.evidence / 'ar-compiled-strings.json', actual)
                failed, summary = self.run_audit()
                self.assertTrue(failed)
                self.assertIn({'locale': 'ar', 'keys': 'mismatch'}, summary['differences'])

    def test_caption_requires_localized_title_and_correct_photo_author(self):
        for caption in ('en-US Scene 1 | synthetic art', 'ar Lake | Wrong Author'):
            with self.subTest(caption=caption):
                self.change_saver(caption=caption)
                failed, summary = self.run_audit()
                self.assertTrue(failed)
                self.assertTrue(any(error.get('screensaver') for error in summary['differences']))
        self.change_saver(caption='\u2068ar Lake | Fixture Author\u2069')
        self.assertFalse(self.run_audit()[0], 'Directional isolates must not reject a valid caption')

    def test_unsecured_or_wrong_context_screensaver_fails(self):
        for secure, description in ((False, 'ar Saver: fixture'), (True, 'en-US Saver: fixture')):
            with self.subTest(secure=secure, description=description):
                self.change_saver(secure=secure, description=description)
                self.assertTrue(self.run_audit()[0])

    def test_visible_vertical_clip_and_ellipsis_each_fail(self):
        for text in (self.text(height=29), self.text(ellipsis=1)):
            with self.subTest(text=text):
                self.screens[0]['texts'] = [text]
                self.write(self.evidence / 'report.json', self.screens)
                failed, summary = self.run_audit()
                self.assertTrue(failed)
                self.assertTrue(summary['vertical_clip_candidates'] or summary['ellipsis'])

    def test_hidden_unlaid_or_offscreen_nodes_are_not_visible_clip_failures(self):
        self.screens[0]['texts'] += [
            self.text(height=1, shown=False), self.text(height=1, laidOut=False),
            self.text(height=1, inViewport=False), self.text(width=0), self.text(height=0),
        ]
        self.write(self.evidence / 'report.json', self.screens)
        failed, summary = self.run_audit()
        self.assertFalse(failed)
        self.assertEqual(summary['visible_text_views'], 273)

    def test_duplicate_screen_or_missing_locale_cannot_pass(self):
        original = self.screens[0]
        self.screens[0] = self.screens[1]
        self.write(self.evidence / 'report.json', self.screens)
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(AssertionError):
            AUDITOR.audit(self.evidence)
        self.screens[0] = original
        self.screens = [screen for screen in self.screens if screen['locale'] != 'ar']
        self.write(self.evidence / 'report.json', self.screens)
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(AssertionError):
            AUDITOR.audit(self.evidence)

    def test_missing_or_malformed_evidence_does_not_produce_success(self):
        path = self.evidence / 'ar-screensaver.json'
        path.unlink()
        with self.assertRaises(FileNotFoundError):
            self.run_audit()
        path.write_text('{ invalid json', encoding='utf-8')
        with self.assertRaises(json.JSONDecodeError):
            self.run_audit()


if __name__ == '__main__':
    unittest.main()

#!/usr/bin/env python3
"""Check the project's 21 resource catalogs and localized guides; standard library only.

Missing production resources are failures. --self-check uses temporary fixtures,
never the project's translations. Structural success is not linguistic approval.
"""
from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass, field
import json
from pathlib import Path
import re
import tempfile
import unittest
import unicodedata
import xml.etree.ElementTree as ET


LOCALES = (
    "en-US", "en-GB", "en-CA", "pt-BR", "pt-PT", "es-ES", "fr-FR",
    "zh-CN", "hi-IN", "ar", "bn-BD", "ru-RU", "ur-PK", "ja-JP",
    "pnb-Arab-PK", "mr-IN", "de-DE", "it-IT", "ko-KR", "id-ID", "tr-TR",
)
ANDROID_NAME = "{http://schemas.android.com/apk/res/android}name"
QUANTITIES = {"zero", "one", "two", "few", "many", "other"}
FORMAT = re.compile(r"%(?:(\d+)\$)?([-#+ 0,(<]*)(\d+)?(?:\.(\d+))?([tT])?([a-zA-Z%])")
CONVERSIONS = set("bBhHsScCdoxXeEfgGaA")
DATE_CONVERSIONS = set("HIklMSLNpzZsQBbhAaCYyjmdeRTrDFc")


@dataclass
class Value:
    text: str
    formatted: bool = True


@dataclass
class Resource:
    kind: str
    name: str
    forms: dict[str, Value]
    translatable: bool = True


@dataclass
class Report:
    errors: list[dict[str, str]] = field(default_factory=list)
    catalogs: dict[str, dict] = field(default_factory=dict)
    documents: dict[str, str] = field(default_factory=dict)

    @property
    def ok(self) -> bool:
        return not self.errors

    def fail(self, code: str, path: Path, message: str) -> None:
        self.errors.append({"code": code, "path": str(path), "message": message})

    def data(self) -> dict:
        return {"ok": self.ok, "expected_locales": list(LOCALES),
                "catalogs": self.catalogs, "documents": self.documents, "errors": self.errors}


def qualifiers(locale: str) -> tuple[str, ...]:
    if locale == "en-US":
        return ("values",)
    bcp = "values-b+" + locale.replace("-", "+")
    if locale == "ar":
        return ("values-ar", bcp)
    if locale == "pnb-Arab-PK":
        return (bcp,)
    language, region = locale.split("-")
    legacy = ("values-in-rID",) if locale == "id-ID" else ()
    return (bcp, f"values-{language}-r{region}", *legacy)


def read_utf8(path: Path, report: Report) -> str | None:
    try:
        return path.read_bytes().decode("utf-8-sig")
    except (OSError, UnicodeError) as exc:
        report.fail("utf8_or_read", path, str(exc))
        return None


def read_xml(path: Path, report: Report) -> ET.Element | None:
    text = read_utf8(path, report)
    if text is None:
        return None
    declaration = re.match(r"\s*<\?xml\b[^?]*encoding\s*=\s*['\"]([^'\"]+)['\"]", text, re.I)
    if declaration and declaration[1].lower() not in {"utf-8", "utf8"}:
        report.fail("xml_encoding", path, "XML must declare UTF-8, or omit the declaration")
        return None
    if re.search(r"<!\s*(?:DOCTYPE|ENTITY)\b", text, re.I):
        report.fail("xml_doctype", path, "DTD/entities are not accepted by this resource verifier")
        return None
    try:
        return ET.fromstring(text)
    except ET.ParseError as exc:
        report.fail("xml_parse", path, str(exc))
        return None


def display_text(element: ET.Element) -> str:
    text = "".join(element.itertext())
    if len(text) >= 2 and text[0] == text[-1] == '"':
        text = text[1:-1]
    text = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m[1], 16)), text)
    return re.sub(r"\\([nrt])", lambda m: {"n": "\n", "r": "\r", "t": "\t"}[m[1]], text)


def visible(text: str) -> bool:
    return text not in {"@null", "@empty"} and any(
        unicodedata.category(c) not in {"Cc", "Cf", "Zs", "Zl", "Zp"} for c in text
    )


def format_signature(value: Value) -> Counter:
    """Check numbered Java-style argument tokens, not a replacement for Android lint."""
    if not value.formatted:
        return Counter()
    tokens: Counter = Counter()
    invalid: list[int] = []
    position = 0
    while position < len(value.text):
        position = value.text.find("%", position)
        if position < 0:
            break
        match = FORMAT.match(value.text, position)
        if not match:
            invalid.append(position)
            position += 1
            continue
        index, flags, width, precision, date, conversion = match.groups()
        if conversion in {"%", "n"} and not date:
            if index or flags or width or precision:
                raise ValueError("%% and %n cannot consume numbered arguments/flags here")
        else:
            if not index or int(index) < 1 or "<" in flags:
                raise ValueError("Use explicit positive numbered arguments, e.g. %1$s")
            allowed = DATE_CONVERSIONS if date else CONVERSIONS
            if conversion not in allowed:
                raise ValueError("Unsupported Java format conversion")
            tokens[(int(index), (date or "") + conversion)] += 1
        position = match.end()
    # A plain non-formatted label such as '50%' is valid. A malformed argument is not.
    if invalid and (tokens or any(re.match(r"%\d+\$", value.text[i:]) for i in invalid)):
        raise ValueError("Malformed/unescaped percent in a formatted resource")
    return tokens


def parse_catalog(folder: Path, report: Report) -> dict[tuple[str, str], Resource]:
    resources: dict[tuple[str, str], Resource] = {}
    for path in sorted(folder.glob("*.xml")):
        root = read_xml(path, report)
        if root is None:
            continue
        if root.tag != "resources":
            report.fail("resource_root", path, "Expected resources root")
            continue
        for node in root:
            if node.tag not in {"string", "string-array", "array", "plurals"}:
                continue
            name = node.get("name", "")
            kind = "array" if node.tag in {"string-array", "array"} else node.tag
            if not name:
                report.fail("resource_name", path, "Missing resource name")
                continue
            key = (kind, name)
            if key in resources:
                report.fail("duplicate_resource", path, f"Duplicate {kind}/{name}")
                continue
            forms: dict[str, Value] = {}
            entries = [node] if kind == "string" else list(node)
            for index, item in enumerate(entries):
                form = "value" if kind == "string" else (item.get("quantity", "") if kind == "plurals" else str(index))
                if kind != "string" and item.tag != "item":
                    report.fail("resource_item", path, f"Unexpected child in {kind}/{name}")
                    continue
                if kind == "plurals" and (form not in QUANTITIES or form in forms):
                    report.fail("plural_quantity", path, f"Invalid/duplicate quantity in {name}")
                    continue
                forms[form] = Value(display_text(item), item.get("formatted", node.get("formatted", "true")) != "false")
            if not forms or (kind == "plurals" and "other" not in forms):
                report.fail("empty_or_missing_other", path, f"Empty {kind}/{name}, or plurals without other")
            resource = Resource(kind, name, forms, node.get("translatable") != "false")
            resources[key] = resource
            if resource.translatable:
                for form, value in forms.items():
                    if not visible(value.text):
                        report.fail("empty_text", path, f"Empty {kind}/{name}:{form}")
    return resources


def resolve_value(value: Value, catalog: dict, base: dict, visited: frozenset = frozenset()) -> Value:
    alias = re.fullmatch(r"@string/([A-Za-z0-9_.]+)", value.text.strip())
    if not alias:
        if value.text.strip().startswith("@"):
            raise ValueError("External/unsupported resource reference requires explicit review")
        return value
    key = ("string", alias[1])
    if key in visited:
        raise ValueError("Cyclic string alias")
    target = catalog.get(key, base.get(key))
    if target is None:
        raise ValueError("Missing string alias target")
    return resolve_value(target.forms["value"], catalog, base, visited | {key})


def verify(resources: Path, docs: Path, config: Path | None = None) -> Report:
    report = Report()
    base_folder = resources / "values"
    base = parse_catalog(base_folder, report)
    required = {k: v for k, v in base.items() if v.translatable}
    if not required:
        report.fail("missing_base", base_folder, "Default en-US must contain translatable resources")
    for locale in LOCALES:
        folders = [resources / name for name in qualifiers(locale) if (resources / name).is_dir()]
        if not folders:
            report.fail("missing_catalog", resources, f"Missing catalog for {locale}")
            continue
        if len(folders) > 1:
            report.fail("ambiguous_catalog", resources, f"Use one complete catalog directory for {locale}")
            continue
        folder = folders[0]
        catalog = base if locale == "en-US" else parse_catalog(folder, report)
        localized = {k: v for k, v in catalog.items() if v.translatable}
        for key in sorted(required.keys() - localized.keys()):
            report.fail("missing_resource", folder, f"{key[0]}/{key[1]}")
        for key in sorted(localized.keys() - required.keys()):
            report.fail("extra_resource", folder, f"{key[0]}/{key[1]}")
        for key in sorted(required.keys() & localized.keys()):
            original, translated = required[key], localized[key]
            if original.kind == "array" and len(original.forms) != len(translated.forms):
                report.fail("array_length", folder, f"Array length differs: {key[1]}")
            for form, value in translated.forms.items():
                reference = original.forms.get(form, original.forms.get("other"))
                if reference is None:
                    continue  # Extra array index is already reported by array_length.
                try:
                    actual = resolve_value(value, catalog, base)
                    expected = resolve_value(reference, base, base)
                    if not visible(actual.text):
                        raise ValueError("Resolved value is empty")
                    if actual.formatted != expected.formatted or format_signature(actual) != format_signature(expected):
                        report.fail("placeholder_mismatch", folder, f"{key[0]}/{key[1]}:{form}")
                except (ValueError, KeyError) as exc:
                    report.fail("format_or_alias", folder, f"{key[0]}/{key[1]}:{form}: {exc}")
        counts = Counter(k[0] for k in localized)
        report.catalogs[locale] = {"directory": folder.name, "strings": counts["string"],
                                   "arrays": counts["array"], "plurals": counts["plurals"]}
    config = config or resources / "xml" / "locales_config.xml"
    root = read_xml(config, report)
    if root is not None:
        names = [node.get(ANDROID_NAME, "") for node in root]
        if root.tag != "locale-config" or any(n.tag != "locale" for n in root):
            report.fail("locale_config_shape", config, "Expected locale-config/locale elements")
        if len(names) != len(set(names)):
            report.fail("locale_config_duplicate", config, "Duplicate locale entries")
        if set(names) != set(LOCALES):
            report.fail("locale_config_set", config, "Locale entries must equal the canonical 21 tags")
    for locale in LOCALES:
        path = docs / locale / "README.md"
        text = read_utf8(path, report)
        if text is not None:
            if not visible(text):
                report.fail("empty_document", path, "Localized guide is empty")
            report.documents[locale] = str(path)
    return report


class VerifierSelfCheck(unittest.TestCase):
    """Mutate temporary catalogs to prove failures cannot become accidental passes."""
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="lume-locale-check-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.res, self.docs = self.root / "res", self.root / "docs"
        self.source = ('<resources><string name="hello">Hi %1$s, %2$d%%</string>'
                       '<string name="brand" translatable="false">Lume</string>'
                       '<string-array name="actions"><item>Back</item><item>Open %1$s</item></string-array>'
                       '<plurals name="count"><item quantity="one">%1$d item</item>'
                       '<item quantity="other">%1$d items</item></plurals></resources>')
        for locale in LOCALES:
            folder = self.res / qualifiers(locale)[0]
            folder.mkdir(parents=True)
            (folder / "strings.xml").write_text(self.source, encoding="utf-8")
            page = self.docs / locale / "README.md"
            page.parent.mkdir(parents=True)
            page.write_text("# Fixture\n\nSynthetic test guide.\n", encoding="utf-8")
        config = self.res / "xml" / "locales_config.xml"
        config.parent.mkdir()
        config.write_text('<locale-config xmlns:android="http://schemas.android.com/apk/res/android">' +
                          ''.join(f'<locale android:name="{x}"/>' for x in LOCALES) + '</locale-config>', encoding="utf-8")

    def change(self, old: str, new: str):
        path = self.res / qualifiers("pt-BR")[0] / "strings.xml"
        path.write_text(self.source.replace(old, new), encoding="utf-8")

    def codes(self):
        return {e["code"] for e in verify(self.res, self.docs).errors}

    def test_complete_fixture_and_reordered_arguments_pass(self):
        self.assertEqual(21, len(LOCALES))
        self.change('Hi %1$s, %2$d%%', '%2$d%% · مرحبا %1$s')
        result = verify(self.res, self.docs)
        self.assertTrue(result.ok, result.errors)
        self.assertEqual(21, len(result.catalogs))
        self.assertEqual(21, len(result.documents))

    def test_missing_live_resources_fail(self):
        result = verify(self.root / "absent", self.root / "absent-docs")
        self.assertFalse(result.ok)
        self.assertIn("missing_base", {x["code"] for x in result.errors})

    def test_missing_extra_and_duplicate_resources_fail(self):
        self.change('<string name="hello">Hi %1$s, %2$d%%</string>', '<string name="extra">Extra</string>')
        self.assertTrue({"missing_resource", "extra_resource"} <= self.codes())
        self.change('</resources>', '<string name="hello">Duplicate</string></resources>')
        self.assertIn("duplicate_resource", self.codes())

    def test_argument_type_number_and_unnumbered_fail(self):
        for bad in ('Hi %1$d, %2$d%%', 'Hi %3$s, %2$d%%', 'Hi %s, %2$d%%', 'Hi %1$z, %2$d%%'):
            with self.subTest(bad=bad):
                self.change('Hi %1$s, %2$d%%', bad)
                self.assertTrue({"placeholder_mismatch", "format_or_alias"} & self.codes())

    def test_array_lengths_empty_and_plural_other_fail(self):
        self.change('<item>Back</item>', '')
        self.assertIn("array_length", self.codes())
        self.change('<item>Back</item>', '<item> </item>')
        self.assertIn("empty_text", self.codes())
        self.change('<item quantity="other">%1$d items</item>', '')
        self.assertIn("empty_or_missing_other", self.codes())
        self.change('quantity="one"', 'quantity="other"')
        self.assertIn("plural_quantity", self.codes())

    def test_extra_plural_category_with_matching_argument_passes(self):
        self.change('</plurals>', '<item quantity="few">%1$d example</item></plurals>')
        self.assertTrue(verify(self.res, self.docs).ok)

    def test_invalid_utf8_xml_encoding_and_dtd_fail(self):
        path = self.res / qualifiers("pt-BR")[0] / "strings.xml"
        path.write_bytes(b'\xff')
        self.assertIn("utf8_or_read", self.codes())
        path.write_text('<resources>', encoding="utf-8")
        self.assertIn("xml_parse", self.codes())
        path.write_text('<?xml version="1.0" encoding="iso-8859-1"?>' + self.source, encoding="utf-8")
        self.assertIn("xml_encoding", self.codes())
        path.write_text('<!DOCTYPE resources [<!ENTITY x "x">]>' + self.source, encoding="utf-8")
        self.assertIn("xml_doctype", self.codes())

    def test_config_missing_duplicate_or_extra_fails(self):
        path = self.res / "xml" / "locales_config.xml"
        path.write_text('<locale-config xmlns:android="http://schemas.android.com/apk/res/android">'
                        '<locale android:name="en-US"/><locale android:name="en-US"/>'
                        '<locale android:name="xx"/></locale-config>', encoding="utf-8")
        self.assertTrue({"locale_config_duplicate", "locale_config_set"} <= self.codes())

    def test_missing_empty_and_invalid_utf8_documents_fail(self):
        path = self.docs / "ar" / "README.md"
        path.unlink()
        self.assertIn("utf8_or_read", self.codes())
        path.write_text(' \n', encoding="utf-8")
        self.assertIn("empty_document", self.codes())
        path.write_bytes(b'\xff')
        self.assertIn("utf8_or_read", self.codes())

    def test_classic_qualifier_and_aliases(self):
        folder = self.res / qualifiers("pt-BR")[0]
        folder.rename(self.res / "values-pt-rBR")
        self.assertTrue(verify(self.res, self.docs).ok)
        self.change_for_alias = self.res / "values-pt-rBR" / "strings.xml"
        self.change_for_alias.write_text(self.source.replace('<item>Back</item>', '<item>@string/brand</item>'), encoding="utf-8")
        self.assertTrue(verify(self.res, self.docs).ok)
        self.change_for_alias.write_text(self.source.replace('Hi %1$s, %2$d%%', '@string/hello'), encoding="utf-8")
        self.assertIn("format_or_alias", self.codes())

    def test_ambiguous_catalog_and_invisible_translation_fail(self):
        (self.res / "values-pt-rBR").mkdir()
        self.assertIn("ambiguous_catalog", self.codes())
        (self.res / "values-pt-rBR").rmdir()
        self.change('<item>Back</item>', '<item>\u200f</item>')
        self.assertIn("empty_text", self.codes())

    def test_format_literals_dates_and_explicit_non_format(self):
        self.assertEqual(Counter({(1, 'tY'): 1}), format_signature(Value('%1$tY %% %n')))
        self.assertEqual(Counter(), format_signature(Value('50%')))
        self.assertEqual(Counter(), format_signature(Value('%s', False)))
        for value in ('%0$s', '%1$<s', '%1$tq', '%1$s %1$'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                format_signature(Value(value))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--resources', type=Path)
    parser.add_argument('--docs-root', type=Path)
    parser.add_argument('--locale-config', type=Path)
    parser.add_argument('--json', action='store_true', help='Print the complete machine-readable report to stdout')
    parser.add_argument('--self-check', action='store_true', help='Run isolated verifier tests, not production validation')
    args = parser.parse_args()
    if args.self_check:
        suite = unittest.defaultTestLoader.loadTestsFromTestCase(VerifierSelfCheck)
        return 0 if unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful() else 1
    report = verify(args.resources or args.root / 'src/android/app/src/main/res',
                    args.docs_root or args.root / 'docs/i18n', args.locale_config)
    if args.json:
        print(json.dumps(report.data(), ensure_ascii=False, indent=2))
    else:
        print(f'{"PASS" if report.ok else "FAIL"}: {len(report.catalogs)}/21 catalogs, '
              f'{len(report.documents)}/21 guides, {len(report.errors)} errors')
        for error in report.errors[:40]:
            print(f'{error["code"]}: {error["path"]}: {error["message"]}')
        if len(report.errors) > 40:
            print(f'... {len(report.errors) - 40} further errors; use --json')
    return 0 if report.ok else 1


if __name__ == '__main__':
    raise SystemExit(main())

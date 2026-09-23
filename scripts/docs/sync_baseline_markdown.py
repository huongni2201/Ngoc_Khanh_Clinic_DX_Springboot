"""Generate searchable Markdown mirrors from the final baseline DOCX files."""

from __future__ import annotations

import re
import sys
import zipfile
from datetime import date
from pathlib import Path
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[2]
BASELINE_DIR = ROOT / "docs" / "baseline"
SOURCES = (
    ("requirement-v2.5_FINAL.docx", "requirement-v2.5.md"),
    ("use-case-v2.7_FINAL.docx", "use-case-v2.7.md"),
    ("table-design-v2.11_FINAL.docx", "table-design-v2.11.md"),
)
WORD = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
NS = {"w": WORD}


def iter_blocks(parent: ElementTree.Element):
    for child in parent:
        if child.tag in (f"{{{WORD}}}p", f"{{{WORD}}}tbl"):
            yield child
        else:
            yield from iter_blocks(child)


def text_from_paragraph(paragraph: ElementTree.Element) -> str:
    parts: list[str] = []
    for element in paragraph.iter():
        if element.tag == f"{{{WORD}}}t":
            parts.append(element.text or "")
        elif element.tag == f"{{{WORD}}}tab":
            parts.append("    ")
        elif element.tag in (f"{{{WORD}}}br", f"{{{WORD}}}cr"):
            parts.append("\n")
    return "".join(parts).strip()


def numbering_formats(numbering: ElementTree.Element | None) -> dict[tuple[str, str], str]:
    if numbering is None:
        return {}

    abstract_formats: dict[str, dict[str, str]] = {}
    for abstract in numbering.findall("w:abstractNum", NS):
        abstract_id = abstract.get(f"{{{WORD}}}abstractNumId")
        if abstract_id is None:
            continue
        abstract_formats[abstract_id] = {
            level.get(f"{{{WORD}}}ilvl", "0"): number_format.get(f"{{{WORD}}}val", "")
            for level in abstract.findall("w:lvl", NS)
            if (number_format := level.find("w:numFmt", NS)) is not None
        }

    formats: dict[tuple[str, str], str] = {}
    for number in numbering.findall("w:num", NS):
        number_id = number.get(f"{{{WORD}}}numId")
        abstract = number.find("w:abstractNumId", NS)
        if number_id is None or abstract is None:
            continue
        abstract_id = abstract.get(f"{{{WORD}}}val", "")
        level_formats = dict(abstract_formats.get(abstract_id, {}))
        for override in number.findall("w:lvlOverride", NS):
            level = override.get(f"{{{WORD}}}ilvl", "0")
            override_format = override.find("w:lvl/w:numFmt", NS)
            if override_format is not None:
                level_formats[level] = override_format.get(f"{{{WORD}}}val", "")
        formats.update({(number_id, level): value for level, value in level_formats.items()})
    return formats


def paragraph_markdown(paragraph: ElementTree.Element, formats: dict[tuple[str, str], str]) -> str:
    text = text_from_paragraph(paragraph)
    if not text:
        return ""

    style = paragraph.find("w:pPr/w:pStyle", NS)
    style_name = style.get(f"{{{WORD}}}val", "") if style is not None else ""
    normalized_style = style_name.replace(" ", "").lower()
    if normalized_style == "title":
        return f"# {text}"
    heading = re.fullmatch(r"heading(\d+)", normalized_style)
    if heading:
        return f"{'#' * min(int(heading.group(1)) + 1, 6)} {text}"

    number_properties = paragraph.find("w:pPr/w:numPr", NS)
    indentation = ""
    if number_properties is not None:
        number_id = number_properties.find("w:numId", NS)
        level = number_properties.find("w:ilvl", NS)
        level_value = level.get(f"{{{WORD}}}val", "0") if level is not None else "0"
        number_format = formats.get((number_id.get(f"{{{WORD}}}val", "") if number_id is not None else "", level_value))
        try:
            indentation = "    " * int(level_value)
        except ValueError:
            indentation = ""
        marker = "1." if number_format in {
            "decimal", "decimalZero", "lowerLetter", "upperLetter", "lowerRoman", "upperRoman"
        } else "-"
        return f"{indentation}{marker} {text}"

    is_list = normalized_style.startswith("list")
    return f"- {text}" if is_list else text


def table_markdown(table: ElementTree.Element, formats: dict[tuple[str, str], str]) -> str:
    rows: list[list[str]] = []
    for row in table.findall("w:tr", NS):
        cells: list[str] = []
        for cell in row.findall("w:tc", NS):
            value = "<br>".join(filter(None, (paragraph_markdown(p, formats) for p in cell.findall(".//w:p", NS))))
            cells.append(value.replace("|", "\\|"))
        if cells:
            rows.append(cells)
    if not rows:
        return ""

    width = max(len(row) for row in rows)
    rows = [row + [""] * (width - len(row)) for row in rows]
    rendered = ["| " + " | ".join(rows[0]) + " |", "| " + " | ".join("---" for _ in range(width)) + " |"]
    rendered.extend("| " + " | ".join(row) + " |" for row in rows[1:])
    return "\n".join(rendered)


def document_content(source: Path) -> tuple[str, str, str]:
    with zipfile.ZipFile(source) as package:
        document = ElementTree.fromstring(package.read("word/document.xml"))
        numbering = ElementTree.fromstring(package.read("word/numbering.xml")) if "word/numbering.xml" in package.namelist() else None
    formats = numbering_formats(numbering)
    body = document.find("w:body", NS)
    if body is None:
        raise ValueError(f"Missing Word document body: {source}")

    blocks: list[str] = []
    raw_text: list[str] = []
    for child in iter_blocks(body):
        if child.tag == f"{{{WORD}}}p":
            raw_text.append(text_from_paragraph(child))
            rendered = paragraph_markdown(child, formats)
        elif child.tag == f"{{{WORD}}}tbl":
            for paragraph in child.findall(".//w:p", NS):
                raw_text.append(text_from_paragraph(paragraph))
            rendered = table_markdown(child, formats)
        else:
            continue
        if rendered:
            blocks.append(rendered)

    flat_text = "\n".join(raw_text)
    version_match = re.search(r"(?:phiên bản|version)\s*:\s*([\d.]+)", flat_text, re.IGNORECASE)
    date_match = re.search(r"(?:ngày baseline|baseline(?: và cập nhật| and updates?)?)\s*:?[^\d]{0,40}(\d{2}/\d{2}/\d{4})", flat_text, re.IGNORECASE)
    if version_match is None or date_match is None:
        raise ValueError(f"Could not read version and baseline date from {source.name}")
    return version_match.group(1), date_match.group(1), "\n\n".join(blocks)


def main() -> int:
    for source_name, target_name in SOURCES:
        source = BASELINE_DIR / source_name
        target = BASELINE_DIR / target_name
        if not source.is_file():
            raise FileNotFoundError(source)
        version, baseline_date, content = document_content(source)
        header = "\n".join((
            "<!-- Generated by scripts/docs/sync_baseline_markdown.py; edit the FINAL DOCX and regenerate. -->",
            f"Source: `{source.relative_to(ROOT).as_posix()}`",
            f"Version: {version}",
            f"Baseline date: {baseline_date}",
            f"Last sync: {date.today().isoformat()}",
            "",
            "---",
            "",
        ))
        target.write_text(header + content + "\n", encoding="utf-8", newline="\n")
        print(f"Generated {target.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

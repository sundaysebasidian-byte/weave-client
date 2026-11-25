#!/usr/bin/env python3
"""Inventory legacy dynamic rendering branches; Kotlin still owns recognition/precedence."""

import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
UI = ROOT / "app/src/main/java/io/weave/client/ui"
OUTPUT = ROOT / "translations/android/source-dynamic.jsonl"
STRING = r'"((?:\\.|[^"\\])*)"'
REGEX = re.compile(r'(?:translationRegex\(' + STRING + r'\)\.matchEntire\(text\)'
                   r'|translatePattern\(' + STRING + r', text\))')
ENGLISH = re.compile(r'WeaveLanguage\.ENGLISH -> (?:locale\?\.format[^\n]*? \?: )?' + STRING)
V2 = re.compile(r'CountTemplate\(' + STRING + r',\s*SupplementalTranslation\(\s*' + STRING)
ODD = re.compile(r'\$\{([A-Za-z_]\w*)\}|\$([A-Za-z_]\w*)')


COMPOSE_INPUTS = {
    r"^(.*) \[(S0[1-6])\]$": "{message} [{code}]",
    "^(.+) · (.+)；解析器域名的引导查询使用明文 DNS，实际泄漏情况需独立核验$": "{transport} · {profile} · {explanation}",
    r"^(\d+) · (稳定|一般|波动|未完成)$": "{score} · {label}",
    "^(极简风|艺术风) · (.+)$": "{paletteGroup} · {palette}",
    "^ASN  (.+) · 未知运营商$": "ASN {asn} · {carrier}",
    r"^HTTPS (\d+) · (端点可达|服务端错误)$": "HTTPS {code} · {status}",
    "^应用规则 · (.+)$": "{prefix} · {app}",
    "^本地规则 · (.+)$": "{prefix} · {localizedSuffix}",
}
FIELD_ALTERNATIVES = re.compile(r'(?:' + STRING + r'|\belse) -> ' + STRING)
REASON_ALTERNATIVES = re.compile(r'(?:reason == ' + STRING + r'|\belse) -> ' + STRING)

def kotlin_text(text):
    # Kotlin and JSON share the escape alphabet for the literals in this catalog, except \$.
    return json.loads('"' + text.replace(r'\$', '$') + '"')


def row(id_, pattern, english, kind="template"):
    return {"id": f"dynamic:{id_}", "sourcePattern": kotlin_text(pattern),
            "en": english, "kind": kind}


def inventory():
    lines = (UI / "Language.kt").read_text()
    main = lines[lines.index("private fun translateCommonPatterns"):lines.index("internal data class SupplementalTranslation")]
    patterns = list(REGEX.finditer(main))
    assert len(patterns) == (main.count("translationRegex(") + main.count("translatePattern(")), "Unrecognized dynamic matcher"
    match_labels = set()
    count_english = 0
    rows = []
    first_branch = main[:patterns[0].start()]
    assert 'if (text.startsWith("安全拦截 · "))' in first_branch
    rows.append(row("security-block-details", "安全拦截 · {details}", "{securityBlock} · {details}", "composition"))
    for index, match in enumerate(patterns):
        pattern = match[1] or match[2]
        block = main[match.end(): patterns[index+1].start() if index+1 < len(patterns) else len(main)]
        english = ENGLISH.findall(block)
        assert block.count("WeaveLanguage.ENGLISH ->") == len(english), pattern
        count_english += len(english)
        label = pattern
        if pattern in match_labels:
            # The current implementation is ordered and the second matcher is unreachable.
            assert pattern == r'^完成 (\\d+)/(\\d+) 项 · (\\d+) ms$', pattern
            continue
        match_labels.add(label)
        decoded = kotlin_text(pattern)
        key = f"legacy:{decoded}"
        for variants, template in enumerate(english, 1):
            normalized = ODD.sub(lambda m: "{" + (m[1] or m[2]) + "}", kotlin_text(template))
            kind = "fragment" if len(english) > 1 and "{" not in normalized else "template"
            if normalized == ": ":
                kind = "separator"
            rows.append(row(f"{key}#{variants}", pattern, normalized, kind))
        if not english:
            rows.append(row(f"{key}#compose", pattern, COMPOSE_INPUTS[decoded], "composition"))
        for name, alternatives in (("englishKind", FIELD_ALTERNATIVES), ("englishReason", REASON_ALTERNATIVES)):
            if f"val {name} = when" in block:
                definition = block.split(f"val {name} = when", 1)[1].split("}", 1)[0]
                fragments = alternatives.findall(definition)
                assert len(fragments) == definition.count("->")
                for condition, template in fragments:
                    rows.append(row(f"{key}#{name}:{condition or 'else'}", pattern, kotlin_text(template).strip(), "fragment"))
    assert count_english == main.count("WeaveLanguage.ENGLISH ->"), "English dynamic branch not inventoried"
    v2 = (UI / "LanguageV2.kt").read_text().split("private val COUNT_TEMPLATES = listOf(", 1)[1].split("private val COUNT_REGEXES", 1)[0]
    matches = V2.findall(v2)
    assert len(matches) == v2.count("CountTemplate("), "Unrecognized V2 matcher"
    for pattern, template in matches:
        rows.append(row(f"v2:{kotlin_text(pattern)}", pattern, kotlin_text(template).replace("%d", "{first}").replace("%t", "{second}")))
    assert len({r["id"] for r in rows}) == len(rows)
    return sorted(rows, key=lambda item: item["id"])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    entries = inventory()
    encoded = "".join(json.dumps(r, ensure_ascii=False, separators=(",", ":")) + "\n" for r in entries)
    if args.check:
        assert OUTPUT.read_text() == encoded, "Dynamic inventory is stale"
    else:
        OUTPUT.write_text(encoded)
    counts = {kind: sum(r["kind"] == kind for r in entries) for kind in {r["kind"] for r in entries}}
    print("Dynamic inventory:", ", ".join(f"{n} {kind}" for kind, n in sorted(counts.items())))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Validate staged Android Compose translations; use --release to forbid uncovered keys."""

import argparse
from collections import Counter
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent / "translations/android"
SLOTS = re.compile(r"\{[A-Za-z_]\w*\}|%(?:\d+\$)?[dsf]")
TECHNICAL = re.compile(
    r"[A-Za-z][A-Za-z0-9+.-]*://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]*"
    r"|(?<![A-Za-z0-9])(?:\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?)"
    r"|(?<![A-Za-z0-9])(?:[a-z0-9-]+\.)+[a-z]{2,}:[0-9]+"
    r"|\b(?:V2Ray|VMess|Clash|Mihomo|WireGuard|WebRTC|OpenSSL|Android|GitHub"
    r"|Weave(?: Next)?|TLS|STUN|JSON|YAML|CIDR|QUIC|UDP|HTTPS?|DNS|IPv[46]|VPN|UUID"
    r"|P50|P95|SHA-256|AES-256-GCM|PBKDF2|Apache-2\.0|GPL-3\.0(?:-or-later)?)\b"
    r"|\b(?:sing-box|Base64|Keystore)\b",
    re.ASCII,
)
ISOLATE_OPEN = {"\u2066", "\u2067", "\u2068"}
ISOLATE_CLOSE = "\u2069"
BIDI_LEGACY = frozenset(chr(c) for c in range(0x202A, 0x202F))
UNCHANGED = {"es": {"代理", "全局", r"dynamic:v2:^(\d+) 小时$"}}  # Same standard spelling/notation.
SCRIPT = {
    "ar": re.compile(r"[\u0600-\u06ff\u0750-\u077f\u08a0-\u08ff]"),
    "ko": re.compile(r"[\u1100-\u11ff\u3130-\u318f\uac00-\ud7a3]"),
    "es": re.compile(r"[A-Za-zÁÉÍÓÚáéíóúñÑüÜ]"),
    "ru": re.compile(r"[А-Яа-яЁё]"),
    "hi": re.compile(r"[\u0900-\u097f]"),
}
INVARIANTS = {
    "QUIC", "UDP / QUIC", "UDP / WebRTC", "WebRTC / STUN", "UUID",
    ": ", "{message} [{code}]", "{transport} · {profile} · {explanation}",
    "{score} · {label}", "{paletteGroup} · {palette}", "HTTPS {code} · {status}",
    "{prefix} · {app}", "{prefix} · {localizedSuffix}", "{securityBlock} · {details}",
}


def rows(path):
    with path.open(encoding="utf-8") as source:
        return [json.loads(line) for line in source]


def run(root, release=False):
    templates = (rows(root / "source-static.jsonl") + rows(root / "source-rc6-delta.jsonl")
                 + rows(root / "source-dynamic.jsonl"))
    source = {row["id"]: row for row in templates}
    if len(source) != len(templates):
        raise ValueError("duplicate source key")
    order = {key: n for n, key in enumerate(source)}
    errors = []
    counts = {}
    for locale, script in SCRIPT.items():
        values = rows(root / (locale + ".jsonl"))
        seen = set()
        last_index = -1
        for value in values:
            key, text = value["id"], value["text"]
            what = f"{locale}: {key}"
            if key not in source:
                errors.append(f"{what}: not a source key")
                continue
            if key in seen or order[key] <= last_index:
                errors.append(f"{what}: duplicate or out of source order")
            seen.add(key)
            last_index = order[key]
            original = source[key]
            english = original["en"]
            if not text.strip() or (text == key and key != english):
                errors.append(f"{what}: untranslated")
            if Counter(SLOTS.findall(english)) != Counter(SLOTS.findall(text)):
                errors.append(f"{what}: placeholders mismatch")
            if "|" in text and "|" not in english and "|" not in key:
                errors.append(f"{what}: unexpected pipe separator")
            lang_text = TECHNICAL.sub(" ", SLOTS.sub(" ", text))
            invariant = english in INVARIANTS or original.get("kind") in {"composition", "separator"} or key in UNCHANGED.get(locale, ())
            if not invariant and (not script.search(lang_text) or text == english):
                errors.append(f"{what}: untranslated or copied English")
            # Machine tokens are exact; do not convert program names, protocol tokens or addresses.
            token_source = english if key.startswith("dynamic:") else key
            missing_tokens = protected_tokens(token_source) - protected_tokens(text)
            if missing_tokens:
                errors.append(f"{what}: missing protected tokens {', '.join(sorted(missing_tokens))}")
            if not balanced_bidi(text):
                errors.append(f"{what}: unbalanced or unsafe bidi controls")
        counts[locale] = (sum(not key.startswith("dynamic:") for key in seen),
                          sum(key.startswith("dynamic:") for key in seen))
        if release and seen != source.keys():
            errors.append(f"{locale}: {len(source.keys() - seen)} missing release keys")
    return errors, counts


def protected_tokens(text):
    # A scheme-only example may be followed by localized punctuation (udp://, tcp://).
    return {token.rstrip(",;") if "://" in token else token for token in TECHNICAL.findall(text)}


def balanced_bidi(text):
    open_isolates = 0
    for char in text:
        if char in BIDI_LEGACY:
            return False
        if char in ISOLATE_OPEN:
            open_isolates += 1
        elif char == ISOLATE_CLOSE:
            open_isolates -= 1
            if open_isolates < 0:
                return False
    return open_isolates == 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release", action="store_true", help="require all keys for every locale")
    args = parser.parse_args()
    errors, counts = run(ROOT, args.release)
    for locale, (static, dynamic) in counts.items():
        print(f"{locale}: {static} static, {dynamic} dynamic entries")
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

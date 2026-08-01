"""Extract the characters the app actually renders in CalculatorDisplayFont.

Scans the files that reference CalculatorDisplayFont, plus data/ and logic/
(the narration lives in Stepconfig.kt and flows into components that use it),
and pulls characters only from string-literal *content* -- not from Kotlin
syntax. Naively grepping the source counts lambda braces, \\n escapes, ->
arrows and array indexing as though they were displayed text.
"""
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path("/Users/ondrejzika/Desktop/CodeStory/Calculator"
            "/app/src/main/java/com/fictioncutshort/justacalculator")

RAW = re.compile(r'"""(.*?)"""', re.S)
LIT = re.compile(r'"(?:[^"\\\n]|\\.)*"')
CHAR = re.compile(r"'(?:[^'\\]|\\.)'")


def literals(src):
    for m in RAW.finditer(src):
        yield m.group(1)
    src = RAW.sub('""', src)
    for m in LIT.finditer(src):
        yield m.group(0)[1:-1]
    for m in CHAR.finditer(src):
        yield m.group(0)[1:-1]


def clean(s):
    s = re.sub(r"\$\{[^{}]*\}", "", s)      # ${expr}
    s = re.sub(r"\$\w+", "", s)             # $var
    s = re.sub(r"\\u[0-9a-fA-F]{4}", "", s)
    for esc, rep in (("\\n", " "), ("\\t", " "), ("\\r", " "),
                     ('\\"', '"'), ("\\'", "'"), ("\\\\", "\\"), ("\\$", "$")):
        s = s.replace(esc, rep)
    return s


def main():
    users = sorted(p for p in ROOT.rglob("*.kt")
                   if "CalculatorDisplayFont" in p.read_text(encoding="utf-8"))
    users = [p for p in users if p.name != "Constants.kt"]
    extra = sorted(set((ROOT / "data").glob("*.kt")) | set((ROOT / "logic").glob("*.kt")))
    files = sorted(set(users) | set(extra))
    print(f"scanned {len(files)} files "
          f"({len(users)} referencing CalculatorDisplayFont + {len(extra)} in data/ logic/)")
    if len(files) < 30:
        sys.exit(f"expected ~39 files, got {len(files)} -- refusing to draw conclusions")

    cnt = Counter()
    for p in files:
        for s in literals(p.read_text(encoding="utf-8")):
            cnt.update(clean(s))

    used = {c for c in cnt if c.isprintable()}
    ascii_used = sorted(c for c in used if 32 <= ord(c) < 127)
    non_ascii = sorted(c for c in used if ord(c) >= 127)
    unused = [chr(c) for c in range(32, 127) if chr(c) not in used]

    print(f"\nin-use ASCII ({len(ascii_used)}): {''.join(ascii_used)}")
    print(f"ASCII never used ({len(unused)}): {''.join(unused)}")
    print(f"non-ASCII: {[(c, hex(ord(c)), cnt[c]) for c in non_ascii]}")

    rare = sorted((c for c in ascii_used if cnt[c] <= 3), key=lambda c: cnt[c])
    print(f"\nrarest in-use characters: {[(c, cnt[c]) for c in rare]}")
    return "".join(ascii_used), non_ascii


if __name__ == "__main__":
    main()

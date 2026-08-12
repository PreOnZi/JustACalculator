"""
desktop_variant.py — derive an installable "CalcDigit Desktop" from the app font.

The shipped font deliberately carries digital_7's malformed vertical metrics: a
*positive* hhea/typo descender (see normalize.py and the README). That is
correct for the app — every multi-line narration block is laid out against the
600/1100 em line height it produces — but it is wrong for any other program.
A host that reads the descender with its documented sign computes a line height
of ascent-minus-descent, so lines set in a design tool, a video editor or a
word processor collapse into each other.

So: same outlines, same advances, same drawing — only the vertical metrics are
made well-formed.

The family name stays "CalcDigit" on purpose. The app font is embedded in the
bundle and is never installed system-wide, so there is nothing for a rename to
disambiguate — and a rename *would* orphan every title already set in CalcDigit
in an edit, forcing the font to be re-picked clip by clip. The file name is
what distinguishes them; only ever install this one.

    python3 desktop_variant.py \\
        ../../app/src/commonMain/composeResources/font/calcdigit.ttf \\
        CalcDigit-Desktop.ttf

Re-run whenever the app font is rebuilt by normalize.py.

Requires fontTools:  python3 -m pip install fonttools
"""
import sys

from fontTools.ttLib import TTFont

FAMILY = "CalcDigit"
SUBFAMILY = "Regular"
FULL = f"{FAMILY} {SUBFAMILY}"
PS = "CalcDigit-Regular"

# Ink runs from -0.072 em (comma) to +0.728 em (quotedbl); caps top out at
# 0.6545 em. Ascent is set to clear the tallest ink, descent to clear the
# lowest, and a 0.12 em line gap gives the leading a segment face needs to stay
# legible in a caption or a title card.
ASC = 0.750
DESC = -0.100
GAP = 0.120


def main(src, dst):
    f = TTFont(src)
    upm = f["head"].unitsPerEm

    f["hhea"].ascent = round(ASC * upm)
    f["hhea"].descent = round(DESC * upm)          # negative, as the spec wants
    f["hhea"].lineGap = round(GAP * upm)

    o = f["OS/2"]
    o.sTypoAscender = round(ASC * upm)
    o.sTypoDescender = round(DESC * upm)
    o.sTypoLineGap = round(GAP * upm)
    o.usWinAscent = round(ASC * upm)
    o.usWinDescent = round(-DESC * upm)             # usWin* are magnitudes
    o.fsType = 0                                    # installable embedding
    # USE_TYPO_METRICS: tell hosts to prefer the sTypo* values above over the
    # usWin* pair, so Windows and macOS agree on the line height. The bit is
    # only defined from OS/2 v4, and the export ships v2; v2 already carries
    # every field v4 requires, so the bump is just a version stamp.
    if o.version < 4:
        o.version = 4
    o.fsSelection |= 1 << 7

    name = f["name"]
    for nid, value in ((1, FAMILY), (2, SUBFAMILY), (3, f"{FULL}; desktop metrics"),
                       (4, FULL), (6, PS), (16, FAMILY), (17, SUBFAMILY)):
        name.setName(value, nid, 3, 1, 0x409)       # Windows/Unicode/en-US
        name.setName(value, nid, 1, 0, 0)           # Mac/Roman/en

    f.save(dst)
    TTFont(dst).save(dst)                           # round-trip, recompute bounds
    print(f"wrote {dst}")
    print(f"  family     {FAMILY}")
    print(f"  ascent     {round(ASC * upm)}  ({ASC:.3f} em)")
    print(f"  descent    {round(DESC * upm)}  ({DESC:.3f} em, negative)")
    print(f"  line gap   {round(GAP * upm)}  ({GAP:.3f} em)")
    print(f"  line height {round((ASC - DESC + GAP) * upm)} units "
          f"({ASC - DESC + GAP:.3f} em)")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(f"usage: {sys.argv[0]} <app-font.ttf> <output.ttf>")
    main(sys.argv[1], sys.argv[2])

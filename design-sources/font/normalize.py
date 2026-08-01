"""
normalize.py — turn a FontStruct export of CalcDigit into a drop-in replacement
for digital_7.ttf, so none of the 76 CalculatorDisplayFont call sites need
retuning.

Three things get fixed, all measured against app/src/main/res/font/digital_7.ttf:

  1. Size.     FontStruct fills the em square; digital_7's caps are 0.6545 em.
               Unscaled, the export renders far larger and every one of the 76
               CalculatorDisplayFont call sites would need its sp value retuned.
  2. Advances. FontStruct derives advance widths from where the bricks sit,
               which comes out ~2 cells narrow across the board and much too
               wide for '.' and ','. We discard them and write digital_7's own
               values, centring the ink in each.
  3. Metrics.  hhea/OS/2 ascent+descent are matched to digital_7 so line
               spacing in the multi-line narration blocks is unchanged, and
               fsType is cleared to allow embedding.

It also reports which characters the app uses that the font doesn't cover yet.

Re-run after every FontStruct re-export:

    python3 normalize.py ~/Downloads/calcdigit/calcdigit.ttf \
        app/src/main/res/font/calculator_lcd.ttf

Requires fontTools:  python3 -m pip install fonttools
"""
import sys
from copy import deepcopy

from fontTools.misc.transform import Transform
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont

# Measured from digital_7.ttf (upm 1100), expressed as em fractions.
D7_CAP = 720 / 1100         # 0.6545 — top of '8'
D7_ASC = 800 / 1100
# digital_7 stores hhea/typo descender as +200, not -200. That is malformed --
# a descender belongs below the baseline -- and FreeType takes it literally, so
# digital_7's line height is ascent+descent = 600 units, not 1000. Every
# multi-line narration block in the app is laid out against that. We copy the
# sign bug deliberately: "correcting" it to -200 inflates line spacing by 67%.
D7_DESC = 200 / 1100
D7_WIN_ASC = 900 / 1100
D7_WIN_DESC = 187 / 1100

REF_GLYPH = "eight"         # its height defines cap height
CAP_CELLS = 18              # FontStruct grid cells per cap height

# Every advance width in digital_7, in units of its 1100 upm, keyed by the
# characters that carry it. FontStruct derives advances from where the bricks
# happen to sit -- about 2 cells narrow across the board, and far too wide for
# '.' and ',' -- so we discard them and copy digital_7's values outright. That
# makes string widths identical to the font being replaced by construction,
# rather than approximately right, and it means the letterforms are the only
# thing that has to be drawn correctly in the editor.
_D7_ADVANCE_UNITS = {
    180: "!',.1:;Ii|",
    280: "`",
    300: " ",
    310: "[]",
    341: '"',
    350: "()",
    370: "<>",
    430: "*{}",
    440: "+-=",
    480: "CEFcef",
    518: "\\",
    519: "T^_t",
    520: "$/023456789?@ABDGHJKLMNOPRSUVWXYZabdghjklmnoprsuvwxyz",
    530: "Qq",
    540: "#~",
    560: "&",
    600: "%",
}
D7_ADVANCE = {c: units / 1100 for units, chars in _D7_ADVANCE_UNITS.items()
              for c in chars}

# The three typographic characters. digital_7 sets – and — at 540/580 units with
# ink 11 and 12 cells; ours are drawn 12 and 14 so they read clearly against a
# 10-cell hyphen, and these advances give them a full cell of bearing each side.
# Between them these characters occur 16 times in the whole app, so the small
# departure from digital_7's widths cannot move a line break. '…' is three
# period squares on the baseline, which is 10 cells of ink -- a normal letter.
D7_ADVANCE.update({"–": 14 / 18 * (720 / 1100),
                   "—": 16 / 18 * (720 / 1100),
                   "…": 13 / 18 * (720 / 1100)})

DEFAULT_ADVANCE = 520 / 1100    # a normal letter, for anything unlisted

# Every character the app sets in CalculatorDisplayFont. Produced by scan.py,
# which reads string-literal *content* from the 14 files referencing the font
# plus data/ and logic/ (narration lives in Stepconfig.kt and flows into
# components that use it). Do NOT regenerate this with a plain grep: Kotlin
# syntax makes lambda braces, \n escapes, -> arrows and array indexing look
# like displayed text, which is how '>', '{', '}' and '\' first got in here.
IN_USE = (" !\"$%&'()*+,-./0123456789:=?"
          "ABCDEFGHIJKLMNOPQRSTUVWXYZ_"
          "abcdefghijklmnopqrstuvwxyz")

# Non-ASCII that genuinely reaches the display. digital_7 covers en/em dash, so
# omitting those two would be a regression; it does not cover the ellipsis, so
# that one is already falling back to Roboto in the shipped app today.
TYPOGRAPHIC = "–—…"

# Brackets appear only in the unsubstituted "[name]" placeholder strings (see
# CalculatorActions.getMessageForCount). If that copy is fixed they aren't
# needed at all, so they're tracked separately rather than as core coverage.
CONDITIONAL = "[]"

OPTIONAL = "#;<>@\\^`|~{}"     # never displayed; cheap insurance on a segment grid

# Characters that need no drawing because an existing glyph is the right shape.
# U+2212 MINUS SIGN is typographically distinct from a hyphen, but on a segment
# armature it is the same middle bar; digital_7 has no U+2212 either, so this
# is a small improvement over the font being replaced.
ALIASES = {0x2212: "hyphen"}


def bounds(glyph_set, name):
    pen = BoundsPen(glyph_set)
    glyph_set[name].draw(pen)
    return pen.bounds


def add_glyph(f, cp, name, glyph, advance):
    """Register a new glyph in glyf, hmtx, the glyph order and every cmap."""
    f.setGlyphOrder(f.getGlyphOrder() + [name])
    f["glyf"].glyphs[name] = glyph
    f["hmtx"].metrics[name] = (advance, 0)
    for table in f["cmap"].tables:
        if table.isUnicode():
            table.cmap[cp] = name


def synthesize(f):
    """Build – — … from glyphs already drawn.

    FontStruct's editor only exposes a fixed character set, so these three
    can't be drawn there. They don't need to be: an en/em dash is the hyphen
    with a longer middle, and an ellipsis is three periods. Deriving them from
    the real glyphs means they inherit the exact stroke weight and baseline,
    which hand-drawing them somewhere else would not guarantee.
    """
    cmap = f.getBestCmap()
    glyf, gs = f["glyf"], f.getGlyphSet()
    cell = bounds(gs, cmap[ord("8")])[3] / CAP_CELLS

    hyphen = cmap.get(ord("-"))
    if hyphen:
        src = glyf[hyphen]
        src.expand(glyf)
        xs = [x for x, _ in src.coordinates]
        mid = (min(xs) + max(xs)) / 2
        # Shift only the right-hand points, so the chamfered tips are preserved
        # and the flat middle section grows.
        for cp, name, extra in ((0x2013, "endash", 2), (0x2014, "emdash", 4)):
            if cp in cmap:
                continue
            g = deepcopy(src)
            for i, (x, y) in enumerate(g.coordinates):
                if x > mid:
                    g.coordinates[i] = (x + extra * cell, y)
            g.recalcBounds(glyf)
            add_glyph(f, cp, name, g, 0)
            print(f"synthesised {name} from the hyphen (+{extra} cells)")

    period = cmap.get(ord("."))
    if period and 0x2026 not in cmap:
        pen = TTGlyphPen(gs)
        for i in range(3):        # 2-cell dot, 2-cell gap -> 10 cells of ink
            gs[period].draw(TransformPen(pen, Transform(1, 0, 0, 1, i * 4 * cell, 0)))
        add_glyph(f, 0x2026, "ellipsis", pen.glyph(), 0)
        print("synthesised ellipsis from three periods")


def main(src, dst):
    f = TTFont(src)
    upm = f["head"].unitsPerEm

    synthesize(f)          # before scaling, so the new glyphs scale with the rest
    gs = f.getGlyphSet()

    cap = bounds(gs, REF_GLYPH)[3] / upm
    s = D7_CAP / cap
    print(f"cap height {cap:.4f} em -> {D7_CAP:.4f} em   (scale {s:.5f})")

    glyf, hmtx = f["glyf"], f["hmtx"]
    for name in f.getGlyphOrder():
        pen = TTGlyphPen(gs)
        gs[name].draw(TransformPen(pen, Transform(s, 0, 0, s, 0, 0)))
        glyf[name] = pen.glyph()
        adv, lsb = hmtx[name]
        hmtx[name] = (round(adv * s), round(lsb * s))

    # Rewrite every advance from digital_7's table, centring the ink in it.
    gs = f.getGlyphSet()
    cmap = f.getBestCmap()
    changed = 0
    for cp, name in sorted(cmap.items()):
        ch = chr(cp)
        target = round(D7_ADVANCE.get(ch, DEFAULT_ADVANCE) * upm)
        bnd = bounds(gs, name)
        if bnd is None:                      # space and friends: no ink to centre
            hmtx[name] = (target, 0)
            continue
        x0, _, x1, _ = bnd
        lsb = round((target - (x1 - x0)) / 2)
        if hmtx[name] != (target, lsb):
            changed += 1
        hmtx[name] = (target, lsb)
        g = glyf[name]
        if g.numberOfContours > 0:
            dx = lsb - x0
            for i, (x, y) in enumerate(g.coordinates):
                g.coordinates[i] = (x + dx, y)   # keep the GlyphCoordinates type
            g.recalcBounds(glyf)
    print(f"advances rewritten from digital_7: {changed} of {len(cmap)} glyphs")

    f["hhea"].ascent = round(D7_ASC * upm)
    f["hhea"].descent = round(D7_DESC * upm)      # positive, matching digital_7
    f["hhea"].lineGap = 0
    o = f["OS/2"]
    o.sTypoAscender, o.sTypoDescender, o.sTypoLineGap = (
        round(D7_ASC * upm), round(D7_DESC * upm), 0)
    o.usWinAscent = round(D7_WIN_ASC * upm)
    o.usWinDescent = round(D7_WIN_DESC * upm)
    o.fsType = 0

    add_aliases(f)

    f.save(dst)
    TTFont(dst).save(dst)   # round-trip so head/maxp bounds are recomputed
    print("wrote", dst)

    report_coverage(f.getBestCmap())


def add_aliases(f):
    """Point extra codepoints at glyphs that are already the right shape."""
    for cp, glyph in ALIASES.items():
        if glyph not in f.getGlyphOrder():
            continue
        for table in f["cmap"].tables:
            if table.isUnicode():
                table.cmap.setdefault(cp, glyph)
        print(f"aliased U+{cp:04X} -> {glyph}")


def report_coverage(cmap):
    """Which characters the app sets that this font can't draw yet."""
    have = {chr(cp) for cp in cmap}
    missing = [c for c in IN_USE if c not in have]
    draw = [c for c in missing if not c.islower()]
    copy = [c for c in missing if c.islower()]

    print()
    if missing:
        print(f"coverage: {len(IN_USE) - len(missing)} of {len(IN_USE)} in-use characters")
        if draw:
            print(f"  still to draw ({len(draw)}): {''.join(draw)}")
        if copy:
            print(f"  still to copy from capitals ({len(copy)}): {''.join(copy)}")
        print("  (missing characters fall back to a system font mid-sentence)")
    else:
        print(f"coverage: all {len(IN_USE)} in-use ASCII characters drawn")

    for label, group, note in (
        ("typographic", TYPOGRAPHIC, "displayed in narration; digital_7 has – and —"),
        ("conditional", CONDITIONAL, 'only needed while "[name]" goes unsubstituted'),
        ("optional", OPTIONAL, "never displayed"),
    ):
        gone = [c for c in group if c not in have]
        if gone:
            print(f"  {label} missing ({len(gone)}): {''.join(gone)}   — {note}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(f"usage: {sys.argv[0]} <fontstruct-export.ttf> <output.ttf>")
    main(sys.argv[1], sys.argv[2])

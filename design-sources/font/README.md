# Calculator LCD font

## Which file do I use?

| File | What it is |
| --- | --- |
| `app/src/commonMain/composeResources/font/calcdigit.ttf` | **The font.** What the app ships. Built by `normalize.py`. |
| `design-sources/font/calcdigit-fontstruct-export.ttf` | Raw FontStruct export. **Input to the build — never use it directly.** |
| `design-sources/font/CalcDigit-Desktop.ttf` | Install-this-one copy for use *outside* the app. Built by `desktop_variant.py`. |

The raw export is not a usable font. FontStruct derives advance widths from
where the bricks happen to sit, so `I` carries about four cells of blank to its
left and nothing to its right — "FIVE BIG" sets as "F IVE B IG" — while every
other letter is jammed to a single cell of bearing, which leaves word spaces
indistinguishable from the gaps inside a word. Its cap height is also 1.06 em
rather than 0.65, its descender has the wrong sign, and `fsType` is 4, which
forbids embedding. `normalize.py` fixes all of that. See "Two things not to
fix" below: **advance widths are set by the script, not in the editor** — the
export looking wrong is expected, not a drawing mistake.

Use `CalcDigit-Desktop.ttf` for titles, captions and mockups — it is the only
one of the three that should ever be installed in Font Book. It is the same
drawing with the same advances; only the vertical metrics differ, because the
app font copies digital_7's malformed positive descender on purpose (see
below) and other programs read that sign literally and collapse the leading.

All three report the same family name, `CalcDigit`, deliberately: the app font
is embedded in the bundle and never installed, so there is nothing for a
rename to disambiguate, and renaming would orphan every title already set in
CalcDigit in an edit. **The file name is the only thing that tells them
apart** — which is exactly how the export got installed by mistake once, so
check `Location:` in Font Book, not the family name:

```sh
system_profiler SPFontsDataType | grep -B8 'Family: CalcDigit'
```

## What it's for

The segment-style face used by `CalculatorDisplayFont`
(`app/src/commonMain/kotlin/.../util/Constants.kt`), which sets the calculator display,
the buttons, the narration, the browser overlay, the phase-1 phone overlay and
the ending screens — 76 call sites in 14 files.

It replaces `digital_7.ttf`, which could not be licensed: styleseven.com is
parked, the author is unreachable, and 1001fonts' own admin note confirms they
couldn't reach them either. Every off-the-shelf alternative we tested either
lacked glyphs the narration needs, was the wrong width, or excluded app
embedding. So the font is drawn from scratch and owned outright.

Note this font does **not** reach the Building 3 phone simulation — `TankGame.kt`
and `HomeScreenOverlay.kt` declare no `fontFamily` and render in Compose's
default, and `PhoneTetrisApp.kt` uses `FontFamily.Monospace`. That's deliberate:
the phone-within-the-game should look like a different device.

## Pipeline

Drawn in [FontStruct](https://fontstruct.com) on a 24-cell grid — 18 cells of
cap height, 4 below the baseline, 2 of overshoot — with **2-cell strokes**. That
ratio (2/18 = 11.1%) is digital_7's stroke weight exactly. Letters and digits
are 10 cells of ink.

A raw FontStruct export is not usable as-is, so:

```sh
python3 -m pip install fonttools

# 1. keep the export, so the build is reproducible from what FontStruct gave us
cp ~/Downloads/calcdigit/calcdigit.ttf \
    design-sources/font/calcdigit-fontstruct-export.ttf

# 2. build the app font
python3 design-sources/font/normalize.py \
    design-sources/font/calcdigit-fontstruct-export.ttf \
    app/src/commonMain/composeResources/font/calcdigit.ttf

# 3. rebuild the installable copy from it
python3 design-sources/font/desktop_variant.py \
    app/src/commonMain/composeResources/font/calcdigit.ttf \
    design-sources/font/CalcDigit-Desktop.ttf
```

**Run this after every re-export.** It scales the glyphs to digital_7's
0.6545 em cap height, replaces FontStruct's advance widths with digital_7's own
values, and copies digital_7's vertical metrics. Skipping it means retuning 76
`sp` values by hand.

It also prints which characters the app uses that aren't drawn yet.

## Three glyphs are synthesised, not drawn

FontStruct's editor only exposes a fixed character set, so `–`, `—` and `…`
can't be drawn there. `normalize.py` derives them from glyphs that exist:

- **en dash / em dash** — the hyphen with its right-hand points pushed out 2 and
  4 cells, so the flat middle grows while the chamfered LCD tips are preserved.
- **ellipsis** — three copies of the period at 4-cell intervals.

Deriving them means they inherit the exact stroke weight and baseline. If you
ever do draw them by hand, the script leaves yours alone — it only fills gaps.

`−` U+2212 MINUS SIGN is aliased to the hyphen glyph for the same reason.
digital_7 has no U+2212 either, and on a segment armature it's the same bar.

Of these, `–` and `—` exist in digital_7, so shipping without them would have
been a regression. `…` does *not* exist in digital_7 — it has been falling back
to Roboto in the shipped app across ten narration strings, so this is a fix.

## Two things not to "fix"

**Advance widths are set by the script, not in the editor.** FontStruct derives
them from where the bricks sit — about two cells narrow on everything, and far
too wide on `.` and `,`. Draw the ink and ignore the width marker.

**digital_7 stores its descender as `+200` instead of `−200`.** That is
malformed, but FreeType takes it literally, so its line height is 600 units
rather than 1000 — and every multi-line narration block in the app is laid out
against that. `normalize.py` reproduces the sign bug on purpose. Correcting it
inflates line spacing by 67%.

## Glyph set

81 ASCII characters reach the display, all drawn. `#` and `~` are the only
printable ASCII left undrawn, and neither appears in any displayed string.

Lowercase are copies of the capitals with identical outlines and advances — the
face is caps-only, so `a` is `A`. The slots must still be *filled*: leaving them
empty makes Android substitute a system font for every lowercase letter,
interleaving Roboto with LCD capitals on almost every line of narration.

`scan.py` regenerates the in-use list. **Do not use a plain grep for this.**
Kotlin syntax makes lambda braces, `\n` escapes, `->` arrows and array indexing
look like displayed text — that is how `>`, `{`, `}` and `\` originally ended up
on the to-draw list. `scan.py` parses string-literal content instead, and aborts
if it finds fewer than 30 files rather than silently under-reporting.

Square brackets are needed: `CalculatorActions.getMessageForCount(1)` renders
the literal string `Hello [name].` and that is intentional, not a missing
substitution.

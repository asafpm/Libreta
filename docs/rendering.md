# Rendering Xournal++ documents

This document specifies how `:core:geometry` and `:android:render` draw a page. The
reference is Xournal++ **v1.3.8** (commit `938bdb8`). References in the form
`File.cpp:line` are relative to its `src/core/`. Paragraphs marked **Libreta**
record where Libreta deliberately differs.

All coordinates are page units: PDF points, 1/72 inch. A renderer draws a page at a
zoom factor `z`, in pixels per point.

## 1. Page composition — `view/DocumentView.cpp:64-115`, `view/LayerView.cpp:20-48`

The page is drawn in this order:
1. **The background** (section 2).
2. **Each layer**, bottom to top, in file order.
3. **Within a layer, each element in file order.** There is no reordering:
   highlighters are not grouped, and nothing is drawn "on top".

Layer visibility is not stored in files, so every layer is drawn.

Elements whose bounding box (section 3.6) does not overlap the area being drawn
are skipped (`Element.cpp:95-105`).

**Libreta:** dark-mode recolouring and the "fade strokes without audio" mode are
not implemented (Phase 10).

## 2. Backgrounds — `view/background/*.cpp`

### 2.1 Solid backgrounds
Every style first fills the page with the background colour
(`PlainBackgroundView.cpp:13-16`). Colour alpha is ignored. The style then draws
its pattern on top, in a single foreground colour.

**Unknown styles** (e.g. `:copy`) are drawn as `plain`
(`PageTypeHandler.cpp:102-137`).

**Light or dark:** a background counts as *light* when `r + g + b > 0x180`
(`util/Color.h:67`). Light backgrounds use the default colours below; dark ones
use the "alt" colours.

| Style | Line width | Colour (light / dark) | Cap |
|---|---|---|---|
| ruled, lined | 0.5 | `#40a0ff` / `#434343` | butt |
| lined (margin line) | 0.5 | `#ff0080` / `#220080` | butt |
| staves | 0.5 | `#000000` / `#ffffff` | square |
| graph | 0.5 (bold lines 1.5) | `#bdbdbd` / `#434343` | square |
| dotted | 1.5 (dot diameter) | `#bdbdbd` / `#434343` | round |
| isodotted | 1.5 | `#bdbdbd` / `#434343` | round |
| isograph | 1.0 | `#bdbdbd` / `#434343` | round |

**Config string** (`model/BackgroundConfig.cpp`): the background's `config`
attribute is a comma-separated list. Each entry is split at its **last** `=` into
a key and a value; a later duplicate key wins.

| Key | Meaning | Value |
|---|---|---|
| `f1` / `af1` | line colour on light / dark backgrounds | hex number, optional `0x` |
| `f2` / `af2` | margin-line colour on light / dark backgrounds | hex number |
| `lw` | line width | decimal number |
| `r1` | raster: line spacing, grid square or triangle size | decimal number |
| `m1` | margin | decimal number |
| `rm` | round the margin to the grid (graph only) | integer, non-zero = on |
| `bli` | bold-line interval (graph only) | integer |
| `blw` | bold-line width (graph only) | decimal number |

Colours read from config are interpreted as `0xAARRGGBB`, and the alpha byte is
ignored.

**Visible-index rule** (`PlainBackgroundView.cpp:18-21`): when drawing the clip
range `[min, max]`, the lines with these indices are visible:

```
indexBounds(min, max, step, margin, length) =
    ceil(max(min, margin) / step) .. floor(min(max, length - margin) / step)
```

For a full page, the clip range is the whole page.

#### ruled — `RuledBackgroundView.cpp`
- Lines at `y = 80 + i·r1`, where `r1` defaults to 24.
- `i` ranges over `indexBounds(minY − 80 − lw/2, maxY − 80 + lw/2, r1, 0, H − 80 − 60)`.
- Each line runs from `minX` to `maxX`, the full width for a full page.
- Example: A4 (H = 841.89) has 30 lines, at y = 80 … 776.

#### lined — `LinedBackgroundView.cpp`
- The ruled lines, plus a vertical margin line at `x = m1` (default 72) from y = 0 to H.
- A negative `m1` is counted from the right: `x = W + m1`.

#### staves — `StavesBackgroundView.cpp`
- `staffHeight = lw + 20`.
- Staff tops are at `t = 80 + i·(40 + staffHeight + 4·lw)`, with `i` from
  `indexBounds(minY − 80 − staffHeight, maxY + lw/2 − 80, step, 0, H − 140)`.
- Each staff is one path:
  - five horizontal lines `y = t + 5k` (k = 0…4) from x = 50 to `W − 50`;
  - then, continuing from the end of the fifth line, a vertical line up to
    `(W − 50, t)`. This is a joined corner, cairo's default miter join;
  - then a separate vertical line from `(50, t)` to `(50, t + 20)`.

#### graph — `GraphBackgroundView.cpp`
- Square size `r1` defaults to 14.17. The grid is anchored at the page origin:
  lines at `x = i·r1` and `y = j·r1`.
- Bold lines: `bli` defaults to 0 (off) and `blw` to 1.5.
- **Line range:**
  - `hw = (bli > 0 ? blw : lw) / 2` and `clearance = max(m1, hw)`.
  - With `m1 > 0`, the clip range is first narrowed to `[m1, W − m1] × [m1, H − m1]`.
  - The column range is `indexBounds(minX − hw, maxX + hw, r1, clearance, W)`; rows likewise.
- **`rm`** (only when `m1 > 0`): narrows the extents further to whole grid cells,
  `[r1·iMin, r1·iMax]`, where `iMin..iMax = indexBounds(m1 − hw, W − m1 + hw, r1, clearance, W)`.
  Rows likewise.
- **Drawing:**
  - Vertical lines run `minY → maxY`, drawn only if `minY < maxY`; horizontal lines run `minX → maxX`.
  - With `bli > 0`, lines whose index is a multiple of `bli` are drawn in a
    second pass with width `blw`.

#### dotted — `DottedBackgroundView.cpp`
- Dots (zero-length round-capped segments of width lw, i.e. circles of diameter 1.5)
  at `(i·r1, j·r1)`.
- `i` and `j` come from `indexBounds(min − lw/2, max + lw/2, r1, lw/2, W)` (or `H` for rows).

#### isodotted / isograph — `BaseIsometricBackgroundView.cpp`, `IsoDotted…`, `IsoGraph…`
- **Grid size:** `T = r1` (default 14.17), `xstep = T·√3/2`, `ystep = T/2`.
  `cols = floor((W − 2T)/xstep)` and `rows = floor((H − 2T)/ystep)`.
- **Centring:** `xoff = (W − cols·xstep)/2` and `yoff = (H − rows·ystep)/2`.
- **Visible range:** the visible column/row index ranges are computed and
  extended by one so that they start on an even sum.
- **isodotted:** in column `c`, dots at `y = ystep, 3·ystep, …` (even `c`) or
  `0, 2·ystep, …` (odd `c`).
- **isograph:**
  - horizontal border lines at the top and bottom;
  - vertical lines at every column;
  - two families of diagonals, ported verbatim (`IsoGraphBackgroundView.cpp:37-88`).

### 2.2 Image (pixmap) backgrounds — `ImageBackgroundView.cpp`
- The image is stretched to the page, non-uniformly. Nothing is painted
  underneath, and no EXIF rotation is applied.
- How the image file is found is the caller's job (Phase 5/8).

**Libreta:** if the image is missing, the page is white.

### 2.3 PDF backgrounds — `PdfBackgroundView.cpp`, `control/PdfCache.cpp:113-150`
- The PDF page (1-based `pageno`) is rendered over white, at the PDF page's own
  size, from the page origin. It is **not** scaled to the page's width and height,
  which are normally equal anyway.

**Libreta:**
- PDF pages are rendered by Android's `PdfRenderer` (pdfium), at no less than one
  pixel per point, like Xournal++.
  - Rendering can differ in small details from Xournal++'s poppler: pdfium has
    been seen to draw arrowheads that poppler omits.
  - `PdfRenderer` reports page sizes in whole points.
- If the PDF or page is missing, the page is white, with "PDF background missing"
  in DejaVu Sans Bold 26 pt, `#cccccc`, centred.
  - Xournal++ intends this placeholder (`PdfCache.cpp:renderMissingPdfPage`) but
    never fills the text path, so it does not actually show.

### 2.4 Unknown background types
Drawn as plain white.

## 3. Elements

### 3.1 Colours
Rendering ignores the alpha byte stored in a colour. Xournal++ drops it when
loading (`LoadHandlerHelper.cpp:84`) and draws with alpha 1
(`util/Util.cpp:24-27`); opacity comes only from the rules below.

### 3.2 Strokes — `view/StrokeView.cpp`, `view/StrokeViewHelper.cpp`, `model/StrokeContour.cpp`

**Usable pressures.** These are the load-time rules of `LoadHandler.cpp:1034-1056, 932-998`,
applied at render time; the model keeps the values as written.
- A stroke has `n` points and `k` segment widths.
- If `k == 0`, or `k < n − 1`, the stroke is constant-width at `width`.
- Otherwise only the first `n − 1` widths count. If any of those is ≤ 0 or NaN,
  the stroke splits:
  - each maximal run of valid widths `[i, j)` becomes a sub-stroke of points
    `i…j` (one more point than widths);
  - if no width is valid, nothing is drawn.

**Which shape.** Strokes with fewer than 2 points are not drawn. A stroke is drawn
with pressure only if it has usable pressures and **is not a highlighter**
(`StrokeView.cpp:131-139`); highlighters ignore pressure.

**Constant width** (`StrokeViewHelper.cpp:20-29`):
- one polyline with line width `width` and a round join;
- the cap comes from `capStyle`: `round` (the default), `butt` or `square`;
- the dash pattern (section 3.3) starts at offset 0.

**With pressure** (`StrokeViewHelper.cpp:61-71`, `StrokeContour.cpp`):
- **Single dot** (exactly 2 points at the same position): a filled circle of
  radius `z₀/2`.
- **Otherwise:** one filled outline (non-zero winding), in this order:
  1. a half-circle cap around point 0 with radius `z₀/2`;
  2. the left side, with a *coupling* at each interior point `pᵢ` between
     segment widths `zᵢ₋₁` and `zᵢ` (`drawCoupling`). It is an arc of radius
     `max/2`, entered or left at `asin(min/max)`, or a chord on the inner side
     of a turn, or a "hook" for very short segments;
  3. a half-circle cap around the last point with radius `zₙ₋₂/2`;
  4. the right side, in reverse.
- **`capStyle` is ignored, and the last point's width is never used.**
- **Dashed pressure strokes** (`StrokeContourDashes`): each dash is its own
  closed outline, with round ends of radius `z/2` at the dash boundaries.

Arcs follow cairo's convention (`cairo_arc`): they are swept in the
increasing-angle direction from `a₁` to `a₂`, adding 2π to `a₂` until `a₂ ≥ a₁`,
and they start with a line from the current point.

**Opacity and blending** (`StrokeView.cpp:82-168`):

| Tool | Stroke | Fill (`fill` ≠ absent/−1) |
|---|---|---|
| pen, eraser | colour, opaque, normal (over) | colour at `fill/255`, normal, drawn **before** the stroke |
| highlighter | colour at **0.47**, **multiply** | fill and stroke merged into one shape, painted at `fill/255` with **multiply** (the stroke also uses `fill/255`) |

- **Multiply** means cairo's `MULTIPLY`, i.e. Android `BlendMode.MULTIPLY`. It is
  **not** `PorterDuff.Mode.MULTIPLY`, which gives a different, darker result.
- **Eraser** (whiteout) strokes are ordinary strokes in their own colour,
  normally white.
- **The fill area** is the point polyline, closed, filled non-zero.
- **Overlap:** overlapping highlighter strokes darken each other. A single stroke
  never darkens where it overlaps itself.

### 3.3 Dash patterns — `model/StrokeStyle.cpp:17-69`
- **Named patterns:**
  - `dash` = 6 on, 3 off
  - `dashdot` = 6, 3, 0.5, 3
  - `dot` = 0.5, 3
- **Custom:** `cust: a b c …` (lengths separated by spaces).
- **Anything else** draws solid.
- **Units:** lengths are page units and are **not** scaled by the stroke width.
- **Odd-length lists** repeat with on and off swapped, as in cairo.

**Libreta:** odd-length lists are doubled for Android's `DashPathEffect`, which
requires an even count.

### 3.4 Text — `model/Text.cpp:90-111`, `view/TextView.cpp`
- **Font:** the `font` attribute is a Pango font description, e.g.
  `Liberation Serif Bold`: a family followed by optional style words.
- **Size:** `size` is the em size in page units.
- **Layout:** no wrapping (lines break only at `\n`), left-aligned, line spacing
  factor 1.0.
- **Position:** `(x, y)` is the top-left of the layout's logical rectangle.
- **Line positions:** Pango uses *hinted* font metrics, rounded to whole points.
  Measured on Xournal++ 1.3.8 with DejaVu at 15 sizes from 6 to 36 pt, using the
  font's ascent and descent (hhea, scaled to `size`):
  - the first baseline is `y + ceil(ascent)`;
  - consecutive baselines are `ceil(ascent + descent)` apart, which is also the
    height of each line.
- **Width:** the widest line's advance, unhinted.
- **Colour:** opaque.

The font a name resolves to depends on each machine's fontconfig. On a system
without DejaVu, Xournal++ draws `Sans` with e.g. Noto Sans, which has different
metrics.

**Libreta:** draws each line separately at those baselines, with bundled fonts:
- **DejaVu** Sans, Serif and Sans Mono. These are what Xournal++ uses on typical
  Linux systems for `Sans`, `Serif` and `Monospace`, and for unknown families.
- **Liberation** Sans, Serif and Mono, which have the same metrics as Arial/Helvetica,
  Times and Courier.
- Each comes in regular, bold, italic and bold italic.

Font names are resolved the way Pango and fontconfig resolve them, as checked
against Xournal++ 1.3.8:
1. **Style words:** trailing style words are removed, case-insensitively. `bold`,
   `semibold`, `demibold`, `heavy`, `black` and `ultrabold` count as bold; `italic`
   and `oblique` count as italic; `regular`, `book`, `medium`, `light`, `condensed`,
   **`roman`** and others are dropped. The rest is the family.
   - Because Pango reads `Roman` as a style, `Times New Roman` and `Nimbus Roman`
     become the unknown families `Times New` and `Nimbus`. Xournal++ draws them in the
     default sans font, and measured line widths confirm it.
2. **Known families, by exact name:**
   - *Liberation Serif*: `liberation serif`, `times`, `tinos`, `thorndale`,
     `nimbus roman no9 l`, `tex gyre termes`.
   - *Liberation Sans*: `liberation sans`, `arial`, `helvetica`, `nimbus sans`,
     `arimo`, `albany`, `tex gyre heros`.
   - *Liberation Mono*: `liberation mono`, `courier`, `courier new`, `nimbus mono`
     (`ps`, `l`), `cousine`, `cumberland`.
   - *DejaVu*: `sans`, `sans-serif`, `serif`, `monospace` and the DejaVu names.
3. **Other families**, approximated by class:
   - *DejaVu Sans Mono* if the name contains `mono`, `courier`, `consol`, `code`,
     `typewriter`, `inconsolata`, `menlo` or `hack`;
   - *DejaVu Serif* if it contains `serif` (but not `sans`), or a known serif family
     whose name does not say so: `georgia`, `palatino`, `garamond`, `cambria`,
     `antiqua`, `c059` and `p052` (URW's Century Schoolbook and Palatino), `century`,
     `schoolbook`, `bookman`, `libertine`, `computer modern`, `cmu`, `baskerville`,
     `caslon`, `charter`, `utopia`, `didot`, `bodoni`;
   - *DejaVu Sans* otherwise.
   - These families render with different metrics than on a system that has them.

### 3.5 Images and LaTeX — `view/ImageView.cpp`, `view/TexImageView.cpp`
- **Images:** the decoded image (PNG, JPEG, …) is stretched to
  `left, top, right, bottom`, with no aspect-ratio preservation, smooth
  filtering and normal blending.
  - EXIF orientation is applied.
  - Images larger than 2²⁵ pixels are shrunk when decoded.
  - **Libreta:** uses bilinear filtering limited to the box. Cairo's filter also
    blurs the image's edges into the surroundings, which is visible when a tiny
    image is scaled up a lot.
- **LaTeX (`teximage`):** the data is detected by its bytes 1–3 (`PDF` or `PNG`).
  - PDF data: page 1 of the PDF is scaled into the box over a transparent
    background, so only the glyphs are drawn.
  - PNG data: drawn like an image.

### 3.6 Bounding boxes — `model/Stroke.cpp:816-857`, `model/Text.cpp:67-76`
- **Stroke:** the min/max of the point coordinates, padded on every side by half
  the maximum width. That is `max(zᵢ)` when the stroke is drawn with pressure,
  otherwise `width`.
  - **Libreta:** Xournal++ starts its maxima at the smallest positive double,
    which gives wrong boxes for strokes lying entirely at negative coordinates.
    Libreta starts at −∞.
  - Square caps can extend slightly outside this box.
- **Image, LaTeX:** their box.
- **Text:** `(x, y)` plus the layout size, which the platform measures.

## 4. Strokes being written and erased (Libreta)
Xournal++ draws the stroke in progress with the page (`StrokeToolView`). Libreta
draws it where it shows soonest, then hands over to the page without a visible
change:

- **Pens and whiteout** are drawn as wet ink in a front buffer above the page
  (`LowLatencyWetInk`): round-capped segments, each as wide as its first point,
  plus a predicted continuation. Once committed, the stroke is drawn over the
  stale tiles until they are re-rendered; its wet ink goes a few frames later.
  Both are opaque, so drawing them twice changes nothing.
- **Highlighters** are drawn by the page view, as they will be on the page:
  translucent and **multiplied** with what is below, which a separate surface
  cannot do. They show a frame later than wet ink. A change is never drawn over
  tiles that already show it, so translucent ink never darkens in between.
- **Erasing** changes the page with each move; until the tiles are re-rendered,
  the changed areas (plus 1.5 pixels for anti-aliasing) are drawn from the page
  over the stale tiles. Undo and redo are shown the same way.

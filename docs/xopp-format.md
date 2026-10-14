# Xournal++ file format (`.xopp` / `.xoj`)

This is the format specification that `:core:format` implements. It describes
what Xournal++ **v1.3.8** (commit `938bdb8`) reads and writes, with references
to its source in the form `File.cpp:line`. Paths are relative to
`src/core/control/xojfile/` unless they say otherwise. It also uses the sample
files in `testdata/` and Xournal++'s own test files (`testdata/xournalpp/`).

Paragraphs marked **Inkpad** record where Inkpad deliberately differs from
Xournal++. These decisions follow from Inkpad's rule that anything it doesn't
understand must survive a load/save cycle unchanged. Xournal++ itself drops
unknown content and normalises some values when it saves.

## 1. Containers

| Container | How to recognise it | Read by Xournal++ | Written by Xournal++ 1.3.8 |
|---|---|---|---|
| gzip-compressed XML | bytes `1f 8b` | yes | **yes** (the only format it writes) |
| plain, uncompressed XML | starts with `<` (`*.unzipped.xoj`) | yes (zlib reads uncompressed input transparently) | no |
| zip package | bytes `PK` | yes | no; only Xournal++ 1.0.x wrote it |

**How the container is chosen** (`LoadHandler.cpp:118-186`). Xournal++ tries to
open the file as a zip first. If that fails with "not a zip", it reads it
through zlib, which accepts both gzip and plain XML. Saving always writes gzip
(`SaveHandler.cpp:330-345`, through `GzOutputStream`).

**Zip packages** (`LoadHandler.cpp:129-178`, test files
`packaged_xopp/*/new.xopp`):
- `mimetype`: `application/xournal++`, stored uncompressed.
- `META-INF/version`: `current=<n>\nmin=<n>\n`. It must match
  `current=(\d+?)(?:\n|\r\n)min=(\d+?)`. Inside a zip, this replaces the
  `fileversion` attribute.
- `content.xml`: the XML document described below.
- `attachments/…`: binary attachments, referenced from the XML by path
  relative to the zip root. They include `<image><attachment path=…/></image>`
  bodies, `pixmap`/`pdf` backgrounds with `domain="attach"`, and
  `<audio fn=…/>` files.
- `thumbnails/thumbnail.png`: preview image; Xournal++ ignores it when loading.

**Inkpad:** reads all three containers and writes gzip only, as Xournal++ does.
When a zip is read, its attachments are returned alongside the document, keyed
by path. Image attachments become the image's data.

## 2. XML conventions

- **Declaration**: the first line is exactly `<?xml version="1.0" standalone="no"?>\n`
  (`SaveHandler.cpp:348`).
- **Layout** (`control/xml/XmlNode.cpp:44-95`): no indentation. Every element
  ends with `\n`. An element with no children is written as `<tag a="v"/>\n`,
  otherwise as `<tag …>\n<children…></tag>\n`.
  - Leaf elements whose body is text (`stroke`, `text`, `image`, `teximage`,
    `title`, `preview`) are written as `<tag …>body</tag>\n`, with no newline
    after the opening tag.
  - Old Xournal wrote `<background … />`, with a space before `/>`.
- **Attribute escaping** (`control/xml/TextAttribute.cpp:16`): `&` → `&amp;`,
  `"` → `&quot;`, `<` → `&lt;`, `>` → `&gt;`, `\n` → `&#10;`, `\r` → `&#13;`.
- **Text escaping** (`control/xml/XmlTextNode.cpp:23`): `&`, `<`, `>` only.
  Newlines are written literally.
- **Line breaks in attributes:** Xournal++ writes newlines in attribute values
  as `&#10;` and `&#13;`. Test file `load/linebreaksLatex.xopp` has LaTeX
  strings `{.\n}`, `{.\r}` and `{.\r\n}`, and they must survive a round trip
  (`LoadHandlerTest.cpp:446-482`).

**Inkpad:**
- Uses the same layout and the same escaping, and also escapes `\t` in
  attributes as `&#9;`. A literal tab inside an attribute would be read back as
  a space, because XML parsers normalise attribute whitespace.
- Never trims text bodies. For example, a `<title>` can end with a space.

## 3. Numbers

- **Writing**: every double uses `%.8g`, i.e. 8 significant digits, from
  `Util::PRECISION_FORMAT_STRING` (`util/include/util/Util.h:84`). Examples:
  `612`, `0.85`, `611.99998`, and `1e-05` for very small values. Integers are
  written with `%i` and `%zu`.
- **Older writers**: Xournal++ 1.1 wrote 8 decimal places (`1.41000000`), and
  old Xournal wrote 2 (`12.00`).
- **The `ll` suffix**: some Xournal++ 1.0/1.1 builds wrote integers as `1ll`
  and `0ll`. Readers parse the leading digits with `strtol`/`strtoull`, which
  stops at the `ll`.
- **Reading**: `g_ascii_strtod` (locale-independent). It also accepts
  exponents, `nan`, `-nan`, `inf` and `infinity`.
  `packaged_xopp/stroke/width_recovery.xopp` contains `nan` and `-nan` in
  pressure lists.

**Inkpad:** writes the **shortest decimal that parses back to exactly the same
double**, never using an exponent, written as `-0` for negative zero, and as
`nan`/`-nan`/`inf`/`-inf` for non-finite values.
- This gives exactly the same text as `%.8g` for every value with at most 8
  significant digits. That covers everything Xournal++ 1.2 and later writes.
- It keeps higher-precision values from older files, such as `461.01539741`,
  where `%.8g` would round them.
- Coordinates created by Inkpad itself should be rounded to 8 significant
  digits when the stroke is built, so that files stay as compact as
  Xournal++'s. That is the inking phase's job, not the writer's.

## 4. Document structure

```
<xournal creator="xournalpp 1.3.8" fileversion="4">
<title>Xournal++ document - see https://xournalpp.github.io/</title>
<preview>BASE64-PNG</preview>          (optional)
<audio fn="attachments/x.ogg"/>      (zip packages only, any number)
<page …> … </page>                  (one or more)
</xournal>
```

### `<xournal>` (root) — `LoadHandler.cpp:276-314`, `SaveHandler.cpp:78-82`
| Attribute | Notes |
|---|---|
| `creator` | Optional. Written as `PROJECT_STRING`, e.g. `xournalpp 1.3.8`; older values seen include `Xournal++ 1.0.7`. |
| `fileversion` | Optional integer; defaults to 1. Written as `4` (`CMakeLists.txt:291`). The `.xoj` export writes `2` (`XojExportHandler.cpp:34`). Zip packages leave it out and use `META-INF/version` instead. |
| `version` | Original Xournal (e.g. `0.4.7`). The creator then becomes `Xournal <version>`. |

- **`fileversion` 1** means an original Xournal file. Xournal++ opens it as an
  import: it won't overwrite the file on save (`LoadHandler.cpp:1119-1129`).
- **`fileversion` below 4** means stroke `ts` values are in seconds; from 4 on
  they are in milliseconds (`LoadHandler.cpp:596-606`).
- **Root element:** it must be `xournal`, or `MrWriter` for MrWriter files,
  which Xournal++ also reads. Any other root fails to load.

### `<title>`, `<preview>`, `<audio>` — `LoadHandler.cpp:316-335`
- **`<title>`:** free text, ignored when loading. 1.3.8 writes
  `Xournal++ document - see https://xournalpp.github.io/`, and the `.xoj` export
  writes `Xournal document (Compatibility) - see …`.
- **`<preview>`:** a base64 PNG thumbnail, ignored when loading. The saver
  writes it right after `<title>` (`SaveHandler.cpp:60-65`).
- **`<audio fn>`:** zip packages only. It declares an audio attachment that
  strokes and texts refer to through their `fn` attribute.
- **Any other child:** warned about and ignored.

**Inkpad:** keeps `title` and `preview` (as bytes). Unknown root-level
children are kept verbatim and written after the pages. `<audio>` is the one
exception: it is never kept in the document.
- In a zip package, the audio data is returned with the other attachments.
- Read from any other container, it is dropped with a warning.
- Xournal++ handles `<audio>` only inside zip packages and crashes on it in a
  gzip file (`LoadHandler.cpp:795-815` reads it from the zip handle
  unconditionally), so writing it back would break the file.

### `<page width height>` — `LoadHandler.cpp:316-325`, `SaveHandler.cpp:219-310`
- **Size:** `width` and `height` are doubles, in points.
- **Children:** one `<background>`, then one or more `<layer>`.
- **A page with no layers** gets one empty layer, both when loading
  (`LoadHandler.cpp:904-906`) and when saving (`SaveHandler.cpp:301-305`),
  because original Xournal requires one.

### `<background>` — `LoadHandler.cpp:337-541`, `SaveHandler.cpp:228-322`
Common attribute: `name`, which is optional (`writeBackgroundName`, not written
by the `.xoj` export).

**`type="solid"`** (attribute order on write: `type color style [config]`)
| Attribute | Notes |
|---|---|
| `color` | `#rrggbbaa`, or an old Xournal background name (section 6). |
| `style` | `plain`, `ruled`, `lined`, `staves`, `graph`, `dotted`, `isodotted`, `isograph` (`pagetype/PageTypeHandler.cpp:102-137`). Unknown values load as `plain`. Real files also contain `:copy` (`pageTypeFormatCopy.xopp`). The `.xoj` export only writes `plain`, `ruled`, `lined` and `graph`. |
| `config` | Optional, free-form; it parameterises the pattern. |

**`type="pdf"`** (attribute order on write: `type domain filename pageno`;
"original xournal can only read the XML if the attributes are in the right
order", `SaveHandler.cpp:231-234`)
| Attribute | Notes |
|---|---|
| `domain` | `absolute` (a file path, either absolute or relative to the `.xopp` file's directory) or `attach`. |
| `filename` | The path. For `attach`: in gzip it is a sidecar file `<xopp path>.<filename>`, normally `bg.pdf`, giving `x.xopp.bg.pdf`; in a zip it is an attachment path such as `attachments/bg.pdf`. |
| `pageno` | 1-based PDF page number. It may carry an `ll` suffix. |

**Only the first page with a PDF background has `domain` and `filename`.**
Later PDF pages have only `type` and `pageno` (`SaveHandler.cpp:237-269`; seen
in `pdfBackground/*.xopp` and `relativePaths.xopp`). Relative paths such as
`./././../cjk/测试.pdf` and non-ASCII names occur.

**`type="pixmap"`** (attribute order on write: `type domain filename`)
| `domain` | `filename` means |
|---|---|
| `absolute` | Image path, absolute or relative to the `.xopp` file's directory (e.g. `../../../bait/Photos/muzzy.jpg`). |
| `attach` | gzip: sidecar file `<xopp path>.<filename>`, e.g. `pages.xoj.bg_1.png`. zip: attachment path. Written as `bg_<n>.png`. |
| `clone` | Page index (0-based) of an earlier page whose image is reused (`LoadHandler.cpp:423-434`). |

Any other `type` fails the load (`LoadHandler.cpp:527-529`).

**Inkpad:** models `solid`, `pdf` and `pixmap` with every field shown above as
optional. The style is kept as a string, so values like `:copy` survive. An
unknown `type` is kept verbatim.

### `<layer>` — `LoadHandler.cpp:530-540`, `SaveHandler.cpp:163-217`
- **`name`** is optional.
- **Layer visibility is not stored in the file.** Neither the loader nor the
  saver reads or writes it.
- **Children:** `stroke`, `text`, `image` and `teximage`, plus the old Xournal
  `timestamp` (below). Any other child is ignored.

## 5. Layer elements

### `<stroke>` — `LoadHandler.cpp:543-653, 1008-1056`, `SaveHandler.cpp:101-161`
Attribute order on write: `tool [ts fn] color width [fill] capStyle [style]`.

| Attribute | Notes |
|---|---|
| `tool` | `pen`, `highlighter` or `eraser`; unknown values load as `pen`. Old files contain `eraser` strokes, drawn as white strokes ("whiteout"). |
| `color` | `#rrggbbaa` or a name (section 6). **The loader ignores the alpha byte.** The saver writes alpha `ff`, or `7f` for highlighters (`SaveHandler.cpp:104-119`). |
| `width` | `w0 [p1 … p(n-1)]`. `w0` is the nominal width. When more values follow, they are per-segment pressure widths, one for each point except the last; on save they come from points 0 to n−2 (`SaveHandler.cpp:125-133`). |
| `pressures` | MrWriter only: pressures kept in a separate attribute. |
| `ts`, `fn` | Audio link: a timestamp (ms; seconds when `fileversion` < 4) and an audio file. Since 1.3 they are written only when `fn` is not empty, and only for pens (`SaveHandler.cpp:91-99`). Older writers always wrote `ts="0" fn=""`, sometimes as `0ll`. In zip packages `fn` points to an attachment. |
| `fill` | Optional integer fill alpha (`-1` means none, which is not written). |
| `capStyle` | `butt`, `round` or `square`; unknown values load as `round`. Always written since 1.1. |
| `style` | Dash pattern: `dash` = {6, 3}, `dashdot` = {6, 3, 0.5, 3}, `dot` = {0.5, 3}, or `cust: a b c …` (written with 2 decimal places). Absent means solid (`model/StrokeStyle.cpp:15-75`). |

**Stroke body** (`LoadHandler.cpp:1008-1056`): `x0 y0 x1 y1 …`, separated by
whitespace (a trailing space occurs).
- **Point count:** fewer than 4 numbers, or an odd count, is an error, and
  **the whole file fails to load**.
- **Pressure count:** if there are at least (points − 1) values, extras beyond
  that are dropped; with fewer, the pressures are ignored and a warning is
  given.
- **Invalid pressures:** non-positive or NaN values make Xournal++ remove
  points and split the stroke (`fixNullPressureValues`, `LoadHandler.cpp:932-998`;
  test `width_recovery.xopp`).

**Inkpad:** keeps every attribute, including the alpha byte, `ts`/`fn` exactly
as present, and the width list exactly as written: NaN, negative values and
the original count all survive.
- Repairing invalid pressure values the way Xournal++ does is left to
  rendering (the geometry module).
- A malformed body is kept verbatim with a warning instead of failing the
  whole file.

### `<text>` — `LoadHandler.cpp:655-693`, `SaveHandler.cpp:176-193`
- **Attribute order on write:** `font size x y color [ts fn]`. The body is the
  text.
- **`x`/`y`:** the top-left corner.
- **Empty texts are not saved** (`SaveHandler.cpp:178-181`).
- **Colour:** old Xournal files use names (`load/text.xml`). `#00f000ff` loads
  as RGB `00f000`; alpha is ignored.

### `<image>` — `LoadHandler.cpp:695-709, 1083-1090`, `SaveHandler.cpp:194-204`
- **Attribute order on write:** `left top right bottom`.
- **Body:** base64 image data. In zip packages there is instead an
  `<attachment path="attachments/…"/>` child.
- **Decoding:** any format GdkPixbuf reads is accepted, including JPEG
  (`doc_with_jpg.xopp`).
- **Encoding:** the saver **re-encodes the image as PNG**, writing base64 in
  30-byte chunks (`control/xml/XmlImageNode.cpp:27-56`). The result is ordinary
  base64.

**Inkpad:** keeps the original encoded bytes (PNG or JPEG) and writes them back
unchanged as standard base64. Converting JPEG to PNG for compatibility with
very old readers is not done.

### `<teximage>` — `LoadHandler.cpp:711-733, 1092-1098`, `SaveHandler.cpp:205-215`
- **Attribute order on write:** `text left top right bottom`.
- **`text`:** the LaTeX source, which may contain `&#10;` and `&#13;`.
- **`texlength`:** optional, old files only; it gives the length of `text`.
- **Body:** base64. Since 1.0.x this is a PDF rendering (`%PDF-…`); older files
  have PNG. In zip packages it can be an `<attachment>` child instead.

### `<timestamp ts fn>` (old Xournal audio)
Inside a layer, it applies to the next stroke (`LoadHandler.cpp:762-771, 648-652`).
**Inkpad:** kept verbatim, in its position.

## 6. Colours — `LoadHandlerHelper.cpp:31-98`

- **Hex form:** `#RRGGBBAA`. The hex digits must cover the whole value.
- **Saving:** writes lowercase `#rrggbbaa` (`SaveHandler.cpp:84-89`).

Stroke and text colour names:

| name | RGB | name | RGB |
|---|---|---|---|
| black | `000000` | lightblue | `00c0ff` |
| blue | `3333cc` | lightgreen | `00ff00` |
| red | `ff0000` | magenta | `ff00ff` |
| green | `008000` | orange | `ff8000` |
| gray | `808080` | yellow | `ffff00` |
| white | `ffffff` | | |

Background colour names are **different** (`parseBackgroundColor`):

| name | RGB |
|---|---|
| blue | `a0e8ff` |
| pink | `ffc0d4` |
| green | `80ffc0` |
| orange | `ffc080` |
| yellow | `ffff80` |
| white, or anything else | parsed as above, defaulting to `ffffff` |

**Inkpad:** colour names are converted to `#rrggbbff` when read, so files are
saved with hex colours, as Xournal++ does.

## 7. Behaviour Inkpad does not copy

These are things Xournal++ normalises or drops when it saves. Inkpad keeps
them:
- unknown elements and attributes
- the alpha byte in colours
- higher-precision numbers
- original image bytes
- `ts`/`fn` attributes as present
- empty texts
- pressure values exactly as written
- `<timestamp>` elements

Not supported yet:
- **MrWriter files:** root `MrWriter` with a `pressures` attribute. No sample
  exists.
- **Writing zip packages.** Xournal++ 1.3.8 doesn't write them either.

# PLAN.md — Android-native Xournal++ ("Inkpad" working name)

## Goal
A native Android handwriting/PDF-annotation app that reads and writes
Xournal++ `.xopp` files losslessly, with low-latency stylus input.
Android first; architected so an iOS app can later reuse the core logic.
Written from scratch (do NOT copy Xournal++ source, which is GPL-2.0; implement
the file format from its observed structure and sample files).

## Tech stack
- Kotlin, Gradle Kotlin DSL, version catalog (libs.versions.toml)
- Android app: minSdk 29, targetSdk latest, single-activity
- UI chrome: Jetpack Compose (toolbars, dialogs, file browser, settings)
- Drawing surface: custom View (not Compose Canvas) using
  `androidx.graphics:graphics-core` (CanvasFrontBufferedRenderer) for
  low-latency ink + `androidx.input:input-motionprediction`
- PDF rendering: `android.graphics.pdf.PdfRenderer` behind an interface
  (swap to pdfium later if needed)
- PDF export: `PdfDocument` for vector pages; PdfBox-Android for
  overlaying annotations onto existing PDFs
- Persistence: Storage Access Framework (open/save any location), autosave
  to app-private storage
- Testing: kotlin.test in commonTest, Robolectric/instrumented tests for
  Android rendering, screenshot tests for page rendering

## Multiplatform rules (important)
- Shared modules are Kotlin Multiplatform (KMP). Enabled targets for now:
  Android (via the `com.android.kotlin.multiplatform.library` plugin, which
  replaces `androidTarget` since AGP 9) and `jvm` (jvm only for fast unit
  tests). An iOS target will be added in a later phase; do not add it yet.
- Until an iOS target exists the compiler does not reject JDK APIs in
  `commonMain`; the `checkCommonPurity` task (part of `check`) enforces the
  import rule below.
- Shared code lives in `commonMain`. Use only multiplatform libraries there:
  Okio (file IO, gzip), xmlutil (XML), kotlinx-serialization (settings or
  metadata if needed), kotlinx-coroutines.
- No `java.*`, `javax.*`, or `android.*` imports in `commonMain`. If something
  platform-specific is unavoidable, use `expect`/`actual` and keep the
  surface tiny.
- Shared code never references screen pixels, Bitmaps, Views, or
  MotionEvents. It works in document coordinates (PDF points) and
  plain data types.

## Modules
- `:core:model` (KMP): domain model, edit commands, undo/redo stack
- `:core:format` (KMP): .xopp/.xoj reading and writing
- `:core:geometry` (KMP): stroke smoothing, pressure→width, hit testing,
  eraser stroke splitting, lasso/rect selection, transforms, bounding boxes,
  background pattern geometry (line positions for lined/graph/dotted)
- `:android:render` (Android): draws model onto android.graphics.Canvas,
  page bitmap cache, PDF background rendering
- `:android:app` (Android): Compose UI, canvas View, input handling,
  file access, export

## Domain model (core:model)
Document → Pages → Layers → Elements
- Page: width/height (pt), Background (Solid{color, style: plain|lined|
  ruled|graph|dotted}, Pdf{file, pageNo}, Image)
- Layer: name, visible, elements
- Element (sealed): Stroke, Text, Image, TexImage
- Stroke: tool (pen|highlighter|eraser), color (RGBA), base width,
  points [x, y], per-point widths (pressure), cap/fill options
- All coordinates in PDF points, independent of screen zoom
- Immutable data + command-based edits for undo/redo
- Images stored as encoded bytes (PNG/JPEG), decoded only by platform code

## .xopp format notes (core:format)
- Gzipped XML; root `<xournal creator=... fileversion="4">`
- `<page width h>` → `<background type="solid|pdf|pixmap" ...>` → `<layer>`
- `<stroke tool color="#rrggbbaa" width="w0 w1 w2 ...">x0 y0 x1 y1 ...</stroke>`
  (first width = nominal, rest = per-segment pressure widths)
- `<text font size x y color>`, `<image left top right bottom>` base64 PNG
- Unknown elements/attributes must be preserved on round-trip
- Also read legacy `.xoj` (same format, older Xournal)

## Phases (complete one at a time; each ends green on `./gradlew check`)

### Phase 0 — Skeleton
Multi-module Gradle project with KMP plugin set up for core modules
(androidTarget + jvm), Android modules, ktlint/detekt, empty Compose shell,
CI script.
✅ Builds; empty tests pass in commonTest (jvm) and Android.

### Phase 1 — Format I/O (core:format, shared)
Parser + writer for .xopp/.xoj into the domain model, using Okio + xmlutil.
✅ Round-trip tests in commonTest: parse → write → parse yields equal model,
using sample files in `testdata/` (I will provide real Xournal++ files).

### Phase 2 — Geometry + static rendering
core:geometry: pressure-width computation, background pattern geometry,
bounding boxes (with tests in commonTest).
android:render: render a page (backgrounds + variable-width strokes +
highlighter blending + text + images) to a Bitmap.
✅ Geometry unit tests; screenshot tests against reference PNGs.

### Phase 3 — Viewer
Scrollable continuous page list, pinch-zoom/pan, per-page bitmap cache
at current zoom, low-res placeholder while zooming, re-render on settle.
Open .xopp via SAF.
✅ Opens a 100-page file smoothly; memory bounded (LRU cache).

### Phase 4 — Inking
Pen tool with pressure, using historical MotionEvent points, front-buffered
rendering for the live stroke, motion prediction. Stroke smoothing lives in
core:geometry; Android code only converts MotionEvents to plain
(x, y, pressure, time) samples in document coordinates.
Commit stroke to model + page cache on ACTION_UP.
Input rules: stylus draws; finger pans/zooms (configurable); handle
TOOL_TYPE_ERASER and stylus button; drop strokes on ACTION_CANCEL /
FLAG_CANCELED (palm rejection).
✅ Unit tests for stroke building/smoothing; manual latency check.

### Phase 5 — Editing tools
Undo/redo (command stack in core:model), stroke eraser, whiteout/partial
eraser (stroke splitting in core:geometry), highlighter, color/width palette,
save via SAF + autosave. All file access goes through a `DocumentStore` interface
(list, read, write, metadata incl. version/etag) in a shared module;
SAF is the first implementation. No direct SAF calls outside it.
✅ Edits survive save → reopen; undo and eraser tests in commonTest.

### Phase 6 — Pages & layers
Add/delete/reorder pages, background chooser, layer panel
(add/hide/rename/reorder), page templates. Page/layer operations are
commands in core:model.

### Phase 7 — Selection
Lasso + rectangle select, move/scale/rotate, copy/paste, delete,
recolor selection. Hit testing and transforms in core:geometry.

### Phase 8 — PDF annotation
Open a PDF → create document with pdf backgrounds; store relative path
like Xournal++; export annotated PDF (vector overlay).
✅ Exported PDF opens in other readers with annotations correct.

### Phase 9 — Nextcloud / WebDAV sync
New KMP module `:core:sync` using Ktor client (shared with future iOS).
- WebDAV ops: PROPFIND (list + ETags), GET, PUT with If-Match, MKCOL, MOVE
- Nextcloud Login Flow v2 for app passwords; plain WebDAV URL + credentials
  for other servers. Credentials stored via expect/actual
  (Android Keystore now, iOS Keychain later).
- Local cache of synced files; work offline, sync on open/save and
  periodically via WorkManager (Android layer only).
- A document's referenced PDF backgrounds sync along with it
  (resolve relative paths against the remote folder).
- Conflicts: .xopp can't be merged; if the ETag changed remotely,
  keep both ("name (conflict 2026-10-13).xopp") and tell the user.
✅ Integration tests against a Nextcloud Docker container
(nextcloud:latest) covering upload, download, offline edit, and conflict.

### Phase 10 — Extras (prioritize later)
Text tool, image insert, shape recognizer (in core:geometry), rulers/straight
lines, LaTeX via bundled renderer, audio recording linked to strokes,
dark mode page inversion, tablet/split-screen polish.

### Phase 11 — iOS (future, requires a Mac)
Add iosArm64 + iosSimulatorArm64 targets to core modules and export them as
an XCFramework. Build a native iOS app: SwiftUI shell, Metal or Core Graphics
canvas, Apple Pencil input via UITouch coalesced/predicted touches, PDFKit
for PDF rendering/export, UIDocument for files. Repeat Phases 2–8 for the
iOS platform layer only; shared logic is reused as-is.
✅ Same `testdata/` round-trip tests pass on iOS targets.

## Working rules for Claude Code
- Work on one phase per session; propose a sub-plan before coding.
- Respect the multiplatform rules above; if a task seems to need a
  platform API in shared code, stop and propose an expect/actual design.
- Write tests first for format, model, and geometry logic.
- Commit at the end of each green sub-step with a descriptive message.
- Never break .xopp compatibility; when unsure about format details,
  stop and ask for a sample file rather than guessing.
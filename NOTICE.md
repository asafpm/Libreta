# Notices

Libreta is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 2 of the License, or (at your option) any later
version (`GPL-2.0-or-later`). See [LICENSE](LICENSE) for the GPL version 2 text and
[licenses/GPL-3.0.txt](licenses/GPL-3.0.txt) for version 3.

The app shows these notices and the licence texts in [licenses/](licenses/) under
Settings, About.

Libreta is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.

## Xournal++

Libreta reads and writes the Xournal++ file format. Xournal++ v1.3.8 is the
reference implementation for that format, and parts of Libreta are derived from
it.

- Project: https://github.com/xournalpp/xournalpp
- Version: v1.3.8 (commit 938bdb8de4d32f38f48dd7f6544886722157a848)
- Copyright © the Xournal++ contributors
- License: GPL-2.0-or-later

Source files containing code derived from Xournal++ say so in their header,
naming the Xournal++ file they are derived from. Test files copied from
Xournal++ live in `testdata/xournalpp/`.

### Files derived from Xournal++

- `testdata/xournalpp/`: test documents copied unchanged from Xournal++'s
  `test/files/` and `development/documentation/` (see the README there).
- `testdata/libreta-pdf.xopp.bg.pdf`: a copy of Xournal++'s
  `test/files/packaged_xopp/pdfBackground/old.xopp.bg.pdf`; `testdata/password.pdf`
  is the same PDF locked with a password.
- `docs/xopp-format.md`: format specification describing Xournal++'s loader
  and saver, with source references.
- `core/format/src/commonMain/.../ColorCodec.kt`: colour-name tables from
  `src/core/control/xojfile/LoadHandlerHelper.cpp`.
- `core/format/src/commonMain/.../XmlTree.kt`: output layout and escaping of
  `src/core/control/xml/XmlNode.cpp`, `TextAttribute.cpp`, `XmlTextNode.cpp`.
- `core/format/src/commonMain/.../DocumentCodec.kt`, `BackgroundCodec.kt`,
  `ElementCodec.kt`, `StrokeCodec.kt`, `XoppReader.kt`: element/attribute
  names, write order and value rules of
  `src/core/control/xojfile/LoadHandler.cpp` and `SaveHandler.cpp`.
- `core/format/src/commonTest/.../ColorCodecTest.kt`: expectations from
  `LoadHandlerHelper.cpp` and `test/unit_tests/control/LoadHandlerTest.cpp`.
- `docs/rendering.md`: rendering specification describing Xournal++'s view
  code, with source references.
- `core/geometry/src/commonMain/.../PressureRepair.kt`, `StrokeGeometry.kt`,
  `StrokeStyle.kt`: pressure rules of `LoadHandler.cpp`, bounding boxes of
  `model/Stroke.cpp`, dash patterns of `model/StrokeStyle.cpp` and stroke
  opacity/blending of `view/StrokeView.cpp`.
- `core/geometry/src/commonMain/.../StrokeContour.kt`: pressure-stroke outline
  of `model/StrokeContour.cpp` and `view/StrokeViewHelper.cpp`.
- `core/geometry/src/commonMain/.../BackgroundConfig.kt`, `BackgroundPattern.kt`,
  `BackgroundStyles.kt`: background ruling of `model/BackgroundConfig.cpp`,
  `view/background/*BackgroundView.cpp` and `control/pagetype/PageTypeHandler.cpp`.
- `android/render/src/main/.../BackgroundPainter.kt`: background drawing of
  `view/background/*BackgroundView.cpp` and `control/PdfCache.cpp`.
- `android/render/src/main/.../StrokePainter.kt`: stroke drawing of
  `view/StrokeView.cpp` and `view/StrokeViewHelper.cpp`.
- `android/render/src/main/.../TextPainter.kt`, `ImagePainter.kt`: text and image
  drawing of `model/Text.cpp`, `view/TextView.cpp`, `view/ImageView.cpp`,
  `view/TexImageView.cpp` and `model/Image.cpp`.
- `core/geometry/src/commonTest/.../PressureRepairTest.kt`: test case ported from
  `LoadHandlerTest.cpp`.
- `core/format/src/commonTest/.../XournalppLoadTest.kt`: test cases ported from
  `test/unit_tests/control/LoadHandlerTest.cpp`.
- `core/format/src/commonMain/.../XoppPreview.kt`: preview lookup of
  `src/util/XojPreviewExtractor.cpp`; `XoppPreviewTest.kt`: expectations from
  `test/unit_tests/util/XojPreviewExtractorTest.cpp`.
- `core/model/src/commonMain/.../NewNote.kt`: default file name pattern of
  `src/core/control/settings/Settings.cpp`.
- `core/geometry/src/commonMain/.../StrokeBuilder.kt`: stroke building of
  `src/core/control/tools/StrokeHandler.cpp` and the pressure filter of
  `src/core/gui/inputdevices/PenInputHandler.cpp`.
- `core/geometry/src/commonMain/.../StrokeEraser.kt`: erasing of
  `src/core/model/Stroke.cpp` (intersects, intersectWithPaddedBox, cloneSection),
  `src/core/model/eraser/ErasableStroke.cpp`, `PaddedBox.h`,
  `src/util/include/util/UnionOfIntervals.h` and the padding of
  `src/core/control/tools/EraseHandler.h`; its tests port
  `test/unit_tests/model/ErasableStrokeTest.cpp`.
- `core/geometry/src/commonMain/.../PageEraser.kt`: the eraser gesture of
  `src/core/control/tools/EraseHandler.cpp`.
- `core/model/src/commonMain/.../PageOperations.kt`: page and layer commands of
  `src/core/control/Control.cpp` and `src/core/control/layer/LayerController.cpp`.
- `core/model/src/commonMain/.../NewNote.kt` (`NewPageTemplate`): new pages of
  `src/core/control/PageBackgroundChangeController.cpp` and the page template of
  `src/core/control/settings/PageTemplateSettings.cpp`.
- `android/app/src/main/.../files/NoteCommands.kt`: the selected layer after layer commands,
  `src/core/control/layer/LayerController.cpp`.
- `core/model/src/commonMain/.../PenSettings.kt`: pen and eraser widths, colour and
  eraser types of `src/core/control/ToolHandler.cpp`, drawing types of
  `src/core/control/ToolEnums.h`, the text default of `src/core/control/settings/Settings.cpp`,
  palette of `palettes/xournalpp.gpl`.
- `core/geometry/src/commonMain/.../Shapes.kt`: port of the shapes of
  `src/core/control/tools/RulerHandler.cpp`, `RectangleHandler.cpp`, `EllipseHandler.cpp`,
  `ArrowHandler.cpp` and `CoordinateSystemHandler.cpp`, with the snapping of
  `src/core/model/Snapping.cpp` and `src/core/control/tools/SnapToGridInputHandler.cpp`.
- `android/app/src/main/.../viewer/ShapeInput.kt`: drawing a shape, of
  `src/core/control/tools/BaseShapeHandler.cpp`.
- `android/app/src/main/.../viewer/TextEditing.kt`: editing text with the text tool, of
  `src/core/control/tools/TextEditor.cpp` and `src/core/gui/PageView.cpp`.
- `android/app/src/main/.../viewer/PageRecolor.kt`: the recolouring of `src/util/Recolor.cpp`.
- `android/app/src/main/.../ui/KeyboardShortcuts.kt`: the keyboard shortcuts of
  `ui/mainmenubar.xml`, `src/core/control/actions/ActionProperties.h` and
  `src/core/gui/XournalView.cpp`.
- `android/app/src/main/.../files/NoteSaver.kt`: the embedded preview of
  `src/core/control/jobs/SaveJob.cpp`.
- `core/model/src/commonMain/.../NewNote.kt` (`forPdf`, `notesForPdf`, `exportName`): notes
  for PDFs of `src/core/model/Document.cpp` (`readPdf`), the existing notes looked for in
  `src/core/control/Control.cpp` (`autoloadPdfXoj`), and the export name `%{name}_annotated`
  of `src/core/control/settings/Settings.cpp`.
- `android/render/src/main/.../PdfPages.kt`: PDF page sizes as `readPdf` takes them.
- `android/app/src/main/.../library/PdfNotes.kt`, `OpenedPdfs.kt`: opening a PDF's note, of
  `src/core/control/Control.cpp`, and carrying the PDF as an attachment, of
  `src/core/control/xojfile/SaveHandler.cpp`.
- `android/app/src/main/.../library/LibraryFiles.kt`: the files that go with a note when it is
  renamed, moved or deleted: its attached files (`<note>.<name>`) of
  `src/core/control/xojfile/SaveHandler.cpp`, the PDF it is named after of
  `src/core/control/Control.cpp`, and its link to that PDF as
  `src/core/control/xojfile/LoadHandler.cpp` follows it.
- `android/render/src/main/.../PdfExport.kt`, `PdfMerge.kt`: PDF export of
  `src/core/pdf/base/XojCairoPdfExport.cpp`.
- `core/geometry/src/commonMain/.../Selection.kt`: what a rectangle or lasso selects of
  `src/core/control/tools/Selector.cpp` and `Element::isInSelection`
  (`src/core/model/Element.cpp`, `Stroke.cpp`), object picking of
  `src/core/gui/PageViewFindObjectHelper.h`, transforms of
  `src/core/control/tools/EditSelectionContents.cpp` with `Stroke::scale`, `Stroke::rotate`,
  `Text::scale` and `Image::scale`, and rotation snapping of `src/core/model/Snapping.cpp`.
- `core/geometry/src/commonMain/.../SelectionBox.kt` and
  `android/app/src/main/.../viewer/SelectionGesture.kt`: the selection box, its handles and
  dragging them, of `src/core/control/tools/EditSelection.cpp`.
- `core/model/src/commonMain/.../SelectionOperations.kt`: restyling of
  `src/core/control/tools/EditSelectionContents.cpp`.
- `android/app/src/main/.../viewer/SelectionTool.kt`, `SelectionOverlay.kt`: selecting and
  its look, of `src/core/control/tools/Selector.cpp`, `src/core/gui/PageView.cpp` and
  `src/core/view/overlays/SelectorView.cpp`.
- `android/app/src/main/.../viewer/SelectionCommands.kt`: where pasted elements go, of
  `src/core/control/Control.cpp`.

## Fonts

`android/render/src/main/res/font/dejavu_*.ttf` are DejaVu Sans, DejaVu Serif and
DejaVu Sans Mono (regular, bold, italic/oblique, bold italic/oblique) from
DejaVu Fonts 2.37 (https://dejavu-fonts.github.io/), unmodified. Fonts are
© Bitstream (Bitstream Vera Fonts licence); DejaVu changes are in the public
domain; glyphs imported from Arev fonts are © Tavmjong Bah. Full licence:
[licenses/DejaVu-LICENSE.txt](licenses/DejaVu-LICENSE.txt). The release archive
`dejavu-fonts-ttf-2.37.zip` had SHA-256
`7576310b219e04159d35ff61dd4a4ec4cdba4f35c00e002a136f00e96a908b0a`.

`android/render/src/main/res/font/liberation_*.ttf` are Liberation Sans, Serif
and Mono 2.1.5 (https://github.com/liberationfonts/liberation-fonts), unmodified,
© 2010 Google Corporation and © 2012 Red Hat, Inc., licensed under the SIL Open
Font License 1.1: [licenses/Liberation-LICENSE.txt](licenses/Liberation-LICENSE.txt).
The release archive `liberation-fonts-ttf-2.1.5.tar.gz` had SHA-256
`7191c669bf38899f73a2094ed00f7b800553364f90e2637010a69c0e268f25d0`.

## Third-party libraries

Libreta depends on libraries under the Apache License 2.0, among them the Kotlin
standard library, AndroidX/Jetpack Compose, Okio, xmlutil and PdfBox-Android. The
Apache License 2.0 is compatible with version 3 of the GPL, which this project's
"or later" clause allows the combined work to be distributed under. Its text is in
[licenses/Apache-2.0.txt](licenses/Apache-2.0.txt).

### PdfBox-Android

PdfBox-Android 2.0.27.0 (https://github.com/TomRoush/PdfBox-Android), a port of
Apache PDFBox 2.0.27, reads PDFs and writes exported ones. It is distributed under
the Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0), with this
notice:

> Apache PDFBox
> Copyright 2014 The Apache Software Foundation
>
> This product includes software developed at
> The Apache Software Foundation (http://www.apache.org/).
>
> Based on source code originally developed in the PDFBox and
> FontBox projects.
>
> Copyright (c) 2002-2007, www.pdfbox.org
>
> Based on source code originally developed in the PaDaF project.
> Copyright (c) 2010 Atos Worldline SAS
>
> Includes the Adobe Glyph List
> Copyright 1997, 1998, 2002, 2007, 2010 Adobe Systems Incorporated.
>
> Includes the Zapf Dingbats Glyph List
> Copyright 2002, 2010 Adobe Systems Incorporated.
>
> Includes the Script Property (Scripts-10.0.0.txt)
> Copyright 2017 Unicode, Inc.

Its licence, with the terms of the Adobe font metrics, Adobe CMaps and PaDaF code
it includes, is [licenses/PdfBox-Android-LICENSE.txt](licenses/PdfBox-Android-LICENSE.txt),
and its notice [licenses/PdfBox-Android-NOTICE.txt](licenses/PdfBox-Android-NOTICE.txt).
It also bundles Liberation Sans Regular (SIL Open Font License 1.1, see Fonts
above). Its optional BouncyCastle dependency is left out.

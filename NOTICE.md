# Notices

Inkpad is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 2 of the License, or (at your option) any later
version (`GPL-2.0-or-later`). See [LICENSE](LICENSE) for the GPL version 2 text.

Inkpad is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.

## Xournal++

Inkpad reads and writes the Xournal++ file format. Xournal++ v1.3.8 is the
reference implementation for that format, and parts of Inkpad are derived from
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
- `core/geometry/src/commonTest/.../PressureRepairTest.kt`: test case ported from
  `LoadHandlerTest.cpp`.
- `core/format/src/commonTest/.../XournalppLoadTest.kt`: test cases ported from
  `test/unit_tests/control/LoadHandlerTest.cpp`.

## Fonts

`android/render/src/main/res/font/` contains DejaVu Sans, DejaVu Serif and
DejaVu Sans Mono (regular, bold, italic/oblique, bold italic/oblique) from
DejaVu Fonts 2.37 (https://dejavu-fonts.github.io/), unmodified. Fonts are
© Bitstream (Bitstream Vera Fonts licence); DejaVu changes are in the public
domain; glyphs imported from Arev fonts are © Tavmjong Bah. Full licence:
[licenses/DejaVu-LICENSE.txt](licenses/DejaVu-LICENSE.txt). The release archive
`dejavu-fonts-ttf-2.37.zip` had SHA-256
`7576310b219e04159d35ff61dd4a4ec4cdba4f35c00e002a136f00e96a908b0a`.

## Third-party libraries

Inkpad depends on libraries under the Apache License 2.0, among them the Kotlin
standard library, AndroidX/Jetpack Compose, Okio and xmlutil. The Apache
License 2.0 is compatible with version 3 of the GPL, which this project's
"or later" clause allows the combined work to be distributed under.

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

_None yet._

## Third-party libraries

Inkpad depends on libraries under the Apache License 2.0, among them the Kotlin
standard library, AndroidX/Jetpack Compose, Okio and xmlutil. The Apache
License 2.0 is compatible with version 3 of the GPL, which this project's
"or later" clause allows the combined work to be distributed under.
